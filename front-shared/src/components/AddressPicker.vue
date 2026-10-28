<script setup lang="ts">
// ============================================================
// 地址选择器（D0 通用业务组件，address-book 正式版）：
// 1. 省/市/区三级级联（高德 AMap.DistrictSearch，经共享请求层拉取 /map/config，
//    无 key/加载失败自动降级为「片区 + 详细地址」旧模式）
// 2. 片区按钮组（派单归属，面向业务 service 区）
// 3. 真实定位（优先高德 AMap.Geolocation，降级 navigator.geolocation；
//    拿到坐标后调 /api/map/regeo 逆地理 → 自动填充省市区 + 详细地址 + 经纬度）
// 4. 常用地址快捷（saved 由页面传入地址簿数据，点选即填充）
// 依赖注入：不 import 各端私有 API，全部经 registerSharedRequest 的共享请求层
// ============================================================
import { onMounted, ref } from 'vue'
import { getSharedRequest } from '../http/sharedHttp'
import type { AddressValue } from './address'
import { addressTextOf } from './address'

interface DistrictNode {
  name: string
  adcode?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: AddressValue
    /** 展示"定位"按钮（预约上门/地址簿表单用） */
    locatable?: boolean
    /** 常用地址（调用方从地址簿拉取；为空不展示） */
    saved?: AddressValue[] | null
    /** 展示"管理地址簿"入口按钮（emit open-book，由宿主跳转地址簿页） */
    manageable?: boolean
  }>(),
  { locatable: false, saved: () => [] as AddressValue[], manageable: false },
)
const emit = defineEmits<{
  (e: 'update:modelValue', v: AddressValue): void
  (e: 'open-book'): void
}>()

const locating = ref(false)
const locateError = ref('')
const mapVisible = ref(false)
const selectedLngLat = ref<[number, number] | null>(null)
const mapInfoText = ref('')
const mapDragEnabled = ref(true) // 允许拖拽
let currentMapClickLngLat: [number, number] | null = null
let mapInstance: unknown = null
let markerInstance: unknown = null
const amapNamespace = ref<unknown>(null)

// ---------------- 高德加载（env 优先，config 经共享请求层兜底） ----------------
const amapReady = ref(false)
let loadPromise: Promise<boolean> | null = null

function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('AMap 脚本加载失败'))
    document.head.appendChild(script)
  })
}

function ensureAmap(): Promise<boolean> {
  if ((window as unknown as { AMap?: unknown }).AMap) return Promise.resolve(true)
  if (loadPromise) return loadPromise
  loadPromise = (async () => {
    try {
      // VITE_AMAP_WEB_KEY（前端 env）优先：后端 8080 关闭时地址级联/定位仍可用
      const importMeta = import.meta as unknown as { env?: { VITE_AMAP_WEB_KEY?: string; VITE_AMAP_SECURITY_CODE?: string } }
      let webKey = importMeta.env?.VITE_AMAP_WEB_KEY
      let securityCode = importMeta.env?.VITE_AMAP_SECURITY_CODE
      if (!webKey) {
        const req = getSharedRequest()
        if (!req) return false
        const cfg = await req.get<{ webKey?: string; securityCode?: string }>('/map/config')
        webKey = cfg?.webKey?.trim()
        securityCode = cfg?.securityCode?.trim()
      }
      if (!webKey) return false
      const w = window as unknown as { _AMapSecurityConfig?: { securityJsCode?: string } }
      if (securityCode) w._AMapSecurityConfig = { securityJsCode: securityCode }
      await loadScript(
        `https://webapi.amap.com/maps?v=2.0&key=${webKey}&plugin=AMap.DistrictSearch,AMap.Geolocation,AMap.Geocoder`,
      )
      return !!(window as unknown as { AMap?: unknown }).AMap
    } catch {
      return false
    } finally {
      // 失败后释放 promise，允许下次打开重试（后端恢复 / env 补齐后不再永久降级）
      if (!(window as unknown as { AMap?: unknown }).AMap) loadPromise = null
    }
  })()
  return loadPromise
}

// ---------------- 省市区级联（AMap.DistrictSearch） ----------------
type DistrictSearchLike = {
  setLevel: (level: string) => void
  search: (keyword: string, cb: (status: string, result?: { districtList?: unknown[] }) => void) => void
}

const openColumn = ref<'province' | 'city' | 'district' | null>(null)
const provinces = ref<DistrictNode[]>([])
const cities = ref<DistrictNode[]>([])
const districts = ref<DistrictNode[]>([])
const districtLoading = ref(false)
let districtSearch: DistrictSearchLike | null = null

function searchAsync(keyword: string): Promise<DistrictNode[]> {
  return new Promise((resolve) => {
    if (!districtSearch) return resolve([])
    districtSearch.search(keyword, (status, result) => {
      if (status !== 'complete') return resolve([])
      const first = result?.districtList?.[0] as { districtList?: unknown[] } | undefined
      const list = (first?.districtList ?? []) as { name?: string; adcode?: string }[]
      resolve(list.map((d) => ({ name: d.name ?? '', adcode: d.adcode ?? '' })).filter((d) => d.name))
    })
  })
}

function toggleColumn(col: 'province' | 'city' | 'district') {
  const disabled = col === 'city' && !props.modelValue.province || col === 'district' && !props.modelValue.city
  if (disabled) return
  openColumn.value = openColumn.value === col ? null : col
}

async function selectProvince(d: DistrictNode) {
  emit('update:modelValue', { ...props.modelValue, province: d.name, city: '', district: '' })
  districtLoading.value = true
  try {
    districtSearch?.setLevel('city')
    cities.value = await searchAsync(d.name)
    if (cities.value.length) {
      openColumn.value = 'city'
    } else {
      // 直辖市：市=省名，直接下钻区级
      emit('update:modelValue', { ...props.modelValue, province: d.name, city: d.name, district: '' })
      districtSearch?.setLevel('district')
      districts.value = await searchAsync(d.name)
      openColumn.value = 'district'
    }
  } finally {
    districtLoading.value = false
  }
}

async function selectCity(d: DistrictNode) {
  emit('update:modelValue', { ...props.modelValue, city: d.name, district: '' })
  districtLoading.value = true
  try {
    districtSearch?.setLevel('district')
    districts.value = await searchAsync(d.name)
    openColumn.value = districts.value.length ? 'district' : null
  } finally {
    districtLoading.value = false
  }
}

function selectDistrict(d: DistrictNode) {
  emit('update:modelValue', { ...props.modelValue, district: d.name })
  openColumn.value = null
}

// ---------------- 真实定位（高德 Geolocation → /map/regeo 逆地理） ----------------
async function markCurrentPosition() {
  locating.value = true
  locateError.value = ''
  try {
    const { lng, lat } = await getPosition()
    // 记录坐标（供打开地图时作为初始中心），并直接逆地理填充表单
    selectedLngLat.value = [lng, lat]
    performRegeoReverse(lng, lat)
  } catch {
    locateError.value = '定位失败，请尝试手动填写地址'
  } finally {
    locating.value = false
  }
}

async function openMapSelector() {
  // 打开地图前先确保已定位，如果未定位则先执行定位
  if (!selectedLngLat.value && !locating.value) {
    locateError.value = ''
    locating.value = true
    try {
      const { lng, lat } = await getPosition()
      selectedLngLat.value = [lng, lat]
      mapInfoText.value = `正在定位，请稍候...`
    } catch {
      locateError.value = '定位失败，将使用默认位置'
      selectedLngLat.value = [116.4, 39.9] // 默认北京
      mapInfoText.value = `默认位置：北京，拖动地图调整` 
    } finally {
      locating.value = false
    }
  } else if (!selectedLngLat.value) {
    // 正在定位中，等待
    mapInfoText.value = `正在定位，请稍候...`
  } else {
    // 已有定位，直接使用
    currentMapClickLngLat = null
    mapInfoText.value = `已定位到附近：(${selectedLngLat.value!.join(', ')})，拖动地图调整`
  }
  mapVisible.value = true
}

async function confirmMapSelection() {
  // 优先使用地图点击选中的位置，其次使用已定位位置
  const use = currentMapClickLngLat ?? selectedLngLat.value
  if (use) {
    const [lng, lat] = use

    // D0: 片区自动创建（返回片区补丁，不单独 emit，避免覆盖地址字段）
    let areaPatch: { areaCode: string; areaName: string } | null = null
    try {
      areaPatch = await autoCreateArea(lng, lat)
    } catch (e) {
      console.error('[AddressPicker] 片区自动创建失败', e)
    }

    // 逆地理回填：后端 /map/regeo 优先，5001 时前端 AMap.Geocoder 兜底（JS key 可用）
    const addr = await reverseGeocode(lng, lat)
    const detail = detailOf(addr)

    // 合并为一次 emit，避免连续 emit 基于旧 props 快照互相覆盖
    emit('update:modelValue', {
      ...props.modelValue,
      lng,
      lat,
      province: addr.province || props.modelValue.province,
      city: addr.city || props.modelValue.city,
      district: addr.district || props.modelValue.district,
      detail: detail || props.modelValue.detail,
      ...(areaPatch ?? {}),
    })
  }

  mapVisible.value = false
}

/** 根据经纬度自动创建/匹配片区（调用后端 API /api/staff/areas/auto-create，silent 不弹 toast） */
async function autoCreateArea(lng: number, lat: number): Promise<{ areaCode: string; areaName: string } | null> {
  try {
    const req = getSharedRequest()
    if (!req) {
      console.warn('[AddressPicker] 共享请求层未就绪，跳过片区自动创建')
      return null
    }
    const resp = await req.post<{ id: string; name: string }>(
      '/staff/areas/auto-create',
      { lng, lat },
      { silent: true, skipErrorToast: true },
    )
    if (resp && resp.id) {
      // 雪花 ID 超 JS 安全整数：字符串透传不丢失精度
      return { areaCode: `A${resp.id}`, areaName: resp.name }
    }
  } catch (e) {
    console.error('[AddressPicker] 片区自动创建失败', e)
    // 失败不阻塞用户，允许手动填写
  }
  return null
}

/** 逆地理公共函数：后端 /map/regeo 优先；后端 5001/不可用时前端 AMap.Geocoder 兜底（定位可用的 JS key 直连） */
async function reverseGeocode(lng: number, lat: number): Promise<{ province: string; city: string; district: string; formattedAddress: string; village: string }> {
  let province = ''
  let city = ''
  let district = ''
  let formattedAddress = ''
  let village = ''

  const req = getSharedRequest()
  if (req) {
    try {
      // silent + skipErrorToast：后端 key 未配置/类型不匹配（SERVICE_NOT_AVAILABLE）时不弹 toast，静默走前端兜底
      const g = await req.get<{ province?: string; city?: string; district?: string; formattedAddress?: string; village?: string }>(
        '/map/regeo',
        { lng, lat },
        { silent: true, skipErrorToast: true },
      )
      province = g.province ?? ''
      city = g.city || province
      district = g.district ?? ''
      formattedAddress = g.formattedAddress ?? ''
      village = g.village ?? ''
    } catch {
      /* 后端不可用：走前端 Geocoder */
    }
  }

  // 后端失败 → 前端 AMap.Geocoder 兜底（与定位同一 JS key，定位可用则此路必通）
  const win = window as unknown as { AMap?: unknown }
  if (!province && typeof win.AMap !== 'undefined') {
    const amap = win.AMap as unknown as {
      Geocoder?: new (o?: Record<string, unknown>) => unknown
    }
    const Geocoder = amap?.Geocoder
    if (Geocoder) {
      try {
        const addr = await new Promise<{ province: string; city: string; district: string; formattedAddress: string } | null>(
          (resolve) => {
            try {
              const geocoder = new Geocoder({})
              ;(geocoder as unknown as {
                getAddress: (lnglat: [number, number], cb: (status: string, result?: { regeocode?: unknown }) => void) => void
              }).getAddress([lng, lat], (status, result) => {
                if (status !== 'complete' || !result?.regeocode) return resolve(null)
                const r = result.regeocode as {
                  formattedAddress?: string
                  addressComponent?: { province?: string; city?: unknown; district?: string }
                }
                const comp = r.addressComponent ?? {}
                const cityVal = Array.isArray(comp.city) ? comp.city[0] : comp.city
                resolve({
                  province: comp.province ?? '',
                  city: typeof cityVal === 'string' ? cityVal : '',
                  district: comp.district ?? '',
                  formattedAddress: r.formattedAddress ?? '',
                })
              })
            } catch {
              resolve(null)
            }
          },
        )
        if (addr) {
          province = addr.province
          city = addr.city || addr.province
          district = addr.district
          formattedAddress = addr.formattedAddress
        }
      } catch {
        /* 前端逆地理失败：保持空值，允许手动填写 */
      }
    }
  }

  return { province, city, district, formattedAddress, village }
}

/** detail 回填：裁剪省市区前缀后，村名缺失时补在首位（定位精确到村，如「塘埠村 上畈路 12 号」） */
function detailOf(addr: { province: string; city: string; district: string; formattedAddress: string; village?: string }): string {
  const region = `${addr.province}${addr.city}${addr.district}`
  let detail = addr.formattedAddress
  if (region && detail.startsWith(region)) detail = detail.slice(region.length)
  const v = addr.village?.trim()
  if (v && !detail.includes(v)) detail = `${v} ${detail}`.trim()
  return detail
}

async function performRegeoReverse(lng: number, lat: number) {
  // 复用公共逆地理（后端优先 + 前端兜底）
  const addr = await reverseGeocode(lng, lat)
  // D0：片区自动创建/匹配（与地图确认同链路），失败不阻塞定位回填
  let areaPatch: { areaCode: string; areaName: string } | null = null
  try {
    areaPatch = await autoCreateArea(lng, lat)
  } catch (e) {
    console.error('[AddressPicker] 片区自动创建失败', e)
  }

  emit('update:modelValue', {
    ...props.modelValue,
    province: addr.province,
    city: addr.city,
    district: addr.district,
    detail: detailOf(addr) || props.modelValue.detail,
    lng,
    lat,
    ...(areaPatch ?? {}),
  })
  mapVisible.value = false
}
function getPosition(): Promise<{ lng: number; lat: number }> {
  const amap = (window as unknown as { AMap?: { Geolocation?: new (opts?: Record<string, unknown>) => unknown } }).AMap
  const Geolocation = amap?.Geolocation
  if (Geolocation) {
    return new Promise((resolve, reject) => {
      const geo = new Geolocation({ enableHighAccuracy: true, timeout: 10000, zoomToAccuracy: false })
      try {
        ;(geo as unknown as {
          getCurrentPosition: (cb: (status: string, result?: { position?: unknown }) => void) => void
        }).getCurrentPosition((status, result) => {
          const p = result?.position as { getLng?: () => number; getLat?: () => number; lng?: number; lat?: number } | undefined
          if (status === 'complete' && p) {
            const lng = typeof p.getLng === 'function' ? p.getLng() : (p.lng as number | undefined)
            const lat = typeof p.getLat === 'function' ? p.getLat() : (p.lat as number | undefined)
            if (typeof lng === 'number' && typeof lat === 'number') resolve({ lng, lat })
            else reject(new Error('定位失败'))
          } else reject(new Error('定位失败'))
        })
      } catch {
        reject(new Error('定位失败'))
      }
    })
  }
  if (!navigator.geolocation) return Promise.reject(new Error('定位不可用'))
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lng: p.coords.longitude, lat: p.coords.latitude }),
      () => reject(new Error('定位失败')),
      { timeout: 8000 },
    )
  })
}

// ---------------- 片区 / 详细地址 / 常用地址 ----------------
function selectArea(code: string, name: string) {
  emit('update:modelValue', { ...props.modelValue, areaCode: code, areaName: name })
}

function onDetailInput(e: Event) {
  emit('update:modelValue', { ...props.modelValue, detail: (e.target as HTMLInputElement).value })
}

function useSaved(s: AddressValue) {
  emit('update:modelValue', { ...props.modelValue, ...s })
}

onMounted(async () => {
  amapReady.value = await ensureAmap()
  const amap = (window as unknown as { AMap?: { DistrictSearch?: new (o?: Record<string, unknown>) => unknown } }).AMap
  if (!amapReady.value || !amap?.DistrictSearch) return
  try {
    districtSearch = new amap.DistrictSearch({ level: 'country', subdistrict: 1 }) as unknown as DistrictSearchLike
    provinces.value = await searchAsync('中国')
  } catch {
    // 行政区划接口失败：静默降级，仅保留片区+详情模式
    amapReady.value = false
  }
})

const activeColumn = () => (openColumn.value === 'province' ? provinces.value : openColumn.value === 'city' ? cities.value : districts.value)

// ---------------- CSS 中心固定标记（与管理端站点选点同款图钉） ----------------
const createCenterPin = (container: HTMLElement) => {
  // 清理旧 DOM（防止重复）
  const existing = container.querySelector('.df-address-picker__center-pin')
  existing?.remove()

  const pin = document.createElement('div')
  pin.className = 'df-address-picker__center-pin'
  pin.innerHTML = `<svg viewBox="0 0 24 24" width="34" height="34" xmlns="http://www.w3.org/2000/svg">
    <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5a2.5 2.5 0 1 1 0-5 2.5 2.5 0 0 1 0 5z" fill="#e64340" stroke="#fff" stroke-width="1.5"/>
  </svg>`
  pin.style.cssText = `position:absolute;top:50%;left:50%;transform:translate(-50%,-100%);pointer-events:none;z-index:100;filter:drop-shadow(0 2px 3px rgba(0,0,0,.3));`
  container.appendChild(pin)
}

// ---------------- 地图选择器初始化与清理 ----------------
function initMap() {
  if (!amapReady.value) return
  const win = window as unknown as { AMap?: unknown }
  const amap = win.AMap as unknown as {
    Map?: new (el: HTMLElement, opts: Record<string, unknown>) => unknown
    LngLat?: new (lng: number, lat: number) => unknown
  }
  if (!amap?.Map || !amap.LngLat) return

  const container = document.getElementById('map-selector')
  if (!container) return

  // 销毁旧实例（防止重复创建）
  const mapAny = mapInstance as { destroy?: () => void }
  if (mapInstance && typeof mapAny.destroy === 'function') {
    mapAny.destroy()
  }

  amapNamespace.value = amap

  // 初始中心：已定位坐标优先，否则默认中心（北京）
  const initial = selectedLngLat.value ?? [116.4, 39.9]
  mapInstance = new amap.Map(container, {
    zoom: 18, // 放大地图：从 17→18，显示更详细街道级信息
    center: new amap.LngLat!(initial[0], initial[1]),
    resizeEnable: true,
    dragControl: true,
    scrollWheel: true,
    mapStyle: 'amap://styles/whitesmoke', // 浅色主题，提高清晰度
  }) as unknown

  // CSS 中心固定标记（红大头针 + 阴影光晕，不依赖外部图标资源）
  createCenterPin(container)

  // 实时预览：moveend 事件（拖动/缩放结束）以地图中心更新坐标，与管理端站点选点一致
  ;(mapInstance as {
    on: (evt: string, cb: (e?: { lnglat?: { getLng?: () => number; getLat?: () => number; lng?: number; lat?: number } }) => void) => void
  }).on(
    'moveend',
    () => {
      try {
        const mapAny = mapInstance as unknown as { getCenter?: () => any }
        if (!mapAny.getCenter) return
        const center = mapAny.getCenter()
        if (!center) return
        const ll = center.lnglat as { getLng?: () => number; getLat?: () => number; lng?: number; lat?: number }
        if (!ll) return
        const lng = typeof ll.getLng === 'function' ? ll.getLng() : ll.lng
        const lat = typeof ll.getLat === 'function' ? ll.getLat() : ll.lat
        if (typeof lng !== 'number' || typeof lat !== 'number') return
        currentMapClickLngLat = [lng, lat]
        mapInfoText.value = `拖动预览位置：(${lng.toFixed(4)}, ${lat.toFixed(4)})`
      } catch {
        /* 忽略单次处理异常 */
      }
    },
  )
}

function cleanupMap() {
  if (mapInstance && typeof (mapInstance as { destroy?: () => void }).destroy === 'function') {
    ;(mapInstance as { destroy: () => void }).destroy()
  }
  mapInstance = null
  markerInstance = null
  amapNamespace.value = null
}

// watch mapVisible to init/cleanup map
// 打开弹窗时初始化地图，关闭时清理
import { watch } from 'vue'

onMounted(() => {
  watch(mapVisible, (show) => {
    if (show) {
      // 延迟初始化地图确保 DOM 已渲染
      setTimeout(() => initMap(), 100)
    } else {
      cleanupMap()
    }
  })
})
</script>

<template>
  <div class="df-address-picker">
    <div v-if="saved && saved.length" class="df-address-picker__saved">
      <span class="df-address-picker__saved-label">常用地址</span>
      <button v-for="(s, i) in saved" :key="i" type="button" class="df-address-picker__saved-item" @click="useSaved(s)">
        {{ addressTextOf(s) }}
      </button>
      <button v-if="manageable" type="button" class="df-address-picker__saved-manage" @click="emit('open-book')">
        管理地址簿
      </button>
    </div>

    <div v-if="amapReady" class="df-address-picker__region">
      <button
        type="button"
        class="df-address-picker__region-col"
        :class="{ 'is-open': openColumn === 'province' }"
        @click="toggleColumn('province')"
      >
        <template v-if="modelValue.province">{{ modelValue.province }}</template>
        <template v-else><span class="is-placeholder">选择省</span></template>
        <span class="df-address-picker__region-caret">▾</span>
      </button>
      <button
        type="button"
        class="df-address-picker__region-col"
        :class="{ 'is-open': openColumn === 'city' }"
        :disabled="!modelValue.province"
        @click="toggleColumn('city')"
      >
        <template v-if="modelValue.city">{{ modelValue.city }}</template>
        <template v-else><span class="is-placeholder">选择市</span></template>
        <span class="df-address-picker__region-caret">▾</span>
      </button>
      <button
        type="button"
        class="df-address-picker__region-col"
        :class="{ 'is-open': openColumn === 'district' }"
        :disabled="!modelValue.city"
        @click="toggleColumn('district')"
      >
        <template v-if="modelValue.district">{{ modelValue.district }}</template>
        <template v-else><span class="is-placeholder">选择区</span></template>
        <span class="df-address-picker__region-caret">▾</span>
      </button>
    </div>

    <div v-if="openColumn" class="df-address-picker__panel">
      <span v-if="districtLoading" class="df-address-picker__panel-tip">加载中…</span>
      <button
        v-for="d in openColumn === 'province' ? provinces : openColumn === 'city' ? cities : districts"
        :key="d.adcode || d.name"
        type="button"
        class="df-address-picker__panel-option"
        @click="openColumn === 'province' ? selectProvince(d) : openColumn === 'city' ? selectCity(d) : selectDistrict(d)"
      >
        {{ d.name }}
      </button>
    </div>

    <div class="df-address-picker__areas">
      <span class="df-address-picker__areas-label">片区（自动创建）</span>
      <span v-if="!modelValue.areaName" class="df-address-picker__areas-hint">未选择片区（根据地图位置自动创建）</span>
      <span v-else class="df-address-picker__area is-active">{{ modelValue.areaName }}</span>
    </div>

    <div class="df-address-picker__detail">
      <input
        :value="modelValue.detail"
        type="text"
        placeholder="详细地址：道路/小区/楼栋门牌，如 梧桐苑 3 栋 2 单元 501"
        @input="onDetailInput"
      />
      <!-- 先定位标记，再打开地图选点 -->
      <button v-if="locatable" type="button" class="df-address-picker__locate" :disabled="locating" @click="markCurrentPosition">
        <svg class="df-address-picker__locate-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/>
          <circle cx="12" cy="10" r="3"/>
        </svg>
        {{ locating ? '定位中…' : '标记位置' }}
      </button>
      <button v-if="locatable && !locating && amapReady" type="button" class="df-address-picker__map" @click="openMapSelector">
        打开地图选择
      </button>
    </div>
    <p v-if="locateError" class="df-address-picker__error">{{ locateError }}</p>
  </div>

  <!-- 地图选择器弹窗：近全屏大图，中心点标固定、拖动地图选点（与管理端站点选点一致） -->
  <van-dialog 
    v-model:show="mapVisible" 
    close-on-click-overlay 
    :show-confirm-button="true" 
    confirm-text="确定位置"
    :size="'92%'"
    @confirm="confirmMapSelection" 
    title="在地图上拖动选择位置">
    <div class="df-address-picker__map-container">
      <div id="map-selector" class="df-address-picker__map-view" />
      <div class="df-address-picker__map-info">
        <span>{{ mapInfoText }}</span>
      </div>
      <div class="df-address-picker__map-hint">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="18,15 12,9 6,15" />
        </svg>
        <span>拖动地图，将中心点标对准目标位置</span>
      </div>
    </div>
  </van-dialog>
</template>

<style scoped lang="scss">
@use '../styles/tokens.scss' as *;

.df-address-picker {
  display: flex;
  flex-direction: column;
  gap: $df-space-sm;

  &__saved {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: $df-space-sm;

    &-label {
      font-size: $df-font-size-sm;
      color: var(--df-text-secondary);
    }

    &-item {
      max-width: 100%;
      padding: $df-space-xs $df-space-md;
      border: 1px dashed var(--df-border-color);
      border-radius: $df-radius-round;
      background: var(--df-bg-card);
      font-size: $df-font-size-sm;
      color: var(--df-text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      cursor: pointer;
    }

    /* 地址簿管理入口：宿主页跳转地址簿（预约/收货统一取用） */
    &-manage {
      margin-left: auto;
      padding: $df-space-xs $df-space-md;
      border: 1px solid var(--df-color-primary);
      border-radius: $df-radius-round;
      background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
      color: var(--df-color-primary);
      font-size: $df-font-size-sm;
      font-weight: 600;
      white-space: nowrap;
      cursor: pointer;
      transition: all 0.2s ease;

      &:hover {
        background: var(--df-color-primary);
        color: var(--df-on-accent);
      }
    }
  }

  &__region {
    display: flex;
    gap: $df-space-sm;

    &-col {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: $df-space-xs;
      padding: $df-space-sm $df-space-md;
      border: 1px solid var(--df-border-color);
      border-radius: $df-radius-md;
      background: var(--df-bg-card);
      font-size: $df-font-size-sm;
      color: var(--df-text-primary);
      cursor: pointer;

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }

      &.is-open {
        border-color: var(--df-color-primary);
        color: var(--df-color-primary-dark);
      }
    }

    &-caret {
      font-size: 10px;
      color: $df-text-placeholder;
    }

    .is-placeholder {
      color: $df-text-placeholder;
    }
  }

  &__panel {
    display: flex;
    flex-wrap: wrap;
    gap: $df-space-sm;
    padding: $df-space-sm;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-card);
    max-height: 200px;
    overflow-y: auto;

    &-tip {
      font-size: $df-font-size-sm;
      color: var(--df-text-placeholder);
    }

    &-option {
      padding: $df-space-xs $df-space-md;
      border: 1px solid var(--df-border-color);
      border-radius: $df-radius-round;
      background: var(--df-bg-page);
      font-size: $df-font-size-sm;
      color: var(--df-text-regular);
      cursor: pointer;

      &:active {
        border-color: var(--df-color-primary);
        color: var(--df-color-primary-dark);
      }
    }
  }

  &__areas {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: $df-space-sm;

    &-label {
      font-size: $df-font-size-sm;
      color: var(--df-text-secondary);
    }
  }

  &__area {
    padding: $df-space-xs $df-space-md;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-round;
    background: var(--df-bg-card);
    font-size: $df-font-size-sm;
    color: var(--df-text-regular);
    cursor: pointer;

    &.is-active {
      border-color: var(--df-color-primary);
      background: var(--df-color-primary-light);
      color: var(--df-color-primary-dark);
    }
  }

  &__detail {
    display: flex;
    gap: $df-space-sm;

    input {
      flex: 1;
      min-width: 0;
      padding: $df-space-sm $df-space-md;
      border: 1px solid var(--df-border-color);
      border-radius: $df-radius-md;
      font-size: $df-font-size-base;
      color: var(--df-text-primary);

      &::placeholder { color: var(--df-text-placeholder); }
      &:focus { outline: none; border-color: var(--df-color-primary); }
    }
  }

  &__locate {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
    padding: 0 $df-space-md;
    border: none;
    border-radius: $df-radius-md;
    background: var(--df-color-primary-light);
    color: var(--df-color-primary-dark);
    font-size: $df-font-size-sm;
    cursor: pointer;
    flex-shrink: 0;
  }

  &__locate-icon {
    width: 14px;
    height: 14px;
    flex-shrink: 0;
  }

  &__error {
    margin: 0;
    font-size: $df-font-size-sm;
    color: $df-color-danger;
  }

  // ---------------- 地图选择器样式 ----------------
  &__map-container {
    position: relative;
    height: 58vh;
    min-height: 480px;
    background: var(--df-bg-page);
    border-radius: $df-radius-md;
    overflow: hidden;
  }

  &__map-view {
    width: 100%;
    height: 100%;
    min-height: 480px;
  }

  &__map-hint {
    position: absolute;
    bottom: 12px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    padding: $df-space-sm $df-space-md;
    background: rgba(0, 0, 0, 0.6);
    border-radius: $df-radius-round;
    font-size: $df-font-size-sm;
    color: #ffffff;
    z-index: 100;
    white-space: nowrap;

    svg {
      width: 16px;
      height: 16px;
      flex-shrink: 0;
    }

    span {
      white-space: nowrap;
    }
  }

  // CSS 中心固定标记（红大头针，尖端对准地图中心）
  &__center-pin {
    pointer-events: none;
    z-index: 100;
  }

  &__map-info {
    position: absolute;
    top: 12px;
    left: 12px;
    padding: 8px 12px;
    background: rgba(0, 0, 0, 0.7); // 降低透明度，更柔和
    border-radius: $df-radius-sm;
    font-size: 12px;
    color: #ffffff;
    z-index: 100;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }
}
</style>