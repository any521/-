package com.dianfei.ai.config;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.ai.config.dto.AiProviderDTO;
import com.dianfei.ai.config.dto.AiRouteDTO;
import com.dianfei.ai.config.entity.AiProvider;
import com.dianfei.ai.config.entity.AiRoute;
import com.dianfei.ai.config.mapper.AiProviderMapper;
import com.dianfei.ai.config.mapper.AiRouteMapper;
import com.dianfei.ai.config.vo.AiConfigRuntimeVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.TombstoneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 模型配置管理实现
 * <p>密钥：写入时 AES 加密入库；列表/详情不回显密钥；runtime 下发时解密
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private static final String PROTOCOL_OPENAI = "openai";
    private static final String PROTOCOL_ANTHROPIC = "anthropic";
    private static final String PROTOCOL_RESPONSES = "responses";
    private static final String DEFAULT_PATH_OPENAI = "/chat/completions";
    private static final String DEFAULT_PATH_ANTHROPIC = "/v1/messages";
    private static final String DEFAULT_PATH_RESPONSES = "/responses";

    private final AiProviderMapper providerMapper;
    private final AiRouteMapper routeMapper;
    private final AiServiceProperties properties;

    // ==================== 供应商 ====================

    @Override
    public PageVO<AiProvider> providerPage(PageDTO page, String keyword) {
        Page<AiProvider> result = providerMapper.selectPage(
                new Page<>(page.getPageNum(), page.getPageSize()),
                new LambdaQueryWrapper<AiProvider>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(AiProvider::getName, keyword)
                                .or().like(AiProvider::getVendor, keyword)
                                .or().like(AiProvider::getRemark, keyword))
                        .orderByAsc(AiProvider::getSort)
                        .orderByAsc(AiProvider::getId));
        // 脱敏：列表不回显密钥
        result.getRecords().forEach(p -> p.setApiKey(null));
        return PageVO.of(result);
    }

    @Override
    public void providerAdd(AiProviderDTO dto) {
        checkProviderNameUnique(dto.getName(), null);
        AiProvider entity = BeanUtil.copyProperties(dto, AiProvider.class);
        entity.setId(null);
        // 明文密钥加密入库
        if (StrUtil.isNotBlank(dto.getApiKey())) {
            entity.setApiKey(AesUtil.encrypt(dto.getApiKey(), properties.getConfigAesKey()));
        }
        providerMapper.insert(entity);
    }

    @Override
    public void providerEdit(AiProviderDTO dto) {
        AiProvider old = dto.getId() == null ? null : providerMapper.selectById(dto.getId());
        if (old == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "供应商不存在");
        }
        checkProviderNameUnique(dto.getName(), dto.getId());
        AiProvider entity = BeanUtil.copyProperties(dto, AiProvider.class);
        // apiKey 为空 → 保留原密钥；非空 → 加密更新
        if (StrUtil.isBlank(dto.getApiKey())) {
            entity.setApiKey(old.getApiKey());
        } else {
            entity.setApiKey(AesUtil.encrypt(dto.getApiKey(), properties.getConfigAesKey()));
        }
        providerMapper.updateById(entity);
    }

    @Override
    public void providerRemove(Long id) {
        AiProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            return;
        }
        String name = provider.getName();
        // 墓碑改写唯一键后逻辑删除，释放 uk_provider_name 供同名重建
        provider.setName(TombstoneUtil.mark(name, provider.getId(), 64));
        providerMapper.updateById(provider);
        providerMapper.deleteById(id);
    }

    @Override
    public String providerTest(Long id) {
        AiProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "供应商不存在");
        }
        // 解析运行时密钥（env 优先）
        String resolvedKey = resolveApiKey(provider);
        if (StrUtil.isBlank(resolvedKey) && !isInternalProvider(provider)) {
            return "密钥未配置（api_key 与 api_key_env 均为空），无法测试连通性";
        }
        // 轻量校验：配置完整性（真实 HTTP 探测留给运维侧，避免管理端长耗时阻塞）
        String requestPath = resolveRequestPath(provider);
        return String.format("配置校验通过：%s%s | 协议=%s | 模型=%s | 密钥来源=%s",
                provider.getBaseUrl(), requestPath, provider.getProtocol(),
                provider.getModels() == null ? "[]" : provider.getModels(),
                StrUtil.isNotBlank(provider.getApiKeyEnv()) ? "环境变量 " + provider.getApiKeyEnv() : "加密入库");
    }

    // ==================== 路由 ====================

    @Override
    public PageVO<AiRoute> routePage(PageDTO page, String keyword) {
        Page<AiRoute> result = routeMapper.selectPage(
                new Page<>(page.getPageNum(), page.getPageSize()),
                new LambdaQueryWrapper<AiRoute>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(AiRoute::getCapability, keyword)
                                .or().like(AiRoute::getRemark, keyword))
                        .orderByAsc(AiRoute::getId));
        return PageVO.of(result);
    }

    @Override
    public void routeAdd(AiRouteDTO dto) {
        checkCapabilityUnique(dto.getCapability(), null);
        AiRoute entity = BeanUtil.copyProperties(dto, AiRoute.class);
        entity.setId(null);
        routeMapper.insert(entity);
    }

    @Override
    public void routeEdit(AiRouteDTO dto) {
        if (dto.getId() == null || routeMapper.selectById(dto.getId()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "路由不存在");
        }
        checkCapabilityUnique(dto.getCapability(), dto.getId());
        routeMapper.updateById(BeanUtil.copyProperties(dto, AiRoute.class));
    }

    @Override
    public void routeRemove(Long id) {
        AiRoute route = routeMapper.selectById(id);
        if (route == null) {
            return;
        }
        String capability = route.getCapability();
        route.setCapability(TombstoneUtil.mark(capability, route.getId(), 64));
        routeMapper.updateById(route);
        routeMapper.deleteById(id);
    }

    // ==================== 运行时下发 ====================

    @Override
    public AiConfigRuntimeVO getRuntime() {
        AiConfigRuntimeVO vo = new AiConfigRuntimeVO();

        List<AiProvider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getEnabled, 1)
                        .orderByAsc(AiProvider::getSort)
                        .orderByAsc(AiProvider::getId));
        vo.setProviders(providers.stream().map(this::toRuntimeProvider).collect(Collectors.toList()));

        List<AiRoute> routes = routeMapper.selectList(
                new LambdaQueryWrapper<AiRoute>()
                        .eq(AiRoute::getEnabled, 1)
                        .orderByAsc(AiRoute::getId));
        vo.setRoutes(routes);

        return vo;
    }

    /** entity → runtime provider 视图：解密密钥 + 推导请求路径 */
    private AiConfigRuntimeVO.Provider toRuntimeProvider(AiProvider p) {
        AiConfigRuntimeVO.Provider vo = new AiConfigRuntimeVO.Provider();
        vo.setName(p.getName());
        vo.setVendor(p.getVendor());
        vo.setProtocol(p.getProtocol());
        vo.setBaseUrl(p.getBaseUrl());
        vo.setRequestPath(resolveRequestPath(p));
        vo.setApiKey(resolveApiKey(p));
        vo.setModels(p.getModels());
        vo.setCapabilities(p.getCapabilities());
        vo.setExtraHeaders(p.getExtraHeaders());
        vo.setMethod(p.getMethod());
        vo.setBodyTemplate(p.getBodyTemplate());
        vo.setResponseTextPath(p.getResponseTextPath());
        vo.setTimeoutSeconds(p.getTimeoutSeconds());
        vo.setMaxTokens(p.getMaxTokens());
        vo.setWebSearchProvider(p.getWebSearchProvider());
        vo.setWebSearchEnabled(p.getWebSearchEnabled());
        return vo;
    }

    /** 密钥解析：api_key_env 优先（从环境变量读取），否则解密 api_key 列 */
    private String resolveApiKey(AiProvider p) {
        if (StrUtil.isNotBlank(p.getApiKeyEnv())) {
            String envValue = System.getenv(p.getApiKeyEnv());
            if (StrUtil.isNotBlank(envValue)) {
                return envValue;
            }
            log.warn("供应商 {} 配置了 api_key_env={} 但环境变量未设置，回退 api_key 列", p.getName(), p.getApiKeyEnv());
        }
        return AesUtil.decrypt(p.getApiKey(), properties.getConfigAesKey());
    }

    /** 请求路径推导：自定义优先，否则按协议默认 */
    private String resolveRequestPath(AiProvider p) {
        if (StrUtil.isNotBlank(p.getRequestPath())) {
            return p.getRequestPath();
        }
        if (PROTOCOL_ANTHROPIC.equalsIgnoreCase(p.getProtocol())) {
            return DEFAULT_PATH_ANTHROPIC;
        }
        if (PROTOCOL_RESPONSES.equalsIgnoreCase(p.getProtocol())) {
            return DEFAULT_PATH_RESPONSES;
        }
        // openai / custom 默认走 OpenAI 兼容路径
        return DEFAULT_PATH_OPENAI;
    }

    private boolean isInternalProvider(AiProvider p) {
        return "internal".equalsIgnoreCase(p.getVendor());
    }

    private void checkProviderNameUnique(String name, Long excludeId) {
        Long count = providerMapper.selectCount(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getName, name)
                .ne(excludeId != null, AiProvider::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "供应商名称已存在");
        }
    }

    private void checkCapabilityUnique(String capability, Long excludeId) {
        Long count = routeMapper.selectCount(new LambdaQueryWrapper<AiRoute>()
                .eq(AiRoute::getCapability, capability)
                .ne(excludeId != null, AiRoute::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "能力标识已存在");
        }
    }
}
