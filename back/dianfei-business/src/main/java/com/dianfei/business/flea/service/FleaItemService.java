package com.dianfei.business.flea.service;

import com.dianfei.business.flea.dto.FleaItemCreateDTO;
import com.dianfei.business.flea.entity.FleaItem;
import com.dianfei.business.flea.vo.FleaItemVO;
import com.dianfei.common.result.PageVO;

/**
 * 闲置商品域（Upgrade U3）：发布 / 在售浏览 / 详情 / 本人下架；
 * 售出与回售由订单域经 markSold/backOnSale 驱动（防双卖条件更新）。
 */
public interface FleaItemService {

    /** 发布闲置（status=1 在售；category 枚举 + imageUrl 前缀校验） */
    FleaItemVO create(Long userId, FleaItemCreateDTO dto);

    /** 在售商品分页（category/keyword/minPrice/maxPrice 过滤，创建时间倒序，回填卖家昵称 + soldCount） */
    PageVO<FleaItemVO> page(String category, String keyword, Long minPrice, Long maxPrice,
                            int pageNum, int pageSize);

    /** 商品详情（含卖家统计；已删/违规下架 404「商品不存在」） */
    FleaItemVO detail(Long id);

    /** 下架我的商品（仅本人 + 在售） */
    void off(Long id, Long userId);

    /** 我的发布分页（本人全部状态，U4 追加契约 GET /flea/items/mine） */
    PageVO<FleaItemVO> minePage(Long userId, int pageNum, int pageSize);

    /** 管理端全量分页（含下架/违规/已售出，keyword/userId 过滤） */
    PageVO<FleaItemVO> pageOps(String keyword, Long userId, int pageNum, int pageSize);

    /** 违规下架（status=3，通知卖家） */
    void forbidden(Long id);

    /** 防双卖条件更新：1在售 → 2已售出；0 行抛 ORDER_STATUS_NOT_ALLOWED（调用方事务回滚） */
    void markSold(Long itemId);

    /** 取消订单回在售：仅 2已售出 → 1在售 */
    void backOnSale(Long itemId);

    /** 取在售商品，非在售抛 ORDER_STATUS_NOT_ALLOWED */
    FleaItem requireOnSale(Long itemId);
}
