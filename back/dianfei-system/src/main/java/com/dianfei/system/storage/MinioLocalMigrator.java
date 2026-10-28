package com.dianfei.system.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 本地磁盘 → MinIO 一次性迁移器（storage-type=minio 且 dianfei.file.minio.migrate-on-start=true 时启用）：
 * 启动时扫描 dianfei.file.local-path 下全部存量文件，按相对路径作为对象 key 上传到 MinIO
 * （与 sys_file_object.storagePath 同构，预览 URL 不变）；已存在的对象跳过（幂等，可重复执行）。
 * 迁移完成后建议在配置中关闭开关，避免每次启动全量扫描。
 */
@Slf4j
@Component
// 仅 minio 模式且显式开启迁移开关时启用（表达式写法兼容未配置 storage-type 的场景）
@ConditionalOnExpression("'${dianfei.file.storage-type:}' == 'minio' && '${dianfei.file.minio.migrate-on-start:}' == 'true'")
public class MinioLocalMigrator implements CommandLineRunner {

    @Value("${dianfei.file.local-path:./upload}")
    private String localPath;

    @Value("${dianfei.file.minio.endpoint:}")
    private String endpoint;

    @Value("${dianfei.file.minio.access-key:}")
    private String accessKey;

    @Value("${dianfei.file.minio.secret-key:}")
    private String secretKey;

    @Value("${dianfei.file.minio.bucket:}")
    private String bucket;

    @Override
    public void run(String... args) {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        Path root = Paths.get(localPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("[文件迁移] 本地存储目录不存在，跳过迁移 path={}", root);
            return;
        }
        AtomicInteger uploaded = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String key = root.relativize(file).toString().replace('\\', '/');
                try {
                    // 幂等：对象已存在则跳过（重复启动/部分失败重跑安全）
                    try {
                        client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
                        skipped.incrementAndGet();
                        return;
                    } catch (ErrorResponseException e) {
                        if (!"NoSuchKey".equals(e.errorResponse().code())) {
                            throw e;
                        }
                    }
                    String contentType = MediaTypeFactory.getMediaType(key)
                            .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
                    try (InputStream in = Files.newInputStream(file)) {
                        client.putObject(PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(key)
                                .stream(in, Files.size(file), -1)
                                .contentType(contentType)
                                .build());
                    }
                    uploaded.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("[文件迁移] 上传失败 key={}", key, e);
                }
            });
        } catch (IOException e) {
            log.error("[文件迁移] 扫描本地目录失败 path={}", root, e);
            return;
        }
        log.info("[文件迁移] 完成：新增 {} 个，已存在跳过 {} 个，失败 {} 个（bucket={}）",
                uploaded.get(), skipped.get(), failed.get(), bucket);
        if (failed.get() == 0 && uploaded.get() > 0) {
            log.info("[文件迁移] 存量文件已全部迁移至 MinIO，可关闭 dianfei.file.minio.migrate-on-start 开关");
        }
    }
}
