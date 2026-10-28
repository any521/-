package com.dianfei.system.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.storage.FileStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * MinIO 对象存储实现（storage-type=minio）：与 LocalFileStorage 同接口、同 URL 语义，
 * 切换后已入库的相对路径（storagePath）即对象 key，/api/file/preview/** 预览链路零改动。
 * 依赖 io.minio:minio（8.5.12），凭据由 dianfei.file.minio.* 注入（缺配启动即拒绝，fail-closed）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dianfei.file.storage-type", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    /** 允许的扩展名白名单（与 LocalFileStorage 保持一致） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "pdf", "xlsx", "xls", "docx", "doc",
            "mp3", "wav", "amr", "ogg", "opus", "webm", "mp4", "m4a");

    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    @Value("${dianfei.file.minio.endpoint:}")
    private String endpoint;

    @Value("${dianfei.file.minio.access-key:}")
    private String accessKey;

    @Value("${dianfei.file.minio.secret-key:}")
    private String secretKey;

    @Value("${dianfei.file.minio.bucket:}")
    private String bucket;

    @Value("${dianfei.file.url-prefix:/api/file/preview}")
    private String urlPrefix;

    private MinioClient client;

    @jakarta.annotation.PostConstruct
    public void init() {
        // 缺配 fail-closed：与 JWT_SECRET 空值校验一致，避免误切 minio 后静默落盘失败
        if (StrUtil.isBlank(endpoint) || StrUtil.isBlank(accessKey)
                || StrUtil.isBlank(secretKey) || StrUtil.isBlank(bucket)) {
            throw new IllegalStateException(
                    "MinIO 存储配置缺失：需配置 dianfei.file.minio.endpoint/access-key/secret-key/bucket");
        }
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[文件存储] MinIO bucket 不存在，已自动创建 bucket={}", bucket);
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 连接初始化失败 endpoint=" + endpoint, e);
        }
        log.info("[文件存储] MinIO 初始化完成 endpoint={} bucket={}", endpoint, bucket);
    }

    @Override
    public String save(InputStream inputStream, String originalFilename, String bizDir) {
        String extension = StrUtil.emptyToDefault(FileUtil.extName(originalFilename), "").toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的文件类型：" + extension);
        }
        String safeBizDir = StrUtil.blankToDefault(bizDir, "common").replaceAll("[^a-zA-Z0-9_-]", "");
        // 对象 key 与本地磁盘相对路径同构：biz/yyyy/MM/uuid.ext（预览 URL 与已入库 storagePath 兼容）
        String key = safeBizDir + "/" + LocalDate.now().format(DIR_FORMAT)
                + "/" + IdUtil.fastSimpleUUID() + "." + extension;
        try {
            byte[] bytes = StreamUtils.copyToByteArray(inputStream);
            String contentType = MediaTypeFactory.getMediaType(key)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
            return urlPrefix + "/" + key;
        } catch (Exception e) {
            log.error("[文件存储] MinIO 写入失败 key={}", key, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件保存失败");
        }
    }

    @Override
    public InputStream load(String relativePath) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(relativePath)
                    .build());
        } catch (ErrorResponseException e) {
            // 对象不存在：与本地实现的 null 语义一致，由调用方回 404
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return null;
            }
            log.warn("[文件存储] MinIO 读取异常 path={} code={}", relativePath, e.errorResponse().code());
            return null;
        } catch (Exception e) {
            log.warn("[文件存储] MinIO 读取异常 path={}", relativePath, e);
            return null;
        }
    }

    @Override
    public void delete(String relativePath) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(relativePath)
                    .build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return; // 幂等：对象不存在视为已删除
            }
            log.warn("[文件存储] MinIO 删除异常 path={} code={}", relativePath, e.errorResponse().code());
        } catch (Exception e) {
            log.warn("[文件存储] MinIO 删除异常 path={}", relativePath, e);
        }
    }
}
