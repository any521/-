package com.dianfei.system.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 当前登录用户画像：基本信息 + 权限码 + 菜单树（供前端动态路由 / v-permission）
 */
@Data
@Builder
@Schema(description = "当前用户信息")
public class ProfileVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    /** 角色编码：USER / RECYCLER / SANITATION / TECHNICIAN / ADMIN */
    private String role;

    /** 员工所属片区 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long areaId;

    /** 工作人员身份（biz_staff 最新一条；无则 null，G1 C-O1 双身份契约） */
    @Schema(description = "工作人员身份摘要（未申请为 null）")
    private StaffInfoVO staffInfo;

    @Schema(description = "按钮级权限码集合，如 system:role:add")
    private Set<String> perms;

    @Schema(description = "菜单树（目录+菜单，供动态路由）")
    private List<MenuTreeVO> menus;
}
