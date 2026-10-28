package com.dianfei.ai.voice;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.voice.dto.VoiceParseDTO;
import com.dianfei.ai.voice.entity.VoiceSession;
import com.dianfei.ai.voice.mapper.VoiceSessionMapper;
import com.dianfei.ai.voice.vo.VoiceParseVO;
import com.dianfei.business.recycle.dto.RecycleOrderCreateDTO;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.recycle.vo.RecycleOrderVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F7 语音碳管家编排（05 章 5.5 / E7）：ASR 转写文本 → AI 服务 /v1/voice/parse
 * 意图与槽位 → 多轮补槽（biz_voice_session 逐轮落库）→ 槽位齐全自动创建回收单并回填 order_id。
 *
 * <p>降级（D15）：AI 不可用返回 intent=UNKNOWN + degraded，前端回退文本输入表单；
 * 成单失败（品类未开通报价等）转为追问/引导，不抛错中断会话。
 *
 * <p>不开启外层事务：tryBook 内 RecycleOrderService.create 自带事务，成单被业务规则拦截时
 * 抛出的 BusinessException（RuntimeException）会标记内层事务 rollback-only，
 * 若此处再用外层事务包裹，提交时必然抛 UnexpectedRollbackException → 500 系统异常；
 * 会话落库与成单本就解耦（订单失败也要留痕追问），故本编排方法不做事务控制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceStewardService {

    /** BOOK_RECYCLING 成单必填槽位（category/appoint_time 与 ai-back _REQUIRED_SLOTS 一致，address Java 侧补充必填） */
    private static final String SLOT_ADDRESS = "address";

    private final AiServiceClient aiServiceClient;
    private final VoiceSessionMapper voiceSessionMapper;
    private final RecycleOrderService recycleOrderService;
    private final ConfigService configService;

    public VoiceParseVO parse(Long userId, VoiceParseDTO dto) {
        String sessionId = StrUtil.blankToDefault(dto.getSessionId(), IdUtil.fastSimpleUUID());
        List<VoiceSession> history = voiceSessionMapper.selectList(
                new LambdaQueryWrapper<VoiceSession>()
                        .eq(VoiceSession::getSessionId, sessionId)
                        .eq(VoiceSession::getUserId, userId)
                        .orderByAsc(VoiceSession::getTurnNo));

        VoiceSession turn = new VoiceSession();
        turn.setSessionId(sessionId);
        turn.setUserId(userId);
        turn.setTurnNo(history.size() + 1);
        turn.setUserText(dto.getText());
        turn.setDegraded(0);

        AiContracts.VoiceParseResponse resp;
        try {
            resp = aiServiceClient.voiceParse(AiContracts.VoiceParseRequest.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .text(dto.getText())
                    .context(buildContext(history))
                    .imageBase64(dto.getImageBase64())
                    .build());
        } catch (AiServiceException e) {
            // 降级 TEXT_INPUT：留痕后引导前端回退文字表单，会话不中断
            log.warn("[F7] 语音解析不可用 fallback={} : {}", e.getFallback(), e.getMessage());
            resp = degradedResponse();
            turn.setDegraded(1);
        }

        turn.setIntent(resp.getIntent());
        turn.setSlotsJson(resp.getSlots() == null ? null : JSONUtil.toJsonStr(resp.getSlots()));
        turn.setConfidence(resp.getConfidence());
        if (Boolean.TRUE.equals(resp.getDegraded())) {
            turn.setDegraded(1);
        }

        // 意图为预约回收且模型判定槽位齐全 → 尝试标准化并成单
        Long orderId = null;
        String replyText = resp.getReplyText();
        List<String> needSlots = resp.getNeedSlots() == null
                ? new ArrayList<>() : new ArrayList<>(resp.getNeedSlots());
        if ("BOOK_RECYCLING".equals(resp.getIntent()) && needSlots.isEmpty() && turn.getDegraded() == 0) {
            BookResult book = tryBook(userId, resp.getSlots(), needSlots);
            if (book.orderId != null) {
                orderId = book.orderId;
            }
            if (book.replyText != null) {
                replyText = book.replyText;
            }
        }
        turn.setOrderId(orderId);
        turn.setReplyText(replyText);
        // 照片持久化：用户上传原图 URL 随会话轨迹落库（V45）
        turn.setImageUrl(dto.getImageUrl());
        voiceSessionMapper.insert(turn);

        VoiceParseVO vo = new VoiceParseVO();
        vo.setSessionId(sessionId);
        vo.setTurnNo(turn.getTurnNo());
        vo.setIntent(resp.getIntent());
        vo.setSlots(resp.getSlots());
        vo.setConfidence(resp.getConfidence());
        vo.setReplyText(replyText);
        vo.setNeedSlots(needSlots);
        vo.setOrderId(orderId == null ? null : String.valueOf(orderId));
        vo.setDegraded(turn.getDegraded() == 1);
        // V44 图片扫描升级：识别后图片 + 检测明细透传前端展示
        vo.setAnnotatedImageBase64(resp.getAnnotatedImageBase64());
        vo.setDetections(resp.getDetections());
        return vo;
    }

    /** 会话轨迹（用户端"我的语音会话"回显 / 联调排查） */
    public List<VoiceSession> sessionTurns(Long userId, String sessionId) {
        return voiceSessionMapper.selectList(new LambdaQueryWrapper<VoiceSession>()
                .eq(VoiceSession::getSessionId, sessionId)
                .eq(VoiceSession::getUserId, userId)
                .orderByAsc(VoiceSession::getTurnNo));
    }

    /** 槽位标准化 + 创建回收单；失败转为追问文案（不中断会话） */
    private BookResult tryBook(Long userId, AiContracts.VoiceSlots slots, List<String> needSlots) {
        if (slots == null || StrUtil.isBlank(slots.getAddress())) {
            // 契约：address 空则取用户默认地址；MVP 无地址簿，Java 侧追问补槽
            needSlots.add(SLOT_ADDRESS);
            return BookResult.reply("请告诉我上门地址，例如：3 栋 2 单元 501");
        }
        LocalDateTime appointTime = VoiceTimeParser.parse(slots.getAppointTime());
        if (appointTime == null) {
            needSlots.add("appoint_time");
            return BookResult.reply("没听清上门时间，您可以说：明天上午 / 周六下午 3 点");
        }
        RecycleOrderCreateDTO create = new RecycleOrderCreateDTO();
        create.setCategory(slots.getCategory());
        create.setAddress(slots.getAddress());
        create.setAppointTime(appointTime);
        create.setAreaId(defaultAreaId());
        try {
            RecycleOrderVO order = recycleOrderService.create(userId, create);
            return BookResult.booked(Long.valueOf(order.getId()),
                    "已为您预约" + slots.getCategory() + "回收，"
                            + appointTime.toLocalDate() + " " + appointTime.toLocalTime()
                            + " 上门，单号 " + order.getOrderNo());
        } catch (BusinessException e) {
            log.info("[F7] 语音成单被业务规则拦截：{}", e.getMessage());
            return BookResult.reply(e.getMessage() + "，您可以换个品类或稍后在页面下单");
        }
    }

    /** 语音单默认片区（sys_config 可配；正式版由地址簿/定位反推） */
    private Long defaultAreaId() {
        return Long.valueOf(configService.getValue("ai.voice.default-area-id", "1"));
    }

    private static List<Map<String, String>> buildContext(List<VoiceSession> history) {
        List<Map<String, String>> context = new ArrayList<>();
        for (VoiceSession turn : history) {
            if (StrUtil.isNotBlank(turn.getUserText())) {
                context.add(Map.of("role", "user", "text", turn.getUserText()));
            }
            if (StrUtil.isNotBlank(turn.getReplyText())) {
                context.add(Map.of("role", "assistant", "text", turn.getReplyText()));
            }
        }
        return context;
    }

    private static AiContracts.VoiceParseResponse degradedResponse() {
        AiContracts.VoiceParseResponse resp = new AiContracts.VoiceParseResponse();
        resp.setIntent("UNKNOWN");
        resp.setSlots(new AiContracts.VoiceSlots());
        resp.setConfidence(0.0);
        resp.setReplyText("语音助手暂时不可用，请使用文字表单完成预约");
        resp.setNeedSlots(List.of());
        resp.setModelName("fallback/text-input");
        resp.setDegraded(true);
        return resp;
    }

    /** 成单尝试结果：orderId 与回复文案二选一/皆有 */
    private record BookResult(Long orderId, String replyText) {

        static BookResult reply(String text) {
            return new BookResult(null, text);
        }

        static BookResult booked(Long orderId, String text) {
            return new BookResult(orderId, text);
        }
    }
}
