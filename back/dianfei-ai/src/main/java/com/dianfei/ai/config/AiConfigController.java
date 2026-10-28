package com.dianfei.ai.config;

import com.dianfei.ai.config.dto.AiProviderDTO;
import com.dianfei.ai.config.dto.AiRouteDTO;
import com.dianfei.ai.config.entity.AiProvider;
import com.dianfei.ai.config.entity.AiRoute;
import com.dianfei.ai.config.vo.AiConfigRuntimeVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模型配置管理（CCSwitch 风格：管理端 CRUD + Python 运行时拉取）
 * <p>runtime 接口供 ai-back 内网调用，X-Internal-Token 鉴权；
 * 未配置 token 时仅放行 dev + 本机回环来源（S1 安全加固，防密钥经局域网/公网泄漏）
 */
@Tag(name = "系统管理-AI模型配置")
@RestController
@RequestMapping("/api/ai/config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;
    private final AiServiceProperties properties;
    private final org.springframework.core.env.Environment environment;

    // ==================== 供应商 ====================

    @Operation(summary = "供应商分页列表")
    @RequiresRole("ADMIN")
    @GetMapping("/provider/page")
    public Result<PageVO<AiProvider>> providerPage(@Valid PageDTO page,
                                                    @RequestParam(required = false) String keyword) {
        return Result.success(aiConfigService.providerPage(page, keyword));
    }

    @Operation(summary = "新增供应商")
    @OperLog(value = "新增AI供应商", module = "系统管理")
    @RequiresRole("ADMIN")
    @PostMapping("/provider")
    public Result<Void> providerAdd(@Valid @RequestBody AiProviderDTO dto) {
        aiConfigService.providerAdd(dto);
        return Result.success();
    }

    @Operation(summary = "编辑供应商")
    @OperLog(value = "编辑AI供应商", module = "系统管理")
    @RequiresRole("ADMIN")
    @PutMapping("/provider")
    public Result<Void> providerEdit(@Valid @RequestBody AiProviderDTO dto) {
        aiConfigService.providerEdit(dto);
        return Result.success();
    }

    @Operation(summary = "删除供应商")
    @OperLog(value = "删除AI供应商", module = "系统管理")
    @RequiresRole("ADMIN")
    @DeleteMapping("/provider/{id}")
    public Result<Void> providerRemove(@PathVariable Long id) {
        aiConfigService.providerRemove(id);
        return Result.success();
    }

    @Operation(summary = "供应商连通性测试")
    @RequiresRole("ADMIN")
    @PostMapping("/provider/test/{id}")
    public Result<String> providerTest(@PathVariable Long id) {
        return Result.success(aiConfigService.providerTest(id));
    }

    // ==================== 路由 ====================

    @Operation(summary = "路由分页列表")
    @RequiresRole("ADMIN")
    @GetMapping("/route/page")
    public Result<PageVO<AiRoute>> routePage(@Valid PageDTO page,
                                              @RequestParam(required = false) String keyword) {
        return Result.success(aiConfigService.routePage(page, keyword));
    }

    @Operation(summary = "新增路由")
    @OperLog(value = "新增AI路由", module = "系统管理")
    @RequiresRole("ADMIN")
    @PostMapping("/route")
    public Result<Void> routeAdd(@Valid @RequestBody AiRouteDTO dto) {
        aiConfigService.routeAdd(dto);
        return Result.success();
    }

    @Operation(summary = "编辑路由")
    @OperLog(value = "编辑AI路由", module = "系统管理")
    @RequiresRole("ADMIN")
    @PutMapping("/route")
    public Result<Void> routeEdit(@Valid @RequestBody AiRouteDTO dto) {
        aiConfigService.routeEdit(dto);
        return Result.success();
    }

    @Operation(summary = "删除路由")
    @OperLog(value = "删除AI路由", module = "系统管理")
    @RequiresRole("ADMIN")
    @DeleteMapping("/route/{id}")
    public Result<Void> routeRemove(@PathVariable Long id) {
        aiConfigService.routeRemove(id);
        return Result.success();
    }

    // ==================== 运行时下发（Python 拉取） ====================

    @Operation(summary = "运行时配置（内部接口，X-Internal-Token 鉴权）")
    @GetMapping("/runtime")
    public Result<AiConfigRuntimeVO> runtime(HttpServletRequest request,
                                              @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!verifyInternalToken(request, token)) {
            return Result.failed(ResultCode.UNAUTHORIZED, "内部 Token 校验失败");
        }
        return Result.success(aiConfigService.getRuntime());
    }

    /** C9 安全加固：X-Internal-Token 校验改为失败关闭（fail-closed）
     * <ul>
     *   <li>生产环境（非 dev）：未配置 config-callback-token → 一律拒绝，防止默认放行</li>
     *   <li>dev 环境：留空仅放行本机回环来源（本地联调便利），
     *       其他来源（局域网/公网）一律拒绝，防止供应商密钥明文泄漏（S1）</li>
     * </ul> */
    private boolean verifyInternalToken(HttpServletRequest request, String token) {
        String expected = properties.getConfigCallbackToken();
        if (expected == null || expected.isEmpty()) {
            return isDevProfile() && isLoopback(request) && token == null;
        }
        return expected.equals(token);
    }

    /** 仅本机回环来源（127.0.0.1 / ::1），反代/远程来源一律不视为本机 */
    private static boolean isLoopback(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return addr != null && ("127.0.0.1".equals(addr) || "::1".equals(addr)
                || "0:0:0:0:0:0:0:1".equals(addr));
    }

    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
