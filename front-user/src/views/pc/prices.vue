<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast, showSuccessToast } from 'vant'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import {
  getPrices, getPriceMeta, syncPrices, queryItemPrice, locateCity,
  type CategoryPrice, type PriceMeta, type ItemPriceQuery,
} from '@/api/business'
import { regeo } from '@/api/map'
import { wgs84ToGcj02 } from '@/utils/coord'
import { ensureAmap, listDistricts, getLocation, type DistrictNode } from '@/composables/useAmap'

/** 最近一次询价结果浏览器缓存键（localStorage：仅存最新一次，页面加载直接展示） */
const LAST_QUERY_STORAGE_KEY = 'dianfei:prices:last-query'

/** 高德区划不可用时的兜底常用城市（滑动窗口选择） */
const FALLBACK_CITIES = [
  '北京', '上海', '广州', '深圳', '天津', '重庆', '杭州', '南京',
  '武汉', '成都', '西安', '苏州', '长沙', '郑州', '青岛', '大连',
  '沈阳', '厦门', '福州', '昆明', '合肥', '南昌', '石家庄', '太原',
]

const REGION_STORAGE_KEY = 'dianfei:prices:region'

/** 选中地区：省/市/区县 三级（AMap 行政区划数据源；高德不可用降级单城市选择） */
interface Region {
  province: string
  city: string
  district: string
}

function loadRegion(): Region {
  try {
    const raw = localStorage.getItem(REGION_STORAGE_KEY)
    if (raw) {
      const r = JSON.parse(raw) as Partial<Region>
      if (typeof r?.province === 'string' || typeof r?.city === 'string') {
        return { province: r.province ?? '', city: r.city ?? '', district: r.district ?? '' }
      }
    }
  } catch {
    /* 损坏的缓存忽略 */
  }
  return { province: '', city: '', district: '' }
}

function saveRegion() {
  localStorage.setItem(REGION_STORAGE_KEY, JSON.stringify(region.value))
}

/** 搜索城市（联网/同步入参）：区县已选拼「市+区县」提升本地精度，直辖市直接用省名 */
const searchCity = computed(() => {
  const r = region.value
  if (r.district && r.city) return `${r.city}${r.district}`
  if (r.city) return r.city
  if (r.province) return r.province
  return ''
})

/** 展示用地区全称：省 · 市 · 区县 */
const displayRegion = computed(() =>
  [region.value.province, region.value.city, region.value.district].filter(Boolean).join(' · '),
)

const loading = ref(true)
const syncing = ref(false)
const querying = ref(false)
const locating = ref(false)
const prices = ref<CategoryPrice[]>([])
const priceMeta = ref<PriceMeta>({})
const queryItem = ref('')
const queryResult = ref<ItemPriceQuery | null>(null)
const region = ref<Region>(loadRegion())
/** 级联数据源：高德可用 → 省市区三级；不可用 → 单城市兜底 */
const districtsReady = ref(false)
const provinces = ref<DistrictNode[]>([])
const cities = ref<DistrictNode[]>([])
const districts = ref<DistrictNode[]>([])

/** 地区下拉菜单：当前展开项（省/市/区县/兜底城市），空串关闭 */
type MenuKey = '' | 'province' | 'city' | 'district' | 'fallback'
const openMenu = ref<MenuKey>('')
function toggleMenu(key: Exclude<MenuKey, ''>) {
  openMenu.value = openMenu.value === key ? '' : key
}

/** 全部可回收品类（中文）：接口报价优先，未配置回退内置默认价（标注「内置」），保证直接展示；命中但价格为 0（联网搜索无结果落库标 0）时同样回退内置价，避免卡片全部显示 0 */
const displayList = computed(() =>
  RECYCLE_CATEGORIES.map((cat) => {
    const hit = prices.value.find((p) => p.code === cat.code || p.name === cat.name)
    return {
      code: cat.code,
      name: cat.name,
      icon: cat.icon,
      price: hit && hit.price > 0 ? hit.price : cat.price,
      builtin: !hit || !(hit.price > 0),
      trend: hit?.trend ?? 0,
      updateTime: hit?.updateTime ?? priceMeta.value.lastSyncTime,
      // 每类展示自己落库时的地区；查询失败不落库 → 仍显示上次成功地区
      city: hit?.city || searchCity.value || priceMeta.value.city || '北京',
    }
  }),
)

/** 浏览器定位（HTML5 Geolocation，真定位；失败 reject 由调用方兜底） */
function locateMe(): Promise<[number, number]> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) return reject(new Error('浏览器不支持定位'))
    navigator.geolocation.getCurrentPosition(
      // 浏览器返回 WGS-84，后端逆地理（高德）需 GCJ-02：定位成功即转换，避免偏差数百米导致区划误判
      (p) => resolve(wgs84ToGcj02(p.coords.longitude, p.coords.latitude)),
      () => reject(new Error('获取定位失败')),
      { timeout: 8000, maximumAge: 600000 },
    )
  })
}

/** 匹配后端城市名（如 北京/深圳，无后缀）与高德区划名（如 北京市/深圳市） */
function sameDistrict(a: string, b: string) {
  return a.replace(/[省市]$/, '') === b.replace(/[省市]$/, '')
}

/** 定位结果落到三级区划并联动展开：高德列表就绪时按省→市→区逐级匹配 */
async function applyLocatedRegion(prov: string, city: string, district: string) {
  region.value = { province: prov, city, district }
  saveRegion()
  if (!districtsReady.value || !provinces.value.length) return
  const p = provinces.value.find((x) => x.name === prov)
  if (!p) return
  const clist = await listDistricts(p.name, 'city')
  cities.value = clist
  // 直辖市：唯一市，直接用省名展开区县
  if (clist.length === 1) {
    const only = clist[0]
    if (!only) return
    region.value.city = only.name
    const dlist = await listDistricts(only.name, 'district')
    districts.value = dlist
    if (district && dlist.some((d) => d.name === district)) {
      region.value.district = district
    } else {
      region.value.district = ''
    }
    saveRegion()
    return
  }
  const c = clist.find((x) => sameDistrict(x.name, city))
  if (c) {
    region.value.city = c.name
    const dlist = await listDistricts(c.name, 'district')
    districts.value = dlist
    if (district && dlist.some((d) => d.name === district)) {
      region.value.district = district
    } else {
      region.value.district = ''
    }
    saveRegion()
  } else {
    region.value.city = ''
    districts.value = []
    saveRegion()
  }
}

/** 真定位：AMap.Geolocation（JS 渠道，浏览器定位+逆地理，一次拿省市区）；
 *  不可用 → 浏览器原生定位 + 后端 regeo 降级；再失败 → 服务端 IP 定位（仅城市） */
async function locateBest(): Promise<string> {
  await ensureAmap()
  const loc = await getLocation().catch(() => null)
  if (loc?.lng && loc.lat && (loc.province || loc.city)) {
    await applyLocatedRegion(loc.province || '', loc.city || loc.province || '', loc.district || '')
    return searchCity.value || loc.city || loc.province || ''
  }
  try {
    const [lng, lat] = await locateMe()
    const geo = await regeo(lng, lat)
    const prov = (geo?.province ?? '').trim()
    const city = (geo?.city ?? '').trim() || prov
    const district = (geo?.district ?? '').trim()
    if (prov || city) {
      await applyLocatedRegion(prov, city, district)
      return searchCity.value || city || prov
    }
  } catch {
    /* 浏览器定位失败 → IP 兜底 */
  }
  const city = await locateCity().catch(() => '')
  if (city) {
    region.value = { province: '', city, district: '' }
    saveRegion()
  }
  return city
}

async function loadPrices() {
  const [p, m] = await Promise.all([getPrices(), getPriceMeta()])
  prices.value = p
  priceMeta.value = m ?? {}
}

/** 初始化级联数据源：高德不可用 → 单城市兜底（FALLBACK_CITIES） */
async function initDistricts() {
  const ok = await ensureAmap()
  if (ok) {
    const plist = await listDistricts('中国', 'province')
    if (plist.length) {
      provinces.value = plist
      districtsReady.value = true
      // 恢复持久化地区：省 → 市 → 区县 逐级展开
      const r = region.value
      if (r.province) {
        const p = plist.find((x) => x.name === r.province)
        if (p) {
          const clist = await listDistricts(p.name, 'city')
          cities.value = clist
          if (clist.length === 1) {
            const only = clist[0]
            if (only) await selectCity(only.name, true)
          } else if (r.city) {
            const c = clist.find((x) => sameDistrict(x.name, r.city))
            if (c) await selectCity(c.name, true)
          }
        }
      }
      return
    }
  }
  // 高德未配置/失败：单城市兜底
  if (!region.value.city && priceMeta.value.city) {
    region.value.city = priceMeta.value.city
    saveRegion()
  }
}

/** 读取最近一次询价结果（浏览器缓存，无缓存返回 null） */
function loadLastQuery(): ItemPriceQuery | null {
  try {
    const raw = localStorage.getItem(LAST_QUERY_STORAGE_KEY)
    if (!raw) return null
    const q = JSON.parse(raw) as ItemPriceQuery
    if (typeof q?.item !== 'string') return null
    q.cached = true
    return q
  } catch {
    return null
  }
}

/** 写入最近一次询价结果（仅浏览器缓存，无服务端缓存） */
function saveLastQuery(q: ItemPriceQuery) {
  try {
    localStorage.setItem(LAST_QUERY_STORAGE_KEY, JSON.stringify(q))
  } catch {
    /* 存储失败忽略（隐私模式等），不影响本次展示 */
  }
}

async function load() {
  try {
    const [p, m] = await Promise.all([getPrices(), getPriceMeta()])
    prices.value = p
    priceMeta.value = m ?? {}
    if (!region.value.province && !region.value.city) {
      const city = m?.city
      if (city) {
        region.value = { province: '', city, district: '' }
        saveRegion()
      }
    }
    const last = loadLastQuery()
    if (last?.pricePerKg != null || last?.note) {
      queryResult.value = last
    }
    initDistricts()
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 三级级联：选省 → 加载市；直辖市仅一个市自动选中继续展开区县 */
async function selectProvince(name: string) {
  openMenu.value = ''
  region.value = { province: name, city: '', district: '' }
  saveRegion()
  districts.value = []
  const clist = await listDistricts(name, 'city')
  cities.value = clist
  if (clist.length === 1) {
    const only = clist[0]
    if (only) await selectCity(only.name)
  }
}

/** 选市 → 加载区县 */
async function selectCity(name: string, silent = false) {
  openMenu.value = ''
  const prevDistrict = silent ? region.value.district : ''
  region.value = { province: region.value.province, city: name, district: prevDistrict }
  saveRegion()
  const dlist = await listDistricts(name, 'district')
  districts.value = dlist
  if (!silent) {
    region.value.district = ''
    saveRegion()
  } else if (prevDistrict && !dlist.some((d) => d.name === prevDistrict)) {
    region.value.district = ''
    saveRegion()
  }
}

/** 选区县 */
function selectDistrict(name: string) {
  openMenu.value = ''
  region.value = { ...region.value, district: name }
  saveRegion()
}

/** 兜底单城市选择（高德区划不可用） */
function selectFallbackCity(name: string) {
  openMenu.value = ''
  region.value = { province: '', city: name, district: '' }
  saveRegion()
}

/** 定位按钮：真定位（浏览器经纬度 + 逆地理） */
async function onLocate() {
  if (locating.value) return
  locating.value = true
  try {
    const city = await locateBest()
    if (city) {
      showSuccessToast(`已定位到 ${displayRegion.value || city}`)
    } else {
      showToast('定位失败，请检查网络或浏览器定位权限')
    }
  } catch {
    showToast('定位失败，请检查网络后重试')
  } finally {
    locating.value = false
  }
}

/** 手动联网更新（异步提交）：立即返回，后台全量同步（8+ 品类 LLM 搜索可达数分钟），
 * 轮询报价元信息 lastSyncTime 变化感知完成；避免长连接被网关超时断开误报网络错误。 */
async function onSync() {
  if (syncing.value) return
  syncing.value = true
  try {
    const city = searchCity.value || (await locateBest().catch(() => ''))
    const r = await syncPrices(city || undefined)
    if (r.started === false) {
      showToast('已有同步进行中，请稍后查看')
      return
    }
    showSuccessToast(`已开始联网更新${r.city ? `（${r.city}）` : ''}，完成后自动刷新`)
    await waitSyncDone()
  } catch {
    showToast('联网更新提交失败，请稍后重试')
  } finally {
    syncing.value = false
  }
}

/** 轮询报价元信息：lastSyncTime 变化即同步完成（最多 5 分钟兜底） */
async function waitSyncDone() {
  const before = (await getPriceMeta().catch(() => null))?.lastSyncTime ?? ''
  for (let i = 0; i < 60; i++) {
    await new Promise((r) => setTimeout(r, 5000))
    const meta = await getPriceMeta().catch(() => null)
    if (meta?.lastSyncTime && meta.lastSyncTime !== before) {
      await loadPrices()
      return
    }
  }
  await loadPrices()
}

/** 自定义物品联网询价（按所选地区；实时搜索，仅将最新结果存浏览器缓存） */
async function onQuery() {
  const item = queryItem.value.trim()
  if (!item) {
    showToast('请输入要查询的物品，如 易拉罐')
    return
  }
  querying.value = true
  try {
    const q = await queryItemPrice(item, searchCity.value || undefined)
    queryResult.value = q
    if (q?.item) {
      saveLastQuery(q)
    }
  } catch {
    showToast('询价失败，请稍后重试')
  } finally {
    querying.value = false
  }
}
</script>

<template>
  <div class="pc-prices">
    <div class="container">
      <h1 class="page-title">实时报价</h1>
      <p class="page-desc">内置参考价直接展示，可联网查询当地行情或自定义询价</p>
      <p v-if="!loading" class="page-meta">
        <template v-if="prices.length">
          {{ priceMeta.city || '当地' }} · 更新于 {{ priceMeta.lastSyncTime || prices[0]?.updateTime || '—' }}
          （每天 08:05 自动定位并同步）
        </template>
        <template v-else>
          当前为内置参考价，点击「联网更新」获取{{ priceMeta.city || '北京' }}实时行情
        </template>
      </p>

      <!-- 地区选择（省/市/区县 三级下拉菜单 + 真定位） -->
      <div v-if="!loading" class="city-bar">
        <div class="city-bar__head">
          <button class="city-bar__locate" :disabled="locating" @click="onLocate">
            <SvgIcon name="common-location" :size="16" />
            {{ locating ? '定位中…' : '真定位' }}
          </button>
          <span v-if="displayRegion" class="city-bar__region">当前：{{ displayRegion }}</span>
        </div>

        <template v-if="districtsReady">
          <div class="city-bar__selects">
            <div class="city-select">
              <button class="city-select__btn" :class="{ 'is-open': openMenu === 'province' }" @click="toggleMenu('province')">
                <span class="city-select__label">省</span>
                <span class="city-select__value" :class="{ 'is-placeholder': !region.province }">{{ region.province || '请选择' }}</span>
                <SvgIcon name="common-arrow-right" :size="12" class="city-select__caret" />
              </button>
              <div v-if="openMenu === 'province'" class="city-select__menu">
                <div v-if="!provinces.length" class="city-select__empty">暂无数据</div>
                <button
                  v-for="p in provinces"
                  :key="p.adcode"
                  class="city-select__item"
                  :class="{ active: p.name === region.province }"
                  @click="selectProvince(p.name)"
                >
                  {{ p.name }}
                </button>
              </div>
            </div>

            <div class="city-select">
              <button class="city-select__btn" :class="{ 'is-open': openMenu === 'city' }" @click="toggleMenu('city')">
                <span class="city-select__label">市</span>
                <span class="city-select__value" :class="{ 'is-placeholder': !region.city }">
                  {{ region.city || (cities.length ? '请选择' : '请先选择省') }}
                </span>
                <SvgIcon name="common-arrow-right" :size="12" class="city-select__caret" />
              </button>
              <div v-if="openMenu === 'city'" class="city-select__menu">
                <div v-if="!cities.length" class="city-select__empty">暂无数据</div>
                <button
                  v-for="c in cities"
                  :key="c.adcode"
                  class="city-select__item"
                  :class="{ active: c.name === region.city }"
                  @click="selectCity(c.name)"
                >
                  {{ c.name }}
                </button>
              </div>
            </div>

            <div class="city-select">
              <button class="city-select__btn" :class="{ 'is-open': openMenu === 'district' }" @click="toggleMenu('district')">
                <span class="city-select__label">区县</span>
                <span class="city-select__value" :class="{ 'is-placeholder': !region.district }">
                  {{ region.district || (districts.length ? '请选择' : '请先选择市') }}
                </span>
                <SvgIcon name="common-arrow-right" :size="12" class="city-select__caret" />
              </button>
              <div v-if="openMenu === 'district'" class="city-select__menu">
                <div v-if="!districts.length" class="city-select__empty">暂无数据</div>
                <button
                  v-for="d in districts"
                  :key="d.adcode"
                  class="city-select__item"
                  :class="{ active: d.name === region.district }"
                  @click="selectDistrict(d.name)"
                >
                  {{ d.name }}
                </button>
              </div>
            </div>
          </div>
        </template>

        <!-- 高德区划不可用：单城市兜底下拉 -->
        <div v-else class="city-select">
          <button class="city-select__btn" :class="{ 'is-open': openMenu === 'fallback' }" @click="toggleMenu('fallback')">
            <span class="city-select__label">城市</span>
            <span class="city-select__value" :class="{ 'is-placeholder': !region.city }">{{ region.city || '请选择' }}</span>
            <SvgIcon name="common-arrow-right" :size="12" class="city-select__caret" />
          </button>
          <div v-if="openMenu === 'fallback'" class="city-select__menu city-select__menu--grid">
            <button
              v-for="city in FALLBACK_CITIES"
              :key="city"
              class="city-select__item"
              :class="{ active: city === region.city }"
              @click="selectFallbackCity(city)"
            >
              {{ city }}
            </button>
          </div>
        </div>

        <!-- 下拉遮罩：点击空白处关闭 -->
        <div v-if="openMenu" class="city-bar__mask" @click="openMenu = ''"></div>
      </div>

      <!-- 操作区：自定义询价 + 手动联网更新 -->
      <div class="toolbar">
        <div class="toolbar__query">
          <input
            v-model="queryItem"
            class="toolbar__input"
            maxlength="32"
            placeholder="自定义询价，如：易拉罐 / 纸箱 / 矿泉水瓶"
            @keyup.enter="onQuery"
          />
          <button class="toolbar__btn toolbar__btn--query" :disabled="querying" @click="onQuery">
            {{ querying ? '查询中…' : '查询报价' }}
          </button>
        </div>
        <button class="toolbar__btn toolbar__btn--sync" :disabled="syncing" @click="onSync">
          <SvgIcon name="common-refresh" :size="16" />
          {{ syncing ? '更新中…' : '联网更新' }}
        </button>
      </div>

      <!-- 自定义询价结果 -->
      <div v-if="queryResult" class="query-result" :class="{ 'query-result--empty': !queryResult.pricePerKg }">
        <template v-if="queryResult.pricePerKg">
          <span class="query-result__item">{{ queryResult.item }}</span>
          {{ queryResult.city || '当地' }}今日回收价
          <span class="query-result__price">¥{{ queryResult.pricePerKg.toFixed(2) }}</span>
          /kg
          <span v-if="queryResult.degraded" class="query-result__tag">参考价</span>
          <span v-if="queryResult.cached" class="query-result__tag">缓存</span>
          <span v-if="queryResult.source" class="query-result__source">来源：{{ queryResult.source }}</span>
          <span v-if="queryResult.note" class="query-result__note">{{ queryResult.note }}</span>
        </template>
        <template v-else>
          <span class="query-result__item">{{ queryResult.item }}</span>
          暂未查询到数据{{ queryResult.note ? `（${queryResult.note}）` : '' }}
        </template>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-else class="prices-grid">
        <div v-for="price in displayList" :key="price.code" class="price-card">
          <div class="price-card__icon">
            <SvgIcon :name="price.icon" :size="48" color="var(--df-color-primary)" />
          </div>
          <div class="price-card__name">
            {{ price.name }}
            <span v-if="price.builtin" class="price-card__tag">内置</span>
          </div>
          <div class="price-card__price">
            ¥{{ price.price.toFixed(2) }}<span>/kg</span>
          </div>
          <div
            v-if="!price.builtin"
            class="price-card__trend"
            :class="price.trend > 0 ? 'price-card__trend--up' : (price.trend < 0 ? 'price-card__trend--down' : '')"
          >
            <template v-if="price.trend !== 0">
              {{ price.trend > 0 ? '↑' : '↓' }} {{ Math.abs(price.trend) }}%
            </template>
            <template v-else>—</template>
          </div>
          <div v-else class="price-card__trend price-card__trend--builtin">内置参考价</div>
          <div v-if="!price.builtin" class="price-card__meta">
            {{ price.city }} · {{ price.updateTime || '—' }} 更新
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-prices {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 16px;
  text-align: center;
}

.page-desc {
  font-size: 18px;
  color: var(--df-text-regular);
  margin-bottom: 16px;
  text-align: center;
}

.page-meta {
  font-size: 14px;
  color: var(--df-text-secondary);
  text-align: center;
  margin-bottom: 40px;
}

// 操作区
.toolbar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
  flex-wrap: wrap;
}

.toolbar__query {
  display: flex;
  gap: 8px;
  flex: 1;
  max-width: 520px;
}

.toolbar__input {
  flex: 1;
  padding: 12px 16px;
  font-size: 15px;
  color: var(--df-text-primary);
  background: var(--df-bg-card);
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  outline: none;
  transition: border-color 0.3s ease;

  &:focus {
    border-color: var(--df-color-primary);
  }
}

.toolbar__btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: none;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &--query {
    background: linear-gradient(135deg, var(--df-color-primary) 0%, var(--df-color-primary-dark) 100%);
    color: white;

    &:hover:not(:disabled) {
      transform: translateY(-2px);
    }
  }

  &--sync {
    background: var(--df-bg-card);
    color: var(--df-color-primary);
    border: 2px solid var(--df-color-primary);

    &:hover:not(:disabled) {
      background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
      transform: translateY(-2px);
    }
  }
}

// 地区选择条（省/市/区县 三级下拉菜单 + 真定位）
.city-bar {
  position: relative;
  margin-bottom: 24px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    margin-bottom: 12px;
  }

  &__region {
    font-size: 14px;
    color: var(--df-text-regular);
  }

  &__locate {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    font-size: 14px;
    font-weight: 600;
    color: var(--df-color-primary);
    background: var(--df-bg-card);
    border: 1px solid var(--df-color-primary);
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover:not(:disabled) {
      background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    }

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  }

  // 三个下拉（省/市/区县）横向排列居中
  &__selects {
    position: relative;
    z-index: 5; // 始终高于遮罩：透明遮罩不拦截按钮/菜单，展开时可切换其他下拉
    display: flex;
    justify-content: center;
    align-items: flex-start;
    gap: 12px;
  }

  // 下拉遮罩：点击空白关闭（低于展开的菜单、高于页面内容）
  &__mask {
    position: fixed;
    inset: 0;
    z-index: 2;
  }
}

// 单级下拉菜单（省/市/区县/兜底城市）：上下滑动选择
.city-select {
  position: relative;
  z-index: 1;

  &.is-open {
    z-index: 4; // 展开时盖过遮罩，菜单可点击
  }

  &__btn {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 140px;
    padding: 10px 16px;
    font-size: 14px;
    color: var(--df-text-primary);
    background: var(--df-bg-card);
    border: 1px solid var(--df-border-color);
    border-radius: 12px;
    cursor: pointer;
    transition: border-color 0.3s ease;

    &.is-open {
      border-color: var(--df-color-primary);
    }
  }

  &__label {
    flex-shrink: 0;
    color: var(--df-text-secondary);
  }

  &__value {
    flex: 1;
    text-align: left;
    font-weight: 600;

    &.is-placeholder {
      color: var(--df-text-placeholder);
      font-weight: 400;
    }
  }

  &__caret {
    flex-shrink: 0;
    transition: transform 0.3s ease;

    .is-open & {
      transform: rotate(90deg);
    }
  }

  // 上下滑动菜单：最大高度 + 纵向滚动
  &__menu {
    position: absolute;
    top: calc(100% + 8px);
    left: 0;
    min-width: 100%;
    max-height: 320px;
    overflow-y: auto;
    // 滚动到底后不继续带动页面滚动（中键/滚轮只滚菜单）
    overscroll-behavior: contain;
    padding: 6px;
    background: var(--df-bg-card);
    border: 1px solid var(--df-border-color);
    border-radius: 12px;
    box-shadow: var(--df-shadow-float);
    text-align: left;

    // 兜底城市：网格布局
    &--grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(84px, 1fr));
      gap: 4px;
      width: 480px;
      max-width: 90vw;
    }
  }

  // 列表空态（区划接口异常时避免空白菜单）
  &__empty {
    padding: 16px 12px;
    font-size: 13px;
    text-align: center;
    color: var(--df-text-placeholder);
  }

  &__item {
    display: block;
    width: 100%;
    padding: 8px 12px;
    font-size: 14px;
    color: var(--df-text-regular);
    background: transparent;
    border: none;
    border-radius: 8px;
    text-align: left;
    cursor: pointer;

    &:hover {
      color: var(--df-color-primary);
      background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent);
    }

    &.active {
      color: var(--df-color-primary);
      font-weight: 600;
      background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    }
  }
}

// 自定义询价结果
.query-result {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 20px 32px;
  margin-bottom: 40px;
  font-size: 16px;
  color: var(--df-text-primary);
  background: linear-gradient(135deg, color-mix(in srgb, var(--df-color-secondary) 8%, transparent) 0%, rgba(26, 95, 158, 0.08) 100%);
  border: 1px solid color-mix(in srgb, var(--df-color-secondary) 30%, transparent);
  border-radius: 12px;

  &--empty {
    background: var(--df-bg-card);
    border-color: var(--df-border-color);
  }
}

.query-result__item {
  font-weight: 700;
}

.query-result__price {
  font-size: 28px;
  font-weight: 700;
  color: var(--df-color-primary);
}

.query-result__tag {
  padding: 2px 8px;
  font-size: 12px;
  color: var(--df-text-secondary);
  background: var(--df-border-color);
  border-radius: 999px;
}

.query-result__source,
.query-result__note {
  font-size: 13px;
  color: var(--df-text-secondary);
}

.loading {
  text-align: center;
  padding: 64px;
  color: var(--df-text-secondary);
  font-size: 18px;
}

.prices-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 32px;
}

.price-card {
  padding: 32px 24px;
  background: var(--df-bg-card);
  border-radius: 16px;
  text-align: center;
  box-shadow: var(--df-shadow-card);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-8px);
    box-shadow: var(--df-shadow-float);
  }
}

.price-card__icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.price-card__name {
  font-size: 18px;
  color: var(--df-text-primary);
  font-weight: 600;
  margin-bottom: 12px;
}

.price-card__tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--df-text-secondary);
  background: var(--df-border-color);
  border-radius: 999px;
  vertical-align: 2px;
}

.price-card__price {
  font-size: 32px;
  font-weight: 700;
  color: var(--df-color-primary);
  margin-bottom: 8px;

  span {
    font-size: 14px;
    color: var(--df-text-secondary);
    font-weight: 400;
  }

  &--empty {
    color: var(--df-text-placeholder);
  }
}

.price-card__trend {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;

  &--up {
    color: var(--df-color-danger);
  }

  &--down {
    color: var(--df-color-primary);
  }

  &--builtin {
    font-weight: 400;
    color: var(--df-text-placeholder);
  }
}

.price-card__meta {
  font-size: 12px;
  color: var(--df-text-secondary);
  padding-top: 8px;
  border-top: 1px dashed var(--df-border-color);
}
</style>
