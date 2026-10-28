package com.dianfei.business.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.mall.dto.MallProductSaveDTO;
import com.dianfei.business.mall.entity.MallProduct;
import com.dianfei.business.mall.mapper.MallProductMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城商品（C3）：用户端上架列表；后台 CRUD、上下架、库存管理
 */
@Service
public class MallProductService extends ServiceImpl<MallProductMapper, MallProduct> {

    /** 用户端：上架商品分页（按积分价升序） */
    public PageVO<MallProduct> pageOnSale(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<MallProduct> wrapper = new LambdaQueryWrapper<MallProduct>()
                .eq(MallProduct::getStatus, MallProduct.STATUS_ON)
                .like(keyword != null && !keyword.isBlank(), MallProduct::getProductName, keyword)
                .orderByAsc(MallProduct::getPointsPrice);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /** 后台：全量商品分页 */
    public PageVO<MallProduct> pageAdmin(int pageNum, int pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<MallProduct> wrapper = new LambdaQueryWrapper<MallProduct>()
                .like(keyword != null && !keyword.isBlank(), MallProduct::getProductName, keyword)
                .eq(status != null, MallProduct::getStatus, status)
                .orderByDesc(MallProduct::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /** 后台：新增/编辑（编辑不直接覆盖库存，库存走 adjustStock） */
    @Transactional
    public MallProduct saveProduct(MallProductSaveDTO dto) {
        MallProduct product;
        if (dto.getId() == null) {
            product = new MallProduct();
            product.setStock(dto.getStock() == null ? 0 : dto.getStock());
            product.setStatus(dto.getStatus() == null ? MallProduct.STATUS_OFF : dto.getStatus());
        } else {
            product = requireProduct(dto.getId());
            if (dto.getStatus() != null) {
                product.setStatus(dto.getStatus());
            }
        }
        product.setProductName(dto.getProductName());
        product.setCoverUrl(dto.getCoverUrl());
        product.setPointsPrice(dto.getPointsPrice());
        product.setDescription(dto.getDescription());
        saveOrUpdate(product);
        return product;
    }

    /** 后台：上/下架 */
    @Transactional
    public void changeStatus(Long productId, boolean onSale) {
        MallProduct product = requireProduct(productId);
        product.setStatus(onSale ? MallProduct.STATUS_ON : MallProduct.STATUS_OFF);
        updateById(product);
    }

    /** 后台：库存增减（delta 正=入库 负=核减），核减不允许出现负库存 */
    @Transactional
    public void adjustStock(Long productId, int delta) {
        if (delta == 0) {
            throw new BusinessException("库存调整量不能为 0");
        }
        MallProduct product = requireProduct(productId);
        if (delta > 0) {
            baseMapper.restoreStock(productId, delta);
        } else if (baseMapper.deductStock(productId, -delta) == 0 && product.getStatus() == MallProduct.STATUS_ON) {
            throw new BusinessException("库存不足，无法核减");
        } else if (product.getStatus() == MallProduct.STATUS_OFF) {
            // 下架商品核减走直接更新（deductStock 仅限上架），仍禁止负库存
            int stock = product.getStock() + delta;
            if (stock < 0) {
                throw new BusinessException("库存不足，无法核减");
            }
            product.setStock(stock);
            updateById(product);
        }
    }

    /** 兑换前校验：商品存在且上架 */
    public MallProduct requireOnSale(Long productId) {
        MallProduct product = requireProduct(productId);
        if (product.getStatus() != MallProduct.STATUS_ON) {
            throw new BusinessException("商品已下架");
        }
        return product;
    }

    public MallProduct requireProduct(Long productId) {
        MallProduct product = getById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        return product;
    }
}
