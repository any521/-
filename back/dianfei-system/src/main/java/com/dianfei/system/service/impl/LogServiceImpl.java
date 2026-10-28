package com.dianfei.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.common.log.OperLogRecord;
import com.dianfei.common.log.OperLogStorage;
import com.dianfei.common.result.PageVO;
import com.dianfei.system.dto.LoginLogQueryDTO;
import com.dianfei.system.dto.OperLogQueryDTO;
import com.dianfei.system.entity.SysLoginLog;
import com.dianfei.system.entity.SysOperLog;
import com.dianfei.system.mapper.SysLoginLogMapper;
import com.dianfei.system.mapper.SysOperLogMapper;
import com.dianfei.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 日志服务实现：
 * 1. 操作/登录日志分页查询（管理端）
 * 2. OperLogStorage SPI 落库实现（@OperLog 切面经此异步写 sys_oper_log）
 * 3. 登录日志异步记录（成功/失败均记，不阻塞登录主流程）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService, OperLogStorage {

    private final SysOperLogMapper sysOperLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;

    @Override
    public PageVO<SysOperLog> pageOperLog(OperLogQueryDTO query) {
        Page<SysOperLog> result = sysOperLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<SysOperLog>()
                        .like(StrUtil.isNotBlank(query.getModule()), SysOperLog::getModule, query.getModule())
                        .like(StrUtil.isNotBlank(query.getOperName()), SysOperLog::getOperName, query.getOperName())
                        .eq(query.getStatus() != null, SysOperLog::getStatus, query.getStatus())
                        .ge(query.getBeginTime() != null, SysOperLog::getOperTime, query.getBeginTime())
                        .le(query.getEndTime() != null, SysOperLog::getOperTime, query.getEndTime())
                        .orderByDesc(SysOperLog::getOperTime));
        return PageVO.of(result);
    }

    @Override
    public PageVO<SysLoginLog> pageLoginLog(LoginLogQueryDTO query) {
        Page<SysLoginLog> result = sysLoginLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<SysLoginLog>()
                        .like(StrUtil.isNotBlank(query.getUsername()), SysLoginLog::getUsername, query.getUsername())
                        .eq(StrUtil.isNotBlank(query.getClientType()), SysLoginLog::getClientType, query.getClientType())
                        .eq(query.getStatus() != null, SysLoginLog::getStatus, query.getStatus())
                        .ge(query.getBeginTime() != null, SysLoginLog::getLoginTime, query.getBeginTime())
                        .le(query.getEndTime() != null, SysLoginLog::getLoginTime, query.getEndTime())
                        .orderByDesc(SysLoginLog::getLoginTime));
        return PageVO.of(result);
    }

    /** OperLogStorage SPI：@OperLog 切面异步落库入口 */
    @Async
    @Override
    public void save(OperLogRecord record) {
        try {
            sysOperLogMapper.insert(BeanUtil.copyProperties(record, SysOperLog.class));
        } catch (Exception e) {
            log.warn("[操作日志] 落库失败，不影响主流程", e);
        }
    }

    @Async
    @Override
    public void recordLoginLog(String username, String clientType, Integer status, String msg,
                               String loginIp, String userAgent) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUsername(username);
            loginLog.setClientType(clientType);
            loginLog.setStatus(status);
            loginLog.setMsg(msg);
            loginLog.setLoginIp(loginIp);
            loginLog.setUserAgent(userAgent);
            loginLog.setLoginTime(LocalDateTime.now());
            sysLoginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.warn("[登录日志] 落库失败，不影响主流程", e);
        }
    }
}
