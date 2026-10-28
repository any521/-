package com.dianfei.ai.config;

import com.dianfei.ai.config.dto.AiProviderDTO;
import com.dianfei.ai.config.dto.AiRouteDTO;
import com.dianfei.ai.config.entity.AiProvider;
import com.dianfei.ai.config.entity.AiRoute;
import com.dianfei.ai.config.vo.AiConfigRuntimeVO;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;

/**
 * AI 模型配置管理（管理端 CRUD + 运行时配置下发）
 */
public interface AiConfigService {

    // ---------------- 供应商 ----------------
    PageVO<AiProvider> providerPage(PageDTO page, String keyword);

    void providerAdd(AiProviderDTO dto);

    void providerEdit(AiProviderDTO dto);

    void providerRemove(Long id);

    /** 连通性测试：用该供应商配置发一次最小请求 */
    String providerTest(Long id);

    // ---------------- 路由 ----------------
    PageVO<AiRoute> routePage(PageDTO page, String keyword);

    void routeAdd(AiRouteDTO dto);

    void routeEdit(AiRouteDTO dto);

    void routeRemove(Long id);

    // ---------------- 运行时下发（Python 拉取） ----------------
    AiConfigRuntimeVO getRuntime();
}
