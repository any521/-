package com.dianfei.business.addressbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.addressbook.dto.AddressSaveDTO;
import com.dianfei.business.addressbook.entity.UserAddress;
import com.dianfei.business.addressbook.mapper.UserAddressMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户地址簿：全部操作限定本人（userId 恒从登录上下文取）。
 * 第一条地址自动设为默认；设默认/删默认时保证同用户至多一个默认。
 */
@Service
public class UserAddressService extends ServiceImpl<UserAddressMapper, UserAddress> {

    /** 我的地址列表（默认排前，其余按创建时间倒序） */
    public List<UserAddress> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddress save(Long userId, AddressSaveDTO dto) {
        UserAddress addr = new UserAddress();
        apply(addr, dto);
        addr.setUserId(userId);
        long count = count(new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, userId));
        boolean wantDefault = dto.getIsDefault() != null && dto.getIsDefault() == 1;
        addr.setIsDefault(wantDefault || count == 0 ? 1 : 0);
        if (addr.getIsDefault() == 1) {
            clearDefaultExcept(userId, null);
        }
        save(addr);
        return addr;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddress update(Long userId, Long id, AddressSaveDTO dto) {
        UserAddress addr = requireOwned(userId, id);
        apply(addr, dto);
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1 && addr.getIsDefault() != 1) {
            clearDefaultExcept(userId, id);
            addr.setIsDefault(1);
        }
        updateById(addr);
        return addr;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        UserAddress addr = requireOwned(userId, id);
        removeById(id);
        // 删的是默认地址：把最近一条提升为默认，保证始终有默认
        if (addr.getIsDefault() != null && addr.getIsDefault() == 1) {
            UserAddress latest = getOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getCreateTime)
                    .last("LIMIT 1"));
            if (latest != null) {
                latest.setIsDefault(1);
                updateById(latest);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        requireOwned(userId, id);
        clearDefaultExcept(userId, id);
        UserAddress addr = new UserAddress();
        addr.setId(id);
        addr.setIsDefault(1);
        updateById(addr);
    }

    /** 校验归属并返回（非本人/不存在 → 4004 NOT_FOUND） */
    private UserAddress requireOwned(Long userId, Long id) {
        UserAddress addr = getById(id);
        if (addr == null || !userId.equals(addr.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return addr;
    }

    /** 把该用户其余地址的默认位清零 */
    private void clearDefaultExcept(Long userId, Long excludeId) {
        UserAddress upd = new UserAddress();
        upd.setIsDefault(0);
        update(upd, new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .ne(excludeId != null, UserAddress::getId, excludeId));
    }

    private void apply(UserAddress addr, AddressSaveDTO dto) {
        addr.setContactName(StrUtil.blankToDefault(dto.getContactName(), ""));
        addr.setContactPhone(StrUtil.blankToDefault(dto.getContactPhone(), ""));
        addr.setProvince(StrUtil.blankToDefault(dto.getProvince(), ""));
        addr.setCity(StrUtil.blankToDefault(dto.getCity(), ""));
        addr.setDistrict(StrUtil.blankToDefault(dto.getDistrict(), ""));
        addr.setDetail(dto.getDetail().trim());
        addr.setLng(dto.getLng());
        addr.setLat(dto.getLat());
        addr.setAreaId(dto.getAreaId());
        addr.setTag(StrUtil.blankToDefault(dto.getTag(), "家"));
    }
}
