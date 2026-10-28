package com.dianfei.system.controller;

import cn.hutool.core.io.FileUtil;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.storage.FileStorage;
import com.dianfei.common.security.UserContext;
import com.dianfei.system.entity.SysFileObject;
import com.dianfei.system.mapper.SysFileObjectMapper;
import cn.hutool.core.util.IdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传/预览（A4）：经 FileStorage 抽象存取，默认本地磁盘，预留 OSS/MinIO
 */
@Tag(name = "系统管理-文件")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileStorage fileStorage;
    private final SysFileObjectMapper fileObjectMapper;

    /** 业务目录白名单（Upgrade U1+V42+徽章市场）：不在名单内一律 400 */
    private static final Set<String> ALLOWED_BIZ = Set.of(
            "avatar", "product", "diy", "flea", "badge", "im", "repair", "recycle", "kitchen", "common",
            "science-article", "science-cover", "voice");

    /** C17：图片类业务（Content-Type + magic bytes 双校验，禁止伪装扩展名上传非图片） */
    private static final Set<String> IMAGE_BIZ = Set.of("avatar", "product", "diy", "flea", "badge", "voice");

    /** C17：现场记录类业务（同为图片，但由拍照复核链路使用，语义即现场照片） */
    private static final Set<String> IMAGE_RECORD_BIZ = Set.of("repair", "recycle", "kitchen");

    /** C17：IM 媒体（图片/音频/视频，图片仍需 magic bytes 校验，音视频按扩展名白名单 + 大小上限） */
    private static final Set<String> MEDIA_BIZ = Set.of("im");
    private static final Set<String> PRIVATE_BIZ = Set.of("im", "repair", "recycle", "kitchen", "science", "voice");

    /** 图片大小上限：普通图片 5MB，头像专用 2MB */
    private static final long IMAGE_MAX_SIZE = 5 * 1024 * 1024L;
    private static final long AVATAR_MAX_SIZE = 2 * 1024 * 1024L;
    /** C17：IM/track 媒体上限（图片/语音 10MB），common 通用文件上限 10MB */
    private static final long MEDIA_MAX_SIZE = 10 * 1024 * 1024L;
    /** IM 视频上限 500MB（手机拍摄长视频；与 nginx client_max_body_size 500m 对齐） */
    private static final long MEDIA_VIDEO_MAX_SIZE = 500 * 1024 * 1024L;
    private static final long COMMON_MAX_SIZE = 10 * 1024 * 1024L;
    /** V42：科普文章 PDF 上限 50MB，封面图片上限 5MB */
    private static final long SCIENCE_PDF_MAX_SIZE = 50 * 1024 * 1024L;
    private static final long SCIENCE_COVER_MAX_SIZE = 5 * 1024 * 1024L;

    @Operation(summary = "上传文件（三端通用，需登录；biz 为业务目录如 avatar/repair/recycle）")
    @OperLog(value = "上传文件", module = "系统管理", saveParams = false)
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "biz", defaultValue = "common") String biz) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }
        if (!ALLOWED_BIZ.contains(biz)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的业务目录：" + biz);
        }
        
        // V42：科普专栏专用校验逻辑
        if ("science-article".equals(biz)) {
            return uploadScienceArticle(file);
        }
        if ("science-cover".equals(biz)) {
            return uploadScienceCover(file);
        }
        
        // ========== 原有业务逻辑保持不变 ==========
        String contentType = file.getContentType();
        String normalizedType = contentType == null ? "" : contentType.toLowerCase();
        if (IMAGE_BIZ.contains(biz) || IMAGE_RECORD_BIZ.contains(biz)) {
            long maxSize = "avatar".equals(biz) ? AVATAR_MAX_SIZE : IMAGE_MAX_SIZE;
            if (!normalizedType.startsWith("image/")) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该业务目录仅支持图片文件");
            }
            // C17：magic bytes 魔数校验（Content-Type 可客户端伪造，文件头不可伪造）
            if (!isImageMagic(file)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "文件内容不是有效图片（magic bytes 校验失败）");
            }
            if (file.getSize() > maxSize) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "图片大小超过限制（" + (maxSize / 1024 / 1024) + "MB）");
            }
        } else if (MEDIA_BIZ.contains(biz)) {
            boolean imageLike = normalizedType.startsWith("image/")
                    || normalizedType.startsWith("audio/") || normalizedType.startsWith("video/");
            if (!imageLike) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "IM 目录仅支持图片/音频/视频文件");
            }
            // 图片类仍需文件头校验，音视频依赖扩展名白名单（LocalFileStorage ALLOWED_EXTENSIONS）
            if (normalizedType.startsWith("image/") && !isImageMagic(file)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "文件内容不是有效图片（magic bytes 校验失败）");
            }
            boolean isVideo = normalizedType.startsWith("video/");
            long mediaLimit = isVideo ? MEDIA_VIDEO_MAX_SIZE : MEDIA_MAX_SIZE;
            if (file.getSize() > mediaLimit) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "文件大小超过限制（" + (isVideo ? "视频500MB" : "10MB") + "）");
            }
        } else { // common 通用文件
            if (file.getSize() > COMMON_MAX_SIZE) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "文件大小超过限制（10MB）");
            }
        }
        try (InputStream in = file.getInputStream()) {
            String url = fileStorage.save(in, file.getOriginalFilename(), biz);
            String prefix = "/api/file/preview/";
            String storagePath = url.startsWith(prefix) ? url.substring(prefix.length()) : null;
            if (storagePath == null || UserContext.getUserId() == null) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件对象登记失败");
            }
            SysFileObject object = new SysFileObject();
            object.setId(IdUtil.getSnowflakeNextId());
            object.setStoragePath(storagePath);
            object.setOwnerId(UserContext.getUserId());
            object.setBiz(biz);
            object.setPublicAccess(PRIVATE_BIZ.contains(biz) ? 0 : 1);
            fileObjectMapper.insert(object);
            return Result.success(Map.of(
                    "id", object.getId().toString(),
                    "url", url,
                    "name", FileUtil.getName(file.getOriginalFilename())));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件读取失败");
        }
    }

    /**
     * C17：魔数（magic bytes）校验文件头是否为常见图片格式。
     * PNG(89 50 4E 47) / JPEG(FF D8 FF) / GIF(GIF8) / WebP(RIFF..WEBP) / BMP(BM)。
     * 只认可列表内格式；其余一律拒绝（防伪造 Content-Type/扩展名上传可执行文件）。
     */
    private boolean isImageMagic(MultipartFile file) {
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(head);
            if (read < 4) {
                return false;
            }
            if ((head[0] & 0xFF) == 0x89 && (head[1] & 0xFF) == 0x50
                    && (head[2] & 0xFF) == 0x4E && (head[3] & 0xFF) == 0x47) {
                return true; // PNG
            }
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return true; // JPEG
            }
            if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
                return true; // GIF
            }
            if (read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
                return true; // WebP
            }
            if (head[0] == 'B' && head[1] == 'M') {
                return true; // BMP
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
    
    /** V42：科普 PDF 上传专用方法（校验 .pdf 扩展名 + 魔数%PDF-，最大 50MB） */
    private Result<Map<String, String>> uploadScienceArticle(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (!originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅限 PDF 文件");
        }
        // 简单魔数校验：%PDF-
        byte[] magic = new byte[5];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(magic);
            if (read < 5 || !"%PDF-".equals(new String(magic))) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "不是有效的 PDF 文件");
            }
            if (file.getSize() > SCIENCE_PDF_MAX_SIZE) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "PDF 大小超过限制（" + (SCIENCE_PDF_MAX_SIZE / 1024 / 1024) + "MB）");
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件读取失败");
        }
        return saveFile(file, "science-article");
    }
    
    /** V42：科普封面图片上传专用方法（同普通图片校验） */
    private Result<Map<String, String>> uploadScienceCover(MultipartFile file) {
        long maxSize = SCIENCE_COVER_MAX_SIZE;
        String contentType = file.getContentType();
        String normalizedType = contentType == null ? "" : contentType.toLowerCase();
        if (!normalizedType.startsWith("image/")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅限图片文件");
        }
        if (!isImageMagic(file)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件内容不是有效图片（magic bytes 校验失败）");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "图片大小超过限制（" + (maxSize / 1024 / 1024) + "MB）");
        }
        return saveFile(file, "science-cover");
    }
    
    /** 保存文件至本地存储并登记 sys_file_object（内部复用） */
    private Result<Map<String, String>> saveFile(MultipartFile file, String bizPrefix) {
        try (InputStream in = file.getInputStream()) {
            String url = fileStorage.save(in, file.getOriginalFilename(), "science");
            String prefix = "/api/file/preview/";
            String storagePath = url.startsWith(prefix) ? url.substring(prefix.length()) : null;
            if (storagePath == null || UserContext.getUserId() == null) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件对象登记失败");
            }
            SysFileObject object = new SysFileObject();
            object.setId(IdUtil.getSnowflakeNextId());
            object.setStoragePath(storagePath);
            object.setOwnerId(UserContext.getUserId());
            object.setBiz(bizPrefix);
            object.setPublicAccess(PRIVATE_BIZ.contains("science") ? 0 : 1);
            fileObjectMapper.insert(object);
            return Result.success(Map.of(
                    "id", object.getId().toString(),
                    "url", url,
                    "name", FileUtil.getName(file.getOriginalFilename())));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件读取失败");
        }
    }

    @Operation(summary = "预览/下载文件（匿名可访问）")
    @GetMapping("/preview/**")
    public void preview(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        // C17：空路径/未知映射防御：/api/file/preview 无子路径或无 attribute 一律 404，不再 500
        if (fullPath == null || !fullPath.startsWith("/api/file/preview/")
                || fullPath.length() == "/api/file/preview/".length()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String relativePath;
        try {
            relativePath = UriUtils.decode(
                    fullPath.substring("/api/file/preview/".length()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // C17：非法 % 编码直接 404，不抛 500
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (relativePath.isBlank()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String topLevel = relativePath.split("/", 2)[0];
        if (PRIVATE_BIZ.contains(topLevel) && UserContext.getUserId() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        // science 科普内容公开给登录用户（封面/PDF 预览不要求 owner/admin，与前台列表认证策略一致）
        // im 聊天媒体（图片/语音/视频）：会话双方需随时查看，任意登录用户可访问（路径含雪花 ID 不可枚举，保留登录门槛）；
        // 其余私密业务（现场记录等）仍要求 owner/admin，防止越权读取他人业务文件
        if (PRIVATE_BIZ.contains(topLevel) && !"science".equals(topLevel) && !"im".equals(topLevel)) {
            SysFileObject object = fileObjectMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysFileObject>()
                    .eq(SysFileObject::getStoragePath, relativePath)
                    .eq(SysFileObject::getPublicAccess, 0)
                    .last("LIMIT 1"));
            boolean owner = object != null && UserContext.getUserId().equals(object.getOwnerId());
            boolean admin = "ADMIN".equals(UserContext.getRole());
            if (!owner && !admin) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        try (InputStream in = fileStorage.load(relativePath)) {
            if (in == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String contentType = MediaTypeFactory.getMediaType(relativePath)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
            response.setContentType(contentType);
            // 下载模式（?download=1）：附件响应头，浏览器直接保存文件（预览默认内联）。
            // 移动端曾用 blob+a[download]，在微信内置浏览器/部分安卓浏览器会尝试打开 blob: URL 报"链接无效"，
            // 改用直链附件下载可全端统一触发保存
            if ("1".equals(request.getParameter("download"))) {
                String filename = FileUtil.getName(relativePath);
                String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
                response.setHeader("Content-Disposition",
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            }
            StreamUtils.copy(in, response.getOutputStream());
        }
    }
}
