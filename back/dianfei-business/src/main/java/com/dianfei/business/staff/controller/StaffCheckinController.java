package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.dto.CheckinDTO;
import com.dianfei.business.staff.service.StaffCheckinService;
import com.dianfei.business.staff.vo.CheckinTodayVO;
import com.dianfei.business.staff.vo.CheckinVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工端 · 签到（G9）：上班/下班打卡、今日状态（12 章契约 /api/staff/checkin、/api/staff/checkin/today）
 */
@Tag(name = "员工端-签到")
@RestController
@RequestMapping("/api/staff/checkin")
@RequiredArgsConstructor
public class StaffCheckinController {

    private final StaffCheckinService checkinService;

    @Operation(summary = "打卡（type=ON 上班 / OFF 下班），返回本次打卡时间 yyyy-MM-dd HH:mm:ss")
    @PostMapping
    public Result<CheckinVO> checkin(@Valid @RequestBody CheckinDTO dto) {
        return Result.success(checkinService.checkin(UserContext.getUserId(), dto));
    }

    @Operation(summary = "今日签到状态（未打为空串）")
    @GetMapping("/today")
    public Result<CheckinTodayVO> today() {
        return Result.success(checkinService.today(UserContext.getUserId()));
    }
}
