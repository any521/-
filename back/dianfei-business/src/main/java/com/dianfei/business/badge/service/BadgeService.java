package com.dianfei.business.badge.service;

import com.dianfei.business.badge.dto.BadgeSaveDTO;
import com.dianfei.business.badge.vo.BadgeExchangeVO;
import com.dianfei.business.badge.vo.BadgeVO;
import com.dianfei.common.result.PageVO;

/**
 * 成就徽章域（积分市场合并升级）：用户端上架浏览/积分兑换（虚拟商品即到账）/我的徽章；
 * 管理端全量分页/新增编辑/上下架。扣分一律走碳账本 consume（order_type=BADGE）。
 */
public interface BadgeService {

    /** 用户端：上架徽章分页（sort 升序；登录态回填 owned） */
    PageVO<BadgeVO> pageOnSale(Long userId, String keyword, int pageNum, int pageSize);

    /** 用户端：积分兑换徽章（校验上架/防重复 → 落兑换单 → 账本扣分 → 回填流水，单事务） */
    BadgeExchangeVO exchange(Long userId, Long badgeId);

    /** 用户端：我的徽章分页（兑换时间倒序） */
    PageVO<BadgeExchangeVO> pageMine(Long userId, int pageNum, int pageSize);

    /** 管理端：徽章全量分页（含下架，keyword/status 过滤） */
    PageVO<BadgeVO> pageAdmin(String keyword, Integer status, int pageNum, int pageSize);

    /** 管理端：新增/编辑（code 唯一校验；编辑不覆盖已兑换记录的快照） */
    BadgeVO save(BadgeSaveDTO dto);

    /** 管理端：上/下架 */
    void changeStatus(Long id, boolean onSale);
}
