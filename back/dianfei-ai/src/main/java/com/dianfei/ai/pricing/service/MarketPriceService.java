package com.dianfei.ai.pricing.service;

import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.pricing.vo.ItemPriceVO;
import com.dianfei.ai.pricing.vo.PriceSyncVO;
import com.dianfei.business.map.service.AmapProxyService;
import com.dianfei.business.map.vo.GeocodeVO;
import com.dianfei.business.recycle.dto.RecyclePriceSaveDTO;
import com.dianfei.business.recycle.entity.RecyclePrice;
import com.dianfei.business.recycle.service.RecyclePriceService;
import com.dianfei.common.utils.RedisUtils;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 回收报价联网行情（B3 实时报价）：每日定时 + 用户手动按钮共用一套同步逻辑。
 * 调用 AI 旁路服务 /v1/market-price（博查/Tavily 联网搜索当地行情 + LLM 提炼），
 * 命中后 upsert biz_recycle_price；自定义物品询价不落库。
 *
 * <p>护栏（sys_config 热配置，管理端「参数配置」页）：
 * <ul>
 *   <li>recycle.price.sync.enabled：1 开启（默认） / 0 关闭</li>
 *   <li>recycle.price.city：查询城市，默认「北京」（IP 定位失败时兜底）</li>
 *   <li>recycle.price.sync.max-change：单日相对旧价最大涨跌幅，默认 0.6（60%）</li>
 *   <li>recycle.price.sync.max-price：绝对上限（元/kg），默认 200</li>
 *   <li>recycle.price.sync.apply-degraded：1 应用降级结果（正则/mock，默认）/ 0 只应用 LLM 提炼价</li>
 * </ul>
 * 同步前旧价快照入 Redis（TTL 3 天），/api/recycle/price 回填 prevPricePerKg 供前端展示
 * 「较昨日涨跌 %」；单品类失败不影响其余品类（D15）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceService {

    /** 前价快照键（独立于 F5 定价回滚快照，互不覆盖） */
    public static final String PREV_PRICE_KEY = "dianfei:price:market-prev:%s";
    public static final String SYNC_TIME_KEY = "dianfei:price:sync-time";
    /** 定位城市缓存键（AMap IP 定位结果，7 天有效） */
    public static final String CITY_KEY = "dianfei:price:city";
    /** 同步互斥锁（手动/定时共用，防止并发挤爆 AI 旁路） */
    private static final String SYNC_LOCK_KEY = "dianfei:price:sync-lock";
    private static final Duration PREV_PRICE_TTL = Duration.ofDays(3);
    private static final Duration CITY_TTL = Duration.ofDays(7);
    private static final Duration SYNC_LOCK_TTL = Duration.ofMinutes(10);
    /** 后台同步线程（单线程串行 + daemon，容器退出不阻塞） */
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "market-price-sync");
        t.setDaemon(true);
        return t;
    });

    private final AiServiceClient aiServiceClient;
    private final RecyclePriceService recyclePriceService;
    private final ConfigService configService;
    private final RedisUtils redisUtils;
    private final AmapProxyService amapProxyService;

    /** 标准内置品类（与前端 baseData 一致）：联网更新始终遍历全部 8 个，缺库自动补 */
    private static final List<String> STANDARD_CATEGORIES = List.of(
            "废纸类", "塑料类", "金属类", "织物类", "家电类", "电子产品", "玻璃类", "厨余垃圾");

    /** 英文业务核算编码（V1 字典/订单核算依赖）→ 前端中文展示品类：同步以中文行为主，双写英文行避免两行分叉 */
    private static final Map<String, String> BIZ_CODE_TO_CN = Map.of(
            "PAPER", "废纸类", "PLASTIC", "塑料类", "METAL", "金属类", "FABRIC", "织物类", "E_WASTE", "电子产品");
    private static final Map<String, String> CN_TO_BIZ_CODE = Map.of(
            "废纸类", "PAPER", "塑料类", "PLASTIC", "金属类", "METAL", "织物类", "FABRIC", "电子产品", "E_WASTE");

    /** 不可回收品类：不联网搜索，直接标 0（如厨余垃圾） */
    private static final Set<String> UNRECYCLABLE_CATEGORIES = Set.of("厨余垃圾");

    /** 同步开关（sys_config recycle.price.sync.enabled，默认开） */
    public boolean syncEnabled() {
        return "1".equals(configService.getValue("recycle.price.sync.enabled", "1"));
    }

    /** 手动按钮入口（指定城市）：内置 8 品类全量联网同步；空/未传=IP 定位默认城市 */
    public PriceSyncVO syncAll(String city) {
        if (city == null || city.isBlank()) {
            city = resolveCity();
        } else {
            // 手动选城同步后作为默认城市，报价元信息/卡片随之展示
            redisUtils.set(CITY_KEY, city, CITY_TTL);
        }
        double maxChange = parseDouble(configService.getValue("recycle.price.sync.max-change", "0.6"), 0.6);
        double maxPrice = parseDouble(configService.getValue("recycle.price.sync.max-price", "200"), 200);
        boolean applyDegraded = "1".equals(configService.getValue("recycle.price.sync.apply-degraded", "1"));

        // 内置 8 品类 + 后台额外生效品类（去重保序），保证「联网更新」覆盖全部内置
        LinkedHashSet<String> categories = new LinkedHashSet<>(STANDARD_CATEGORIES);
        for (RecyclePrice p : recyclePriceService.listActive()) {
            categories.add(p.getCategory());
        }
        int updated = 0;
        int skipped = 0;
        for (String category : categories) {
            // 英文核算行（PAPER/E_WASTE 等）由对应中文展示行落库时双写维护，不单独联网搜索，避免两行价格分叉
            if (BIZ_CODE_TO_CN.containsKey(category)) {
                continue;
            }
            RecyclePrice oldPrice = recyclePriceService.getByCategory(category);
            try {
                BigDecimal newPrice;
                String note;
                if (UNRECYCLABLE_CATEGORIES.contains(category)) {
                    if (oldPrice != null && oldPrice.getPricePerKg() != null
                            && oldPrice.getPricePerKg().signum() == 0) {
                        // 已是 0：不重复落库（避免每次同步都 0→0 假更新）
                        log.warn("[行情] {} 不可回收品类已标 0，跳过", category);
                        skipped++;
                        continue;
                    }
                    // 不可回收：不联网，直接标 0
                    newPrice = BigDecimal.ZERO;
                    note = "不可回收品类";
                } else {
                    AiContracts.MarketPriceResponse resp = aiServiceClient.marketPrice(
                            AiContracts.MarketPriceRequest.builder().category(category).city(city).build());
                    if (resp == null || resp.getPricePerKg() == null) {
                        if (oldPrice != null && oldPrice.getPricePerKg() != null
                                && oldPrice.getPricePerKg().signum() > 0) {
                            // 已有正价：保留旧价不落 0，避免「联网查询到数据但页面全部显示 0」
                            log.warn("[行情] {} 搜索无结果，保留旧价 {}/kg", category, oldPrice.getPricePerKg());
                            skipped++;
                            continue;
                        }
                        // 无旧价且无结果：不落库不计数（前端内置价兜底展示；避免 0→0 假更新）
                        log.warn("[行情] {} 搜索无结果且无旧价，跳过（前端内置价兜底）", category);
                        skipped++;
                        continue;
                    } else {
                        if (Boolean.TRUE.equals(resp.getDegraded()) && !applyDegraded) {
                            log.warn("[行情] {} 降级结果被配置丢弃（degraded={}）", category, resp.getNote());
                            skipped++;
                            continue;
                        }
                        newPrice = clamp(resp.getPricePerKg(), maxPrice);
                        note = resp.getNote();
                    }
                }
                BigDecimal old = oldPrice != null ? oldPrice.getPricePerKg() : null;
                // 强制标 0 不受涨跌幅护栏限制；其余超护栏跳过
                if (newPrice.signum() != 0 && exceedsMaxChange(old, newPrice, maxChange)) {
                    log.warn("[行情] {} 涨跌幅超护栏 {}/kg -> {}/kg（limit {}），跳过",
                            category, old, newPrice, maxChange);
                    skipped++;
                    continue;
                }
                if (old != null && newPrice.compareTo(old) != 0) {
                    // 更新前快照旧价，供当日「较昨日」涨跌展示
                    redisUtils.set(PREV_PRICE_KEY.formatted(category), old.toPlainString(), PREV_PRICE_TTL);
                }
                RecyclePriceSaveDTO dto = new RecyclePriceSaveDTO();
                dto.setCategory(category);
                dto.setPricePerKg(newPrice);
                dto.setStatus(1);
                // 落库时记录本次查询地区：失败分支（保留旧价/护栏跳过）不落库 → city/update_time 保留上次
                dto.setCity(city);
                recyclePriceService.saveByCategory(dto);
                // 双写英文核算行（订单/积分核算依赖英文编码），保持两行同价避免再次分叉
                String bizCode = CN_TO_BIZ_CODE.get(category);
                if (bizCode != null) {
                    RecyclePriceSaveDTO bizDto = new RecyclePriceSaveDTO();
                    bizDto.setCategory(bizCode);
                    bizDto.setPricePerKg(newPrice);
                    bizDto.setStatus(1);
                    bizDto.setCity(city);
                    recyclePriceService.saveByCategory(bizDto);
                }
                log.info("[行情] {} 报价 {} -> {}/kg（{}{}）",
                        category, old, newPrice, city, note);
                updated++;
            } catch (AiServiceException e) {
                log.warn("[行情] {} 同步失败（AI 不可用）: {}", category, e.getMessage());
                skipped++;
            } catch (Exception e) {
                log.warn("[行情] {} 同步异常: {}", category, e.getMessage());
                skipped++;
            }
        }
        redisUtils.set(SYNC_TIME_KEY, LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), Duration.ofDays(3));
        log.info("[行情] 报价同步完成 city={} 成功 {} 跳过 {}", city, updated, skipped);
        return PriceSyncVO.builder().city(city).updated(updated).skipped(skipped).started(true).build();
    }

    /** 手动按钮入口（异步提交）：Redis 锁防并发（与每日定时互斥），立即返回提交结果，
     * 后台线程执行全量联网同步（8+ 品类 LLM 搜索可达数分钟）；前端轮询 /recycle/price/meta
     * 的 lastSyncTime 变化感知完成，避免长连接被网关/代理超时断开误报网络错误。 */
    public PriceSyncVO syncAsync(String city) {
        if (!redisUtils.setIfAbsent(SYNC_LOCK_KEY, "1", SYNC_LOCK_TTL)) {
            log.warn("[行情] 已有报价同步进行中，本次手动请求未提交");
            return PriceSyncVO.builder().started(false).build();
        }
        syncExecutor.execute(() -> {
            try {
                syncAll(city);
            } catch (Exception e) {
                log.error("[行情] 后台报价同步异常", e);
            } finally {
                redisUtils.delete(SYNC_LOCK_KEY);
            }
        });
        String resolved = city != null && !city.isBlank() ? city : resolveCity();
        return PriceSyncVO.builder().started(true).city(resolved).build();
    }

    /** 定时任务入口：城市走 IP 定位默认 */
    public PriceSyncVO syncAll() {
        return syncAll(null);
    }

    /** 定位按钮：返回当前 IP 定位城市（失败回退配置默认；结果缓存 7 天） */
    public String locate() {
        return resolveCity();
    }

    /** 自定义物品联网询价（指定城市；不落库、不缓存，每次都是实时联网搜索；无结果返回 pricePerKg=null） */
    public ItemPriceVO query(String item, String city) {
        if (city == null || city.isBlank()) {
            city = resolveCity();
        }
        double maxPrice = parseDouble(configService.getValue("recycle.price.sync.max-price", "200"), 200);
        try {
            AiContracts.MarketPriceResponse resp = aiServiceClient.marketPrice(
                    AiContracts.MarketPriceRequest.builder().category(item).city(city).build());
            BigDecimal price = resp != null && resp.getPricePerKg() != null
                    ? clamp(resp.getPricePerKg(), maxPrice) : null;
            return ItemPriceVO.builder()
                    .item(item)
                    .city(city)
                    .pricePerKg(price)
                    .unit(resp != null ? resp.getUnit() : null)
                    .note(resp != null ? resp.getNote() : null)
                    .source(resp != null ? resp.getSource() : null)
                    .degraded(resp != null ? resp.getDegraded() : null)
                    .cached(false)
                    .build();
        } catch (AiServiceException e) {
            log.warn("[行情] 自定义询价失败 item={}（AI 不可用）: {}", item, e.getMessage());
            return ItemPriceVO.builder().item(item).city(city).pricePerKg(null)
                    .unit("元/公斤").note("AI 行情服务暂不可用，请稍后重试").degraded(true).cached(false).build();
        }
    }

    /**
     * 先定位再搜索：AMap IP 定位获取服务器所在城市（缓存 7 天），失败/未配置高德
     * 回退 sys_config recycle.price.city（默认北京）。直辖市等高德 city 为 [] 的场景取省名。
     */
    public String resolveCity() {
        String cached = redisUtils.get(CITY_KEY);
        if (cached != null) {
            return cached;
        }
        String city = null;
        try {
            GeocodeVO loc = amapProxyService.ipLocation();
            city = normalizeCity(loc != null ? loc.getCity() : null,
                    loc != null ? loc.getProvince() : null);
            log.info("[行情] IP 定位成功 city={}", city);
        } catch (Exception e) {
            log.warn("[行情] IP 定位失败，回退配置城市: {}", e.getMessage());
        }
        if (city == null) {
            city = configService.getValue("recycle.price.city", "北京");
        }
        redisUtils.set(CITY_KEY, city, CITY_TTL);
        return city;
    }

    /** 高德直辖市 city 为 "[]" 或空：回退省名；统一去掉尾部「市」便于搜索词拼接 */
    private static String normalizeCity(String city, String province) {
        String value = (city == null || city.isBlank() || "[]".equals(city.trim())) ? province : city;
        if (value == null || value.isBlank() || "[]".equals(value.trim())) {
            return null;
        }
        return value.trim().replaceAll("[市省]$", "");
    }

    private static BigDecimal clamp(double price, double maxPrice) {
        double v = Math.min(Math.max(price, 0.01), maxPrice);
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean exceedsMaxChange(BigDecimal oldPrice, BigDecimal newPrice, double maxChange) {
        if (oldPrice == null || oldPrice.signum() <= 0) {
            return false;
        }
        double ratio = Math.abs(newPrice.subtract(oldPrice).doubleValue() / oldPrice.doubleValue());
        return ratio > maxChange;
    }

    private static double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
