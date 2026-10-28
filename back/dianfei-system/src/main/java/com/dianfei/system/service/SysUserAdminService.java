package com.dianfei.system.service;

import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.system.dto.AdminUserSaveDTO;
import com.dianfei.system.vo.SysUserVO;

/**
 * 系统用户管理服务（管理端 G1）：分页 / 新增编辑 / 逻辑删除
 */
public interface SysUserAdminService {

    PageVO<SysUserVO> page(PageDTO page, String keyword, Integer status, Integer realNameStatus);

    /** id 空=新增，非空=编辑（前端只有 POST，无 PUT）；返回新增用户的一次性初始密码（编辑返回 null） */
    String save(AdminUserSaveDTO dto);

    void remove(Long id);

    /** 实名审核（GapFill B1）：仅 status=1 待审可审，pass→2 通过、false→3 驳回（写备注） */
    void auditRealName(Long userId, Boolean pass, String remark);
}
