package com.dianfei.business.addressbook.controller;

import com.dianfei.business.addressbook.dto.AddressSaveDTO;
import com.dianfei.business.addressbook.entity.UserAddress;
import com.dianfei.business.addressbook.service.UserAddressService;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端 · 地址簿（/api/user/addresses）：
 * 预约回收/维修/商城收货/跳蚤收货的统一地址来源，省市区 + 详细地址 + 定位经纬度。
 */
@Tag(name = "用户端-地址簿")
@RestController
@RequestMapping("/api/user/addresses")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;

    @Operation(summary = "我的地址列表（默认地址排前）")
    @GetMapping
    public Result<List<UserAddress>> list() {
        return Result.success(addressService.listByUser(UserContext.getUserId()));
    }

    @Operation(summary = "新增地址（第一条自动设为默认；isDefault=1 时其余取消默认）")
    @PostMapping
    public Result<UserAddress> save(@Valid @RequestBody AddressSaveDTO dto) {
        return Result.success(addressService.save(UserContext.getUserId(), dto));
    }

    @Operation(summary = "更新地址（仅本人，归属校验失败 4004）")
    @PutMapping("/{id}")
    public Result<UserAddress> update(@PathVariable Long id, @Valid @RequestBody AddressSaveDTO dto) {
        return Result.success(addressService.update(UserContext.getUserId(), id, dto));
    }

    @Operation(summary = "删除地址（仅本人；删除默认地址后自动提升最近一条为默认）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(UserContext.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "设为默认地址")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(UserContext.getUserId(), id);
        return Result.success();
    }
}
