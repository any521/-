package com.dianfei.system.service;

import com.dianfei.common.result.PageVO;
import com.dianfei.system.dto.LoginLogQueryDTO;
import com.dianfei.system.dto.OperLogQueryDTO;
import com.dianfei.system.entity.SysLoginLog;
import com.dianfei.system.entity.SysOperLog;

/**
 * 日志服务：操作/登录日志查询与登录日志记录
 */
public interface LogService {

    PageVO<SysOperLog> pageOperLog(OperLogQueryDTO query);

    PageVO<SysLoginLog> pageLoginLog(LoginLogQueryDTO query);

    /** 异步记录登录日志（成功/失败均记，不阻塞登录主流程；IP/UA 由同步线程捕获后传入） */
    void recordLoginLog(String username, String clientType, Integer status, String msg,
                        String loginIp, String userAgent);
}
