package com.dianfei.system.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.storage.FileStorage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 阿里云 OSS 存储实现（storage-type=oss；URL 保持 /api/file/preview 相对路径，
 * 预览时经 FileController 从 OSS 拉流转发，前端/存量数据零感知）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dianfei.file.storage-type", havingValue = "oss")
public class OssFileStorage implements FileStorage {

    /** 允许的扩展名白名单（与 LocalFileStorage 保持一致：图片 + 常见文档 + IM 音视频） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "pdf", "xlsx", "xls", "docx", "doc",
            "mp3", "wav", "amr", "ogg", "opus", "webm", "mp4", "m4a");

    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    @Value("${dianfei.file.oss.endpoint:}")
    private String endpoint;

    @Value("${dianfei.file.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${dianfei.file.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${dianfei.file.oss.bucket:}")
    private String bucket;

    @Value("${dianfei.file.url-prefix:/api/file/preview}")
    private String urlPrefix;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        // fail-closed：OSS 模式缺配拒绝启动（对齐 MinIO 模式行为）
        if (StrUtil.hasBlank(endpoint, accessKeyId, accessKeySecret, bucket)) {
            throw new IllegalStateException(
                    "storage-type=oss 但 OSS 配置缺失：请配置 DIANFEI_OSS_ENDPOINT / DIANFEI_OSS_ACCESS_KEY_ID / DIANFEI_OSS_ACCESS_KEY_SECRET / DIANFEI_OSS_BUCKET");
        }
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        log.info("[文件存储] 阿里云 OSS 初始化完成 bucket={} endpoint={}", bucket, endpoint);
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    @Override
    public String save(InputStream inputStream, String originalFilename, String bizDir) {
        String extension = StrUtil.emptyToDefault(FileUtil.extName(originalFilename), "").toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的文件类型：" + extension);
        }
        String safeBizDir = sanitize(StrUtil.blankToDefault(bizDir, "common"));
        String key = safeBizDir + "/" + LocalDate.now().format(DIR_FORMAT)
                + "/" + IdUtil.fastSimpleUUID() + "." + extension;
        try {
            ossClient.putObject(bucket, key, inputStream);
        } catch (OSSException e) {
            log.error("[文件存储] OSS 上传失败 key={} bucket={}", key, bucket, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件保存失败");
        }
        return urlPrefix + "/" + key;
    }

    @Override
    public InputStream load(String relativePath) {
        if (!isSafeKey(relativePath)) {
            log.warn("[文件存储] 拦截非法对象 key path={}", relativePath);
            return null;
        }
        try {
            OSSObject object = ossClient.getObject(bucket, relativePath);
            return object.getObjectContent();
        } catch (OSSException e) {
            // NoSuchKey：文件不存在按 404 处理；其余网络/服务异常记录告警日志
            log.warn("[文件存储] OSS 读取失败 key={}: {}", relativePath, e.getErrorCode());
            return null;
        }
    }

    @Override
    public void delete(String relativePath) {
        if (!isSafeKey(relativePath)) {
            log.warn("[文件存储] 拦截非法对象 key path={}", relativePath);
            return;
        }
        try {
            ossClient.deleteObject(bucket, relativePath);
        } catch (OSSException e) {
            // 删除不存在的对象幂等（阿里云不报错），仅网络异常记日志
            log.warn("[文件存储] OSS 删除失败 key={}: {}", relativePath, e.getErrorCode());
        }
    }

    /** 对象 key 安全校验：拒绝路径穿越（../）与绝对路径 */
    private boolean isSafeKey(String relativePath) {
        if (StrUtil.isBlank(relativePath) || relativePath.startsWith("/")) {
            return false;
        }
        String normalized = relativePath.replace('\\', '/');
        return !normalized.contains("../");
    }

    private String sanitize(String dir) {
        return dir.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
