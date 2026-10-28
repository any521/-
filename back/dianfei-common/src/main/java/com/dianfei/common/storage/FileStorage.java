package com.dianfei.common.storage;

import java.io.InputStream;

/**
 * 文件存储抽象（A4）：默认本地磁盘实现，预留 OSS/MinIO 切换（dianfei.file.storage-type）
 */
public interface FileStorage {

    /**
     * 保存文件
     *
     * @param inputStream      文件流
     * @param originalFilename 原始文件名（用于取扩展名）
     * @param bizDir           业务目录（如 avatar / repair / recycle）
     * @return 可访问的相对 URL（如 /api/file/preview/avatar/2026/07/xxx.jpg）
     */
    String save(InputStream inputStream, String originalFilename, String bizDir);

    /**
     * 读取文件
     *
     * @param relativePath 相对存储根的路径（如 avatar/2026/07/xxx.jpg）
     * @return 文件流，不存在返回 null
     */
    InputStream load(String relativePath);

    /** 删除文件（幂等） */
    void delete(String relativePath);
}
