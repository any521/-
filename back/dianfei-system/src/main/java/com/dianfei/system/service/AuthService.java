package com.dianfei.system.service;

import com.dianfei.system.dto.LoginByCodeDTO;
import com.dianfei.system.dto.LoginDTO;
import com.dianfei.system.dto.RegisterDTO;
import com.dianfei.system.dto.SetPasswordDTO;
import com.dianfei.system.vo.TokenVO;

/**
 * 认证服务：登录 / 刷新 / 登出 / 注册 / 验证码 / 设置密码
 */
public interface AuthService {

    /** 登录：BCrypt 校验 + 多端角色校验 + 签发双令牌（Refresh 存 Redis 绑定指纹） */
    TokenVO login(LoginDTO dto, String clientType, String fingerprint);

    /** 邮箱验证码登录：验证码即身份证明，未注册自动建号、已注册直接登录（不累计失败锁定） */
    TokenVO loginByCode(LoginByCodeDTO dto, String clientType, String fingerprint);

    /** 刷新：校验 Refresh Token 与 Redis 指纹一致后轮换双令牌；expectedClientType 为请求方 X-Client-Type（防跨端 Cookie 串号） */
    TokenVO refresh(String refreshToken, String fingerprint, String expectedClientType);

    /** 切换工作人员模式（双身份互通）：当前 USER 会话 + 在岗员工身份 → 签发 STAFF 会话并种 STAFF Refresh Cookie */
    TokenVO switchToStaff(String fingerprint);

    /** 切回用户模式（双身份互通）：当前 STAFF 会话 → 签发 USER 会话并种 USER Refresh Cookie（员工端切回用户端免登录） */
    TokenVO switchToUser(String fingerprint);

    /** 登出：删除 Redis 中的 Refresh Token */
    void logout();

    /** 发送邮箱验证码（随机 6 位 + 163 SMTP 真实发送 + Redis 限流 1 次/分钟） */
    void sendVerifyCode(String email);

    /** 邮箱注册（默认 USER 角色 / HUMAN 主体） */
    void register(RegisterDTO dto);

    /** 设置密码（登录态 + 邮箱验证码二次校验，验证码替代旧密码证明持有邮箱） */
    void setPassword(SetPasswordDTO dto);
}
