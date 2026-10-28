package com.dianfei.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.UserContext;
import com.dianfei.system.dto.AdminUserSaveDTO;
import com.dianfei.system.entity.SysRole;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysRoleMapper;
import com.dianfei.system.mapper.SysUserMapper;
import com.dianfei.system.service.SysUserAdminService;
import com.dianfei.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 系统用户管理实现（G1 补齐）：分页联角色名 / 新增编辑（id 判语义）/ 逻辑删除
 */
@Service
@RequiredArgsConstructor
public class SysUserAdminServiceImpl implements SysUserAdminService {

    /** C5 安全加固：新增用户不再使用固定默认密码，改为服务端随机生成强口令并一次性返回给管理员 */
    private static final int RANDOM_PASSWORD_LENGTH = 12;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageVO<SysUserVO> page(PageDTO page, String keyword, Integer status, Integer realNameStatus) {
        Page<SysUser> result = sysUserMapper.selectPage(
                new Page<>(page.getPageNum(), page.getPageSize()),
                new LambdaQueryWrapper<SysUser>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getNickname, keyword)
                                .or().like(SysUser::getEmail, keyword))
                        .eq(status != null, SysUser::getStatus, status)
                        .eq(realNameStatus != null, SysUser::getRealNameStatus, realNameStatus)
                        .orderByDesc(SysUser::getCreateTime));
        Map<String, String> roleNames = roleNameMap();
        List<SysUserVO> list = result.getRecords().stream()
                .map(u -> toVO(u, roleNames))
                .toList();
        return PageVO.of(result.getTotal(), result.getPages(), list);
    }

    @Override
    public String save(AdminUserSaveDTO dto) {
        if (dto.getId() == null) {
            return add(dto);
        }
        edit(dto);
        return null;
    }

    @Override
    public void remove(Long id) {
        Long currentUserId = UserContext.getUserId();
        SysUser currentUser = sysUserMapper.selectById(currentUserId);
        
        // 禁止删除其他管理员账号
        if (!id.equals(currentUserId)) {
            SysUser targetUser = sysUserMapper.selectById(id);
            if (targetUser != null && "ADMIN".equals(targetUser.getRole())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "禁止删除管理员账号");
            }
        }
        
        if (id.equals(currentUserId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能删除当前登录用户");
        }
        sysUserMapper.deleteById(id);
    }

    @Override
    public void auditRealName(Long userId, Boolean pass, String remark) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        Integer status = user.getRealNameStatus() == null ? 0 : user.getRealNameStatus();
        if (status != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待审（1）状态的实名申请可审核");
        }
        if (Boolean.TRUE.equals(pass)) {
            user.setRealNameStatus(2);
            user.setRealNameRemark(null);
        } else {
            user.setRealNameStatus(3);
            user.setRealNameRemark(remark);
        }
        sysUserMapper.updateById(user);
    }

    /** 新增：返回一次性初始密码明文（管理员转告用户后应即修改） */
    private String add(AdminUserSaveDTO dto) {
        checkUsernameUnique(dto.getUsername(), null);
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setRole(StrUtil.blankToDefault(dto.getRole(), "USER"));
        // C5 安全加固：管理端创建用户必须显式传密码，禁止落固定弱默认密码
        String initialPassword = dto.getPassword();
        if (StrUtil.isBlank(initialPassword)) {
            initialPassword = randomPassword();
        }
        user.setPassword(passwordEncoder.encode(initialPassword));
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        user.setPrincipalType("HUMAN");
        sysUserMapper.insert(user);
        return initialPassword;
    }

    private void edit(AdminUserSaveDTO dto) {
        SysUser old = sysUserMapper.selectById(dto.getId());
        if (old == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        checkUsernameUnique(dto.getUsername(), dto.getId());
        
        // 禁止修改其他管理员的角色/状态
        Long currentUserId = UserContext.getUserId();
        if (!dto.getId().equals(currentUserId)) {
            SysUser currentUser = sysUserMapper.selectById(currentUserId);
            if (currentUser != null && "ADMIN".equals(currentUser.getRole())) {
                if ("ADMIN".equals(old.getRole())) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "禁止修改管理员账号信息");
                }
                // 禁止将普通用户升级为管理员
                if ("ADMIN".equals(dto.getRole())) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "禁止使用管理员账号创建管理员");
                }
            }
        }
        
        old.setUsername(dto.getUsername());
        if (dto.getNickname() != null) {
            old.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            old.setEmail(dto.getEmail());
        }
        if (StrUtil.isNotBlank(dto.getRole())) {
            old.setRole(dto.getRole());
        }
        if (StrUtil.isNotBlank(dto.getPassword())) {
            old.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getStatus() != null) {
            old.setStatus(dto.getStatus());
        }
        sysUserMapper.updateById(old);
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名已存在");
        }
    }

    /** 随机初始密码：大写+小写+数字+符号，长度 12（不使用固定弱默认密码） */
    private String randomPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digit = "23456789";
        String symbol = "!@#$%";
        String pool = upper + lower + digit + symbol;
        StringBuilder sb = new StringBuilder(RANDOM_PASSWORD_LENGTH);
        sb.append(upper.charAt(ThreadLocalRandom.current().nextInt(upper.length())));
        sb.append(lower.charAt(ThreadLocalRandom.current().nextInt(lower.length())));
        sb.append(digit.charAt(ThreadLocalRandom.current().nextInt(digit.length())));
        sb.append(symbol.charAt(ThreadLocalRandom.current().nextInt(symbol.length())));
        for (int i = 4; i < RANDOM_PASSWORD_LENGTH; i++) {
            sb.append(pool.charAt(ThreadLocalRandom.current().nextInt(pool.length())));
        }
        return sb.toString();
    }

    /** role_code → role_name（查不到时回退角色码，由 toVO 处理） */
    private Map<String, String> roleNameMap() {
        return sysRoleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getRoleCode, SysRole::getRoleName, (a, b) -> a));
    }

    private SysUserVO toVO(SysUser user, Map<String, String> roleNames) {
        return SysUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .roleName(roleNames.getOrDefault(user.getRole(), user.getRole()))
                .status(user.getStatus())
                .realNameStatus(user.getRealNameStatus() == null ? 0 : user.getRealNameStatus())
                .realNameRemark(user.getRealNameRemark())
                .createTime(user.getCreateTime() == null ? null : user.getCreateTime().format(TIME_FMT))
                .build();
    }
}
