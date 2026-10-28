package com.dianfei.common.log;

/**
 * 操作日志存储 SPI：common 只定义接口，system 模块提供落库实现
 */
public interface OperLogStorage {

    /** 异步保存操作日志（实现方标注 @Async） */
    void save(OperLogRecord record);
}
