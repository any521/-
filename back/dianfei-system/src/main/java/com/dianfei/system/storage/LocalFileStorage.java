package com.dianfei.system.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.storage.FileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 本地磁盘存储实现（storage-type=local，默认）；OSS/MinIO 实现后按同接口替换
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dianfei.file.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    /** 允许的扩展名白名单（图片 + 常见文档 + IM 音视频） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "pdf", "xlsx", "xls", "docx", "doc",
            "mp3", "wav", "amr", "ogg", "opus", "webm", "mp4", "m4a");

    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    @Value("${dianfei.file.local-path:./upload}")
    private String localPath;

    @Value("${dianfei.file.url-prefix:/api/file/preview}")
    private String urlPrefix;

    @Override
    public String save(InputStream inputStream, String originalFilename, String bizDir) {
        String extension = StrUtil.emptyToDefault(FileUtil.extName(originalFilename), "").toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的文件类型：" + extension);
        }
        String safeBizDir = sanitize(StrUtil.blankToDefault(bizDir, "common"));
        String relativePath = safeBizDir + "/" + LocalDate.now().format(DIR_FORMAT)
                + "/" + IdUtil.fastSimpleUUID() + "." + extension;
        Path target = Paths.get(localPath, relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("[文件存储] 写入失败 path={}", target, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件保存失败");
        }
        return urlPrefix + "/" + relativePath;
    }

    @Override
    public InputStream load(String relativePath) {
        Path file = resolveSafely(relativePath);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return new BufferedInputStream(new FileInputStream(file.toFile()));
        } catch (IOException e) {
            // W-08: 预览读取失败记录告警日志（原静默吞异常），便于排查文件缺失/权限问题
            log.warn("文件预览读取失败: {}", relativePath, e);
            return null;
        }
    }

    @Override
    public void delete(String relativePath) {
        Path file = resolveSafely(relativePath);
        if (file != null) {
            FileUtil.del(file);
        }
    }

    /** 防路径穿越：解析后必须仍位于存储根目录内 */
    private Path resolveSafely(String relativePath) {
        if (StrUtil.isBlank(relativePath)) {
            return null;
        }
        Path root = Paths.get(localPath).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            log.warn("[文件存储] 拦截路径穿越请求 path={}", relativePath);
            return null;
        }
        return resolved;
    }

    private String sanitize(String dir) {
        return dir.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
