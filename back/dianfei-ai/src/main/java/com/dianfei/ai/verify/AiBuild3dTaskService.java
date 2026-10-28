package com.dianfei.ai.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.verify.entity.AiBuild3dTask;
import com.dianfei.ai.verify.mapper.AiBuild3dTaskMapper;
import com.dianfei.ai.verify.vo.Build3dTaskVO;
import com.dianfei.common.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 持久化的 AI 3D 异步任务；任务查询严格按 userId 隔离，重启后中断任务可审计。 */
@Slf4j
@Service
public class AiBuild3dTaskService {

    private static final Set<String> BUILD3D_MODES = Set.of("standard", "advanced", "extreme");

    private final AiServiceClient aiServiceClient;
    private final AiBuild3dTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final FileStorage fileStorage;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ai-build3d-worker");
        t.setDaemon(true);
        return t;
    });

    public AiBuild3dTaskService(AiServiceClient aiServiceClient, AiBuild3dTaskMapper taskMapper,
                                ObjectMapper objectMapper, FileStorage fileStorage) {
        this.aiServiceClient = aiServiceClient;
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
        this.fileStorage = fileStorage;
    }

    @PostConstruct
    void recoverInterruptedTasks() {
        AiBuild3dTask update = new AiBuild3dTask();
        update.setStatus("FAILED");
        update.setError("服务重启导致任务中断，请重新提交");
        taskMapper.update(update, new LambdaQueryWrapper<AiBuild3dTask>()
                .eq(AiBuild3dTask::getStatus, "RUNNING"));
    }

    public Build3dTaskVO submit(Long userId, String imageUrl, String mode) {
        if (userId == null) {
            throw new com.dianfei.common.exception.BusinessException(
                    com.dianfei.common.result.ResultCode.UNAUTHORIZED, "请先登录");
        }
        String effectiveMode = BUILD3D_MODES.contains(mode) ? mode : "standard";
        // data URL（手机拍照/上传直传可达 1-5MB）落盘为短 URL 入库；ai-back 执行仍用原始 data URL（原生支持解码）
        String persistedUrl = persistDataUrl(imageUrl);
        AiBuild3dTask task = new AiBuild3dTask();
        task.setId(cn.hutool.core.util.IdUtil.getSnowflakeNextId());
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        task.setImageUrl(persistedUrl);
        task.setStatus("RUNNING");
        task.setMode(effectiveMode);
        taskMapper.insert(task);
        executor.submit(() -> execute(task.getTaskId(), imageUrl, effectiveMode));
        return toVO(task);
    }

    /** data URL 落盘为文件 URL（DB 仅存短 URL）；非 data URL 原样返回，落盘失败回退原值。 */
    private String persistDataUrl(String imageUrl) {
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
            String filename = "build3d_" + System.currentTimeMillis() + "." + ext;
            String fileUrl = fileStorage.save(new ByteArrayInputStream(bytes), filename, "recycle");
            log.info("[3D] data URL 落盘 fileUrl={} bytes={}", fileUrl, bytes.length);
            return fileUrl;
        } catch (Exception e) {
            log.warn("[3D] data URL 落盘失败，回退原值: {}", e.getMessage());
            return imageUrl;
        }
    }

    public Build3dTaskVO get(Long userId, String taskId) {
        AiBuild3dTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiBuild3dTask>()
                .eq(AiBuild3dTask::getTaskId, taskId)
                .eq(AiBuild3dTask::getUserId, userId)
                .last("LIMIT 1"));
        return task == null ? null : toVO(task);
    }

    private void execute(String taskId, String imageUrl, String mode) {
        AiBuild3dTask update = new AiBuild3dTask();
        try {
            AiContracts.Build3dResponse response = aiServiceClient.build3d(AiContracts.Build3dRequest.builder()
                    .imageUrl(imageUrl).mode(mode).build());
            update.setStatus("DONE");
            update.setMode(response.getMode() == null ? mode : response.getMode());
            update.setSpecJson(response.getSpec() == null ? null : objectMapper.writeValueAsString(response.getSpec()));
            update.setError(response.getError());
            if (response.getSpec() == null) {
                log.warn("3D 构建未产出 spec taskId={} error={}", taskId, response.getError());
            }
        } catch (Exception e) {
            update.setStatus("FAILED");
            update.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.warn("3D 构建任务失败 taskId={}: {}", taskId, update.getError());
        }
        update.setUpdateTime(LocalDateTime.now());
        taskMapper.update(update, new LambdaQueryWrapper<AiBuild3dTask>()
                .eq(AiBuild3dTask::getTaskId, taskId)
                .eq(AiBuild3dTask::getStatus, "RUNNING"));
    }

    private Build3dTaskVO toVO(AiBuild3dTask task) {
        Build3dTaskVO vo = new Build3dTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setStatus(task.getStatus());
        vo.setMode(task.getMode());
        vo.setError(task.getError());
        if (task.getSpecJson() != null) {
            try {
                vo.setSpec(objectMapper.readValue(task.getSpecJson(), AiContracts.Build3dSpec.class));
            } catch (Exception e) {
                vo.setStatus("FAILED");
                vo.setError("3D 结果格式损坏");
            }
        }
        return vo;
    }
}
