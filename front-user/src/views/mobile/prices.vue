<script setup lang="ts">
// 实时报价（移动端）：与 PC 端 /pc/prices 功能对齐
// - 全 8 品类内置兜底（接口报价优先，未命中标「内置」）
// - 真定位（AMap.Geolocation → 浏览器 Geolocation → IP 兜底）
// - 省/市/区县 三级联动（高德区划不可用时降级为单城市选择）
// - 自定义物品联网询价（结果存 localStorage，下次进入直接展示）
// - 手动联网更新全部品类报价
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import {
  getPrices, getPriceMeta, syncPrices, queryItemPrice, locateCity,
  type CategoryPrice, type PriceMeta, type ItemPriceQuery,
} from '@/api/business'
import { regeo } from '@/api/map'
import { wgs84ToGcj02 } from '@/utils/coord'
import { ensureAmap, listDistricts, getLocation, type DistrictNode } from '@/composables/useAmap'

defineOptions({ name: 'mobile-prices' })

const router = useRouter()

/** 最近一次询价结果浏览器缓存键（localStorage：仅存最新一次，页面加载直接展示） */
const LAST_QUERY_STORAGE_KEY = 'dianfei:prices:last-query'
const REGION_STORAGE_KEY = 'dianfei:prices:region'

/** 高德区划不可用时的兜底常用城市 */
const FALLBACK_CITIES = [
  '北京', '上海', '广州', '深圳', '天津', '重庆', '杭州', '南京',
  '武汉', '成都', '西安', '苏州', '长沙', '郑州', '青岛', '大连',
  '沈阳', '厦门', '福州', '昆明', '合肥', '南昌', '石家庄', '太原',
]

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

/** 搜索城市（联网/同步入参）：区县已选拼「市+区县」提升本地精度 */
const searchCity = computed(() => {
  const r = region.value
  if (r.district && r.city) return `${r.city}${r.district}`
  if (r.city) return r.city
  if (r.province) return r.province
  return ''
})

/** 展示用地区全称 */
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

/** 地区下拉菜单：当前展开项，空串关闭 */
type MenuKey = '' | 'province' | 'city' | 'district' | 'fallback'
const openMenu = ref<MenuKey>('')
function toggleMenu(key: Exclude<MenuKey, ''>) {
  openMenu.value = openMenu.value === key ? '' : key
}
function closeMenu() {
  openMenu.value = ''
}

/** 全部品类（与 PC 端一致）：接口命中覆盖内置价，未命中标「内置」；命中但价格为 0（联网搜索无结果落库标 0）时同样回退内置价展示，避免页面全部显示 0 */
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

/** 浏览器定位（HTML5 Geolocation） */
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

/** 匹配后端城市名与高德区划名（去「省/市」后缀） */
function sameDistrict(a: string, b: string) {
  return a.replace(/[省市]$/, '') === b.replace(/[省市]$/, '')
}

/** 定位结果落到三级区划并联动展开 */
async function applyLocatedRegion(prov: string, city: string, district: string) {
  region.value = { province: prov, city, district }
  saveRegion()
  if (!districtsReady.value || !provinces.value.length) return
  const p = provinces.value.find((x) => x.name === prov)
  if (!p) return
  const clist = await listDistricts(p.name, 'city')
  cities.value = clist
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

/** 真定位：AMap.Geolocation → 浏览器定位 + regeo → IP 兜底 */
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

/** 初始化级联数据源：高德不可用 → 单城市兜底 */
async function initDistricts() {
  const ok = await ensureAmap()
  if (ok) {
    const plist = await listDistricts('中国', 'province')
    if (plist.length) {
      provinces.value = plist
      districtsReady.value = true
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
  if (!region.value.city && priceMeta.value.city) {
    region.value.city = priceMeta.value.city
    saveRegion()
  }
}

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

function saveLastQuery(q: ItemPriceQuery) {
  try {
    localStorage.setItem(LAST_QUERY_STORAGE_KEY, JSON.stringify(q))
  } catch {
    /* 存储失败忽略 */
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

/** 选省 → 加载市；直辖市自动选唯一市继续展开 */
async function selectProvince(name: string) {
  closeMenu()
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
  closeMenu()
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

function selectDistrict(name: string) {
  closeMenu()
  region.value = { ...region.value, district: name }
  saveRegion()
}

function selectFallbackCity(name: string) {
  closeMenu()
  region.value = { province: '', city: name, district: '' }
  saveRegion()
}

/** 定位按钮 */
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

/** 自定义物品联网询价 */
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
  <div class="prices">
    <van-nav-bar title="实时报价" left-arrow @click-left="router.back()" />

    <div class="page">
      <!-- 头部说明 -->
      <p class="prices__desc">内置参考价直接展示，可联网查询当地行情或自定义询价</p>
      <p v-if="!loading" class="prices__meta">
        <template v-if="prices.length">
          {{ priceMeta.city || '当地' }} · 更新于 {{ priceMeta.lastSyncTime || prices[0]?.updateTime || '—' }}
          （每天 08:05 自动定位并同步）
        </template>
        <template v-else>
          当前为内置参考价，点击「联网更新」获取{{ priceMeta.city || '北京' }}实时行情
        </template>
      </p>

      <!-- 地区选择 -->
      <div v-if="!loading" class="region-bar">
        <button class="region-bar__locate" :disabled="locating" @click="onLocate">
          <SvgIcon name="common-location" :size="14" />
          {{ locating ? '定位中…' : '真定位' }}
        </button>
        <span v-if="displayRegion" class="region-bar__current">{{ displayRegion }}</span>
      </div>

      <van-skeleton :loading="loading" :row="6">
        <!-- 省市区三级下拉 / 兜底单城市 -->
        <div class="region-selects">
          <template v-if="districtsReady">
            <button
              class="region-select"
              :class="{ 'is-open': openMenu === 'province' }"
              @click="toggleMenu('province')"
            >
              <span class="region-select__label">省</span>
              <span class="region-select__value" :class="{ 'is-placeholder': !region.province }">
                {{ region.province || '请选择' }}
              </span>
              <SvgIcon name="common-arrow-right" :size="12" class="region-select__caret" />
            </button>
            <button
              class="region-select"
              :class="{ 'is-open': openMenu === 'city' }"
              @click="toggleMenu('city')"
            >
              <span class="region-select__label">市</span>
              <span class="region-select__value" :class="{ 'is-placeholder': !region.city }">
                {{ region.city || (cities.length ? '请选择' : '请先选省') }}
              </span>
              <SvgIcon name="common-arrow-right" :size="12" class="region-select__caret" />
            </button>
            <button
              class="region-select"
              :class="{ 'is-open': openMenu === 'district' }"
              @click="toggleMenu('district')"
            >
              <span class="region-select__label">区县</span>
              <span class="region-select__value" :class="{ 'is-placeholder': !region.district }">
                {{ region.district || (districts.length ? '请选择' : '请先选市') }}
              </span>
              <SvgIcon name="common-arrow-right" :size="12" class="region-select__caret" />
            </button>
          </template>
          <button
            v-else
            class="region-select"
            :class="{ 'is-open': openMenu === 'fallback' }"
            @click="toggleMenu('fallback')"
          >
            <span class="region-select__label">城市</span>
            <span class="region-select__value" :class="{ 'is-placeholder': !region.city }">
              {{ region.city || '请选择' }}
            </span>
            <SvgIcon name="common-arrow-right" :size="12" class="region-select__caret" />
          </button>
        </div>

        <!-- 操作区：自定义询价 + 联网更新 -->
        <div class="toolbar">
          <input
            v-model="queryItem"
            class="toolbar__input"
            maxlength="32"
            placeholder="自定义询价，如：易拉罐"
            @keyup.enter="onQuery"
          >
          <button class="toolbar__btn toolbar__btn--query" :disabled="querying" @click="onQuery">
            {{ querying ? '查询中…' : '查询' }}
          </button>
          <button class="toolbar__btn toolbar__btn--sync" :disabled="syncing" @click="onSync">
            <SvgIcon name="common-refresh" :size="14" />
            {{ syncing ? '更新中…' : '联网更新' }}
          </button>
        </div>

        <!-- 自定义询价结果 -->
        <div v-if="queryResult" class="query-result" :class="{ 'query-result--empty': !queryResult.pricePerKg }">
          <template v-if="queryResult.pricePerKg">
            <div class="query-result__row">
              <span class="query-result__item">{{ queryResult.item }}</span>
              <span class="query-result__city">{{ queryResult.city || '当地' }}今日回收价</span>
              <span class="query-result__price">¥{{ queryResult.pricePerKg.toFixed(2) }}/kg</span>
            </div>
            <div class="query-result__tags">
              <span v-if="queryResult.degraded" class="query-result__tag">参考价</span>
              <span v-if="queryResult.cached" class="query-result__tag">缓存</span>
              <span v-if="queryResult.source" class="query-result__source">来源：{{ queryResult.source }}</span>
            </div>
            <p v-if="queryResult.note" class="query-result__note">{{ queryResult.note }}</p>
          </template>
          <template v-else>
            <div class="query-result__row">
              <span class="query-result__item">{{ queryResult.item }}</span>
              <span>暂未查询到数据</span>
            </div>
            <p v-if="queryResult.note" class="query-result__note">{{ queryResult.note }}</p>
          </template>
        </div>

        <!-- 全品类报价列表 -->
        <div class="card">
          <div v-for="p in displayList" :key="p.code" class="price-row">
            <div class="price-row__left">
              <SvgIcon :name="p.icon" :size="20" />
              <span class="price-row__name">
                {{ p.name }}
                <span v-if="p.builtin" class="price-row__tag">内置</span>
              </span>
            </div>
            <div class="price-row__right">
              <span class="price-row__value">¥{{ p.price.toFixed(2) }}<span class="price-row__unit">/kg</span></span>
              <span
                v-if="!p.builtin"
                class="price-row__trend"
                :class="p.trend > 0 ? 'up' : (p.trend < 0 ? 'down' : 'flat')"
              >
                <template v-if="p.trend > 0">↑ {{ Math.abs(p.trend) }}%</template>
                <template v-else-if="p.trend < 0">↓ {{ Math.abs(p.trend) }}%</template>
                <template v-else>—</template>
              </span>
              <span v-else class="price-row__trend builtin">内置参考价</span>
            </div>
            <p v-if="!p.builtin" class="price-row__meta">
              {{ p.city }} · {{ p.updateTime || '—' }} 更新
            </p>
          </div>
        </div>

        <p class="prices__tip">报价每日 08:05 联网更新；最终结算以上门称重与当日价为准</p>
        <van-button round block type="primary" @click="router.push('/recycle/book')">立即预约回收</van-button>
      </van-skeleton>
    </div>

    <!-- 下拉菜单浮层（点击外部关闭） -->
    <van-popup
      :show="openMenu !== ''"
      position="bottom"
      :overlay="true"
      :close-on-click-overlay="true"
      @update:show="(v: boolean) => { if (!v) closeMenu() }"
      class="region-popup"
    >
      <div class="region-popup__head">
        <span>{{
          openMenu === 'province' ? '选择省' :
          openMenu === 'city' ? '选择市' :
          openMenu === 'district' ? '选择区县' : '选择城市'
        }}</span>
        <button class="region-popup__close" @click="closeMenu">关闭</button>
      </div>
      <div class="region-popup__list" :class="{ 'region-popup__list--grid': openMenu === 'fallback' }">
        <template v-if="openMenu === 'province'">
          <button
            v-for="p in provinces"
            :key="p.adcode"
            class="region-popup__item"
            :class="{ active: p.name === region.province }"
            @click="selectProvince(p.name)"
          >{{ p.name }}</button>
        </template>
        <template v-else-if="openMenu === 'city'">
          <button
            v-for="c in cities"
            :key="c.adcode"
            class="region-popup__item"
            :class="{ active: c.name === region.city }"
            @click="selectCity(c.name)"
          >{{ c.name }}</button>
        </template>
        <template v-else-if="openMenu === 'district'">
          <button
            v-for="d in districts"
            :key="d.adcode"
            class="region-popup__item"
            :class="{ active: d.name === region.district }"
            @click="selectDistrict(d.name)"
          >{{ d.name }}</button>
        </template>
        <template v-else-if="openMenu === 'fallback'">
          <button
            v-for="city in FALLBACK_CITIES"
            :key="city"
            class="region-popup__item"
            :class="{ active: city === region.city }"
            @click="selectFallbackCity(city)"
          >{{ city }}</button>
        </template>
        <div v-if="!provinces.length && openMenu === 'province'" class="region-popup__empty">暂无数据</div>
        <div v-else-if="!cities.length && openMenu === 'city'" class="region-popup__empty">暂无数据</div>
        <div v-else-if="!districts.length && openMenu === 'district'" class="region-popup__empty">暂无数据</div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.prices {
  &__desc {
    margin: 0 $df-space-md $df-space-xs;
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
    line-height: 1.5;
  }

  &__meta {
    margin: 0 $df-space-md $df-space-md;
    font-size: $df-font-size-xs;
    color: var(--df-text-placeholder);
    line-height: 1.5;
  }

  &__tip {
    margin: $df-space-md 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
    text-align: center;
    line-height: 1.5;
  }
}

.page {
  padding: $df-space-md;
  padding-bottom: calc(var(--df-nav-h) + env(safe-area-inset-bottom) + $df-space-lg);
}

// 真定位按钮 + 当前地区
.region-bar {
  display: flex;
  align-items: center;
  gap: $df-space-sm;
  margin: 0 0 $df-space-md;

  &__locate {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 6px 14px;
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-color-primary);
    background: var(--df-bg-card);
    border: 1px solid var(--df-color-primary);
    border-radius: 999px;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;

    &:disabled {
      opacity: 0.6;
    }
  }

  &__current {
    flex: 1;
    font-size: $df-font-size-sm;
    color: var(--df-text-regular);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

// 省市区三级下拉按钮
.region-selects {
  display: flex;
  gap: $df-space-xs;
  margin-bottom: $df-space-md;
}

.region-select {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  font-size: $df-font-size-sm;
  color: var(--df-text-primary);
  background: var(--df-bg-card);
  border: 1px solid var(--df-border-color);
  border-radius: $df-radius-sm;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;

  &.is-open {
    border-color: var(--df-color-primary);
  }

  &__label {
    flex-shrink: 0;
    color: var(--df-text-secondary);
  }

  &__value {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 600;
    text-align: left;

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
}

// 工具条：询价输入 + 联网更新
.toolbar {
  display: flex;
  gap: $df-space-xs;
  margin-bottom: $df-space-md;

  &__input {
    flex: 1;
    min-width: 0;
    padding: 8px 12px;
    font-size: $df-font-size-sm;
    color: var(--df-text-primary);
    background: var(--df-bg-card);
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-sm;
    outline: none;
    transition: border-color 0.2s ease;

    &:focus {
      border-color: var(--df-color-primary);
    }
  }

  &__btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    padding: 8px 14px;
    font-size: $df-font-size-sm;
    font-weight: 600;
    border: none;
    border-radius: $df-radius-sm;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;

    &:disabled {
      opacity: 0.6;
    }

    &--query {
      background: var(--df-color-primary);
      color: #fff;
    }

    &--sync {
      background: var(--df-bg-card);
      color: var(--df-color-primary);
      border: 1px solid var(--df-color-primary);
    }
  }
}

// 自定义询价结果
.query-result {
  padding: $df-space-md;
  margin-bottom: $df-space-md;
  background: linear-gradient(135deg, rgba(242, 157, 40, 0.08) 0%, rgba(47, 107, 94, 0.08) 100%);
  border: 1px solid rgba(242, 157, 40, 0.3);
  border-radius: $df-radius-md;

  &--empty {
    background: var(--df-bg-card);
    border-color: var(--df-border-color);
  }

  &__row {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__item {
    font-weight: 700;
    font-size: $df-font-size-base;
    color: var(--df-text-primary);
  }

  &__city {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__price {
    font-size: $df-font-size-lg;
    font-weight: 700;
    color: var(--df-color-primary-dark);
    margin-left: auto;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 6px;
  }

  &__tag {
    padding: 1px 8px;
    font-size: 10px;
    color: var(--df-text-secondary);
    background: var(--df-border-color);
    border-radius: 999px;
  }

  &__source {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__note {
    margin: 6px 0 0;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    line-height: 1.5;
  }
}

// 全品类报价列表
.card {
  background: var(--df-bg-card);
  border-radius: $df-radius-md;
  padding: 0 $df-space-md;
  box-shadow: var(--df-shadow-card);
}

.price-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  padding: $df-space-sm 0;
  border-bottom: 1px dotted var(--df-border-color);
  -webkit-tap-highlight-color: transparent;

  &:last-child {
    border-bottom: none;
  }

  &__left {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    min-width: 0;
  }

  &__name {
    font-size: $df-font-size-base;
    font-weight: 500;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  // 行内行情地区 + 更新时间（与 PC 端 price-card__meta 对齐，整行换行展示）
  &__meta {
    flex-basis: 100%;
    margin: 6px 0 0 28px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__tag {
    padding: 1px 6px;
    font-size: 10px;
    color: var(--df-text-secondary);
    background: var(--df-border-color);
    border-radius: 999px;
  }

  &__right {
    text-align: right;
    display: flex;
    align-items: center;
    gap: $df-space-sm;
  }

  &__value {
    font-size: $df-font-size-base;
    font-weight: 600;
    color: var(--df-color-primary-dark);
  }

  &__unit {
    font-size: $df-font-size-xs;
    font-weight: 400;
    color: var(--df-text-secondary);
    margin-left: 2px;
  }

  &__trend {
    display: inline-block;
    font-size: $df-font-size-xs;
    font-weight: 600;
    padding: 1px 6px;
    border-radius: $df-radius-sm;
    min-width: 48px;
    text-align: center;

    &.up {
      color: var(--df-color-stamp);
      background: rgba(201, 79, 61, 0.1);
    }

    &.down {
      color: var(--df-color-primary);
      background: var(--df-color-primary-light);
    }

    &.flat {
      color: var(--df-text-secondary);
      background: var(--df-border-color);
    }

    &.builtin {
      color: var(--df-text-placeholder);
      background: transparent;
      font-weight: 400;
    }
  }
}

// 下拉浮层（van-popup bottom）
.region-popup {
  max-height: 60vh;

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $df-space-md;
    border-bottom: 1px solid var(--df-border-color);
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__close {
    background: none;
    border: none;
    color: var(--df-color-primary);
    font-size: $df-font-size-sm;
    cursor: pointer;
  }

  &__list {
    padding: $df-space-sm;
    overflow-y: auto;
    max-height: 50vh;
    overscroll-behavior: contain;

    // 兜底城市网格
    &--grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 4px;
    }
  }

  &__item {
    display: block;
    width: 100%;
    padding: 10px 12px;
    font-size: $df-font-size-sm;
    color: var(--df-text-regular);
    background: transparent;
    border: none;
    border-radius: $df-radius-sm;
    text-align: left;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;

    &.active {
      color: var(--df-color-primary);
      font-weight: 600;
      background: var(--df-color-primary-light);
    }
  }

  &__empty {
    padding: 24px 12px;
    font-size: $df-font-size-sm;
    text-align: center;
    color: var(--df-text-placeholder);
  }
}
</style>
