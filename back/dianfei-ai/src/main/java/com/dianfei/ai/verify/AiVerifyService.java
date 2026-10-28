package com.dianfei.ai.verify;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.verify.dto.AiVerifyDTO;
import com.dianfei.ai.verify.entity.AiVerifyLog;
import com.dianfei.ai.verify.mapper.AiVerifyLogMapper;
import com.dianfei.ai.verify.vo.AiVerifyVO;
import com.dianfei.common.storage.FileStorage;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * F1 三层管线编排（E1，05 章 5.2 / memory.md 业务规则）：
 * <ol>
 *   <li>Java 侧预检：pHash 防重（汉明距离 ≤ ai.verify.phash-hamming 判重复图直接驳回）
 *       + EXIF 时效（缺失或超 ai.verify.exif-max-hours 强制人审）</li>
 *   <li>AI 服务视觉识别（AiServiceClient，Resilience4j 熔断）</li>
 *   <li>阈值决策落库：AUTO_PASS / MANUAL_REVIEW（积分冻结待人审）/ REJECT</li>
 * </ol>
 * 降级（D15）：AI 超时/熔断/3001 → 全部转人审队列（decision=MANUAL_REVIEW, degraded=1），
 * 回收链路照常推进，审核链路永不中断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVerifyService {

    public static final String DECISION_AUTO_PASS = "AUTO_PASS";
    public static final String DECISION_MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String DECISION_REJECT = "REJECT";

    /** pHash 图片下载超时（预检不能拖慢整体 P95 ≤ 5s） */
    private static final int PHASH_DOWNLOAD_TIMEOUT_MS = 1500;

    private final AiServiceClient aiServiceClient;
    private final AiVerifyLogMapper verifyLogMapper;
    private final ConfigService configService;
    private final FileStorage fileStorage;

    @Transactional(rollbackFor = Exception.class)
    public AiVerifyVO verify(AiVerifyDTO dto, Long userId) {
        long start = System.currentTimeMillis();
        AiVerifyLog logRow = new AiVerifyLog();
        logRow.setBizType(dto.getBizType());
        logRow.setBizId(dto.getBizId());
        logRow.setUserId(userId);
        // base64 data URL 落盘为文件 URL（DB 仅存短 URL，避免 TEXT 64KB 仍超长）
        logRow.setImageUrl(persistDataUrl(dto.getImageUrl(), dto.getBizType()));
        logRow.setExifTime(dto.getExifTime());
        logRow.setTraceId(MDC.get("traceId"));
        logRow.setDegraded(0);

        // ---------- 第 1 层：pHash 防重 ----------
        String phash = PHashUtil.fromUrl(dto.getImageUrl(), PHASH_DOWNLOAD_TIMEOUT_MS);
        logRow.setPhash(phash);
        if (phash != null && isDuplicate(userId, phash)) {
            logRow.setDecision(DECISION_REJECT);
            logRow.setRejectReason("重复图片（pHash 命中历史提交）");
            // 重复图不再调 AI 解析，但回传历史识别品类，供前端展示对应 3D 模型
            logRow.setCategory(findHistoryCategory(userId, phash));
            return finish(logRow, null, start);
        }

        // ---------- 第 1 层：EXIF 时效（缺失或过旧强制人审，仍调 AI 供人审参考） ----------
        boolean exifStale = isExifStale(dto.getExifTime());

        // ---------- 第 2 层：视觉大模型识别（熔断保护） ----------
        AiContracts.VerifyResponse resp;
        try {
            resp = aiServiceClient.verifyImage(buildRequest(dto), passThreshold(), reviewThreshold());
        } catch (AiServiceException e) {
            // AI 宕机/超时/熔断开路：全部转人审队列，回收链路照常（D15）
            log.warn("F1 AI 不可用转人审 biz={}:{} fallback={}", dto.getBizType(), dto.getBizId(), e.getFallback());
            logRow.setDecision(DECISION_MANUAL_REVIEW);
            logRow.setRejectReason("AI 服务不可用转人工审核");
            logRow.setDegraded(1);
            return finish(logRow, null, start);
        }

        // ---------- 第 3 层：阈值决策（AI 建议 + Java 侧 EXIF 修正为最终决策） ----------
        String decision = resp.getSuggestion();
        String reason = null;
        if (DECISION_REJECT.equals(decision)) {
            reason = "违规项：" + resp.getViolations();
        } else if (exifStale && DECISION_AUTO_PASS.equals(decision)) {
            decision = DECISION_MANUAL_REVIEW;
            reason = "EXIF 拍摄时间缺失或超时效，转人工审核";
        } else if (DECISION_MANUAL_REVIEW.equals(decision)) {
            reason = Boolean.FALSE.equals(resp.getCategoryMatch())
                    ? "申报品类与识别不一致" : "置信度低于自动通过阈值";
        }
        logRow.setDecision(decision);
        logRow.setRejectReason(reason);
        logRow.setModelName(resp.getModelName());
        logRow.setCategory(resp.getCategory());
        logRow.setConfidence(resp.getConfidence());
        logRow.setPurity(resp.getPurity());
        logRow.setResultJson(JSONUtil.toJsonStr(resp));
        logRow.setDegraded(Boolean.TRUE.equals(resp.getDegraded()) ? 1 : 0);
        return finish(logRow, resp, start);
    }

    /** 近 30 天同用户提交图 pHash 汉明距离 ≤ 阈值判定重复（idx_verify_phash + idx_verify_user） */
    private boolean isDuplicate(Long userId, String phash) {
        int hammingThreshold = Integer.parseInt(
                configService.getValue("ai.verify.phash-hamming", "5"));
        List<AiVerifyLog> recent = verifyLogMapper.selectList(new LambdaQueryWrapper<AiVerifyLog>()
                .select(AiVerifyLog::getPhash)
                .eq(AiVerifyLog::getUserId, userId)
                .isNotNull(AiVerifyLog::getPhash)
                .ge(AiVerifyLog::getCreateTime, LocalDateTime.now().minusDays(30))
                .ne(AiVerifyLog::getDecision, DECISION_REJECT));
        return recent.stream()
                .anyMatch(row -> PHashUtil.hammingDistance(phash, row.getPhash()) <= hammingThreshold);
    }

    /** 同 phash 最近一次有识别结果的品类（防重驳回时回传，供前端展示 3D 模型） */
    private String findHistoryCategory(Long userId, String phash) {
        List<AiVerifyLog> history = verifyLogMapper.selectList(new LambdaQueryWrapper<AiVerifyLog>()
                .select(AiVerifyLog::getCategory)
                .eq(AiVerifyLog::getUserId, userId)
                .eq(AiVerifyLog::getPhash, phash)
                .isNotNull(AiVerifyLog::getCategory)
                .orderByDesc(AiVerifyLog::getId)
                .last("LIMIT 1"));
        return history.isEmpty() ? null : history.get(0).getCategory();
    }

    private boolean isExifStale(LocalDateTime exifTime) {
        long maxHours = Long.parseLong(configService.getValue("ai.verify.exif-max-hours", "72"));
        return exifTime == null
                || Duration.between(exifTime, LocalDateTime.now()).toHours() > maxHours;
    }

    private AiContracts.VerifyRequest buildRequest(AiVerifyDTO dto) {
        return AiContracts.VerifyRequest.builder()
                .bizType(dto.getBizType())
                .bizId(dto.getBizId())
                .imageUrl(dto.getImageUrl())
                .claimedCategory(dto.getClaimedCategory())
                .claimedBags(dto.getClaimedBags())
                .build();
    }

    private Double passThreshold() {
        return Double.valueOf(configService.getValue("ai.verify.pass-threshold", "0.85"));
    }

    private Double reviewThreshold() {
        return Double.valueOf(configService.getValue("ai.verify.review-threshold", "0.60"));
    }

    /** 落库并组装 VO（elapsed_ms 为端到端口径，P95 ≤ 5000 监控用） */
    private AiVerifyVO finish(AiVerifyLog logRow, AiContracts.VerifyResponse resp, long start) {
        logRow.setElapsedMs((int) (System.currentTimeMillis() - start));
        verifyLogMapper.insert(logRow);

        AiVerifyVO vo = new AiVerifyVO();
        vo.setLogId(String.valueOf(logRow.getId()));
        vo.setDecision(logRow.getDecision());
        vo.setCategory(logRow.getCategory());
        vo.setPurity(logRow.getPurity());
        vo.setConfidence(logRow.getConfidence());
        vo.setRejectReason(logRow.getRejectReason());
        vo.setDegraded(logRow.getDegraded() == 1);
        vo.setElapsedMs(logRow.getElapsedMs());
        if (resp != null) {
            vo.setCategoryMatch(resp.getCategoryMatch());
            vo.setViolations(resp.getViolations());
        }
        return vo;
    }

    /**
     * base64 data URL（前端拍照/上传直传）落盘为文件 URL，DB 仅存短 URL（/api/file/preview/...）。
     * 背景：TEXT 上限 64KB，而一张手机照片的 base64 编码常达 1-5MB，必然 Data truncation。
     * 非 data URL（MinIO/内网 HTTP 地址）原样返回；落盘失败回退原值（由调用方决定是否继续）。
     * pHash 与 Python AI 仍用 dto 原始 data URL（二者均原生支持 data URL 解码）。
     */
    private String persistDataUrl(String imageUrl, String bizType) {
        if (imageUrl == null || !imageUrl.startsWith("data:")) {
            return imageUrl;
        }
        try {
            int comma = imageUrl.indexOf(',');
            if (comma < 0) {
                return imageUrl;
            }
            String meta = imageUrl.substring(5, comma); // 形如 image/png;base64
            String data = imageUrl.substring(comma + 1);
            byte[] bytes = meta.contains(";base64")
                    ? Base64.getDecoder().decode(data)
                    : URLDecoder.decode(data, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            String ext = "jpg";
            if (meta.contains("image/png")) {
                ext = "png";
            } else if (meta.contains("image/gif")) {
                ext = "gif";
            } else if (meta.contains("image/webp")) {
                ext = "webp";
            } else if (meta.contains("image/bmp")) {
                ext = "bmp";
            }
            String bizDir = "KITCHEN".equals(bizType) ? "kitchen" : "recycle";
            String filename = "verify_" + System.currentTimeMillis() + "." + ext;
            String fileUrl = fileStorage.save(new ByteArrayInputStream(bytes), filename, bizDir);
            log.info("[F1] data URL 落盘 fileUrl={} bytes={}", fileUrl, bytes.length);
            return fileUrl;
        } catch (Exception e) {
            log.warn("[F1] data URL 落盘失败，回退原值: {}", e.getMessage());
            return imageUrl;
        }
    }
}
