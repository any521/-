package com.dianfei.business.flea.service;

import com.dianfei.business.flea.dto.FleaOrderCreateDTO;
import com.dianfei.business.flea.dto.FleaReviewDTO;
import com.dianfei.business.flea.vo.FleaOrderVO;
import com.dianfei.common.result.PageVO;

/**
 * 跳蚤订单域（Upgrade U3）：积分托管结算全链路——
 * 下单冻结（freeze）→ 发货 → 收货结算（transfer）→ 评价；取消退冻（unfreeze）。
 * 状态机：0待发货 → 1待收货 → 2已完成；0待发货 → 3已取消；全部节点归属校验 + 状态机校验。
 */
public interface FleaOrderService {

    /** 下单：冻结买家积分 → 写订单（status=0）→ 商品防双卖 → 通知卖家；返回 orderNo + frozenPoints */
    FleaOrderVO create(Long userId, FleaOrderCreateDTO dto);

    /** 取消（仅买家 + 待发货）：解冻退分 → status=3 → 商品回在售 → 通知买家 */
    void cancel(Long orderId, Long userId);

    /** 发货（仅卖家 + 待发货）：status=1 + ship_time → 通知买家 */
    void ship(Long orderId, Long userId);

    /** 收货结算（仅买家 + 待收货）：transfer 原子转账 → status=2 + receive_time → 通知卖家 */
    void receive(Long orderId, Long userId);

    /** 评价（仅买家 + 已完成 + 未评过）：写评 → 通知卖家 */
    void review(Long orderId, Long userId, FleaReviewDTO dto);

    /** 我买到的分页（status 过滤，回填双方昵称/头像） */
    PageVO<FleaOrderVO> pageBuyer(Long userId, Integer status, int pageNum, int pageSize);

    /** 我卖出的分页（status 过滤，回填双方昵称/头像） */
    PageVO<FleaOrderVO> pageSeller(Long userId, Integer status, int pageNum, int pageSize);
}
