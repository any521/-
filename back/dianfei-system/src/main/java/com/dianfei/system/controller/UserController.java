package com.dianfei.system.controller;

import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.LoginUser;
import com.dianfei.common.security.UserContext;
import com.dianfei.system.dto.AvatarUpdateDTO;
import com.dianfei.system.dto.NicknameUpdateDTO;
import com.dianfei.system.dto.RealNameSubmitDTO;
import com.dianfei.system.dto.RegisterDTO;
import com.dianfei.system.dto.SetPasswordDTO;
import com.dianfei.system.dto.VerifyCodeDTO;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.StaffInfoMapper;
import com.dianfei.system.mapper.SysUserMapper;
import com.dianfei.system.service.AuthService;
import com.dianfei.system.service.MenuService;
import com.dianfei.system.vo.ProfileVO;
import com.dianfei.system.vo.RealNameStatusVO;
import com.dianfei.system.vo.StaffInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户开放接口：注册 / 验证码 / 当前用户画像（12 章契约：/api/user/*）
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final MenuService menuService;
    private final SysUserMapper sysUserMapper;
    private final StaffInfoMapper staffInfoMapper;
    private final com.dianfei.common.utils.SensitiveFieldUtil sensitiveFieldUtil;

    @Operation(summary = "发送邮箱验证码（随机 6 位 + 163 SMTP 发送 + Redis 限流）")
    @PostMapping("/verify-code")
    public Result<Void> verifyCode(@Valid @RequestBody VerifyCodeDTO dto) {
        authService.sendVerifyCode(dto.getEmail());
        return Result.success();
    }

    @Operation(summary = "邮箱注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    @Operation(summary = "设置密码（登录态 + 邮箱验证码二次校验，验证码替代旧密码证明持有邮箱）")
    @OperLog(value = "设置密码", module = "用户中心")
    @PutMapping("/password")
    public Result<Void> setPassword(@Valid @RequestBody SetPasswordDTO dto) {
        authService.setPassword(dto);
        return Result.success();
    }

    @Operation(summary = "当前用户画像：基本信息 + 权限码 + 菜单树（供前端动态路由）")
    @GetMapping("/profile")
    public Result<ProfileVO> profile() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        return Result.success(buildProfile(user));
    }

    @Operation(summary = "更新头像（仅接受本站上传 URL 或 http(s) 图片地址，防任意 URL 注入）")
    @OperLog(value = "修改头像", module = "用户中心")
    @PutMapping("/avatar")
    public Result<ProfileVO> updateAvatar(@Valid @RequestBody AvatarUpdateDTO dto) {
        String avatarUrl = dto.getAvatarUrl().trim();
        if (!avatarUrl.startsWith("/api/file/preview/")
                && !avatarUrl.startsWith("http://")
                && !avatarUrl.startsWith("https://")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "头像地址不合法");
        }
        SysUser user = currentUser();
        user.setAvatar(avatarUrl);
        sysUserMapper.updateById(user);
        return Result.success(buildProfile(user));
    }

    @Operation(summary = "修改显示昵称（n7：仅更新 sys_user.nickname，头像等其余字段不动）")
    @OperLog(value = "修改昵称", module = "用户中心")
    @PutMapping("/nickname")
    public Result<ProfileVO> updateNickname(@Valid @RequestBody NicknameUpdateDTO dto) {
        SysUser user = currentUser();
        user.setNickname(dto.getNickname().trim());
        sysUserMapper.updateById(user);
        return Result.success(buildProfile(user));
    }

    @Operation(summary = "提交实名认证（幂等：已通过不可重复提交，待审可覆盖更新）")
    @OperLog(value = "提交实名认证", module = "用户中心")
    @PutMapping("/real-name")
    public Result<Void> submitRealName(@Valid @RequestBody RealNameSubmitDTO dto) {
        SysUser user = currentUser();
        Integer status = user.getRealNameStatus() == null ? 0 : user.getRealNameStatus();
        if (status == 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "已通过实名认证，不可重复提交");
        }
        user.setRealName(dto.getRealName());
        // C10 安全加固：身份证加密后入库（明文不落库）；解密见 SensitiveFieldUtil.decrypt
        user.setIdCard(sensitiveFieldUtil.encrypt(dto.getIdCard()));
        if (status != 1) {
            user.setRealNameStatus(1);
        }
        user.setRealNameRemark(null);
        sysUserMapper.updateById(user);
        return Result.success();
    }

    @Operation(summary = "实名认证状态（0未认证 1待审 2已通过 3已驳回）")
    @GetMapping("/real-name-status")
    public Result<RealNameStatusVO> realNameStatus() {
        SysUser user = currentUser();
        return Result.success(RealNameStatusVO.builder()
                .status(user.getRealNameStatus() == null ? 0 : user.getRealNameStatus())
                .remark(user.getRealNameRemark())
                .realName(user.getRealName())
                .build());
    }

    private ProfileVO buildProfile(SysUser user) {
        StaffInfoVO staffInfo = staffInfoMapper.selectLatestByUserId(user.getId());
        String role = user.getRole();
        // 在岗档案工种优先：员工端角色视图以 biz_staff.staff_type 为准（1回收/2环卫/3技术），
        // 兜底历史不一致数据（改工种后 sys_user.role 未同步，前端按旧角色展示模块、操作被后端按档案拒绝）
        if (staffInfo != null && staffInfo.getAuditStatus() != null && staffInfo.getAuditStatus() == 1
                && staffInfo.getStatus() != null && staffInfo.getStatus() == 1) {
            String mapped = staffTypeToRole(staffInfo.getStaffType());
            if (mapped != null) {
                role = mapped;
            }
        }
        return ProfileVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .email(user.getEmail())
                .role(role)
                .areaId(user.getAreaId())
                .staffInfo(staffInfo)
                .perms(menuService.permsByRoleCode(role))
                .menus(menuService.treeByRoleCode(role))
                .build();
    }

    /** 员工工种 → 系统角色编码（1回收员 2环卫员 3技术员；非员工工种返回 null） */
    private static String staffTypeToRole(Integer staffType) {
        return switch (staffType == null ? -1 : staffType) {
            case 1 -> "RECYCLER";
            case 2 -> "SANITATION";
            case 3 -> "TECHNICIAN";
            default -> null;
        };
    }

    private SysUser currentUser() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        return user;
    }
}
