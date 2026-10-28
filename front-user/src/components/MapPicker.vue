<script setup lang="ts">
// ============================================================
// 地图选点组件（onsite04 新建，纯展示+交互；onsite02 按需集成不强制）
// 能力：地址搜索（geocode）→ 地图展示 → 点击地图选点（逆地理回填地址）
// 降级：mapReady=false → 「地图未配置」占位 + 手动经纬度/地址输入
// ============================================================
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { showToast } from 'vant'
import { geocode, regeo } from '@/api/map'
import { createMap, destroyMap, ensureAmap, mapReady, addMarker } from '@/composables/useAmap'

export interface MapPoint {
  lng: number
  lat: number
  address: string
}

const props = defineProps<{
  modelValue: MapPoint | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: MapPoint | null]
}>()

const mapEl = ref<HTMLDivElement | null>(null)
const keyword = ref('')
const manualLng = ref('')
const manualLat = ref('')
const manualAddress = ref('')
const searching = ref(false)

let mapHandle: ReturnType<typeof createMap> | null = null
let marker: unknown = null

/** 中心点默认杭州（演示域，与站点种子一致） */
const DEFAULT_CENTER: [number, number] = [120.1536, 30.2875]

function emitPoint(p: MapPoint) {
  emit('update:modelValue', p)
}

async function search() {
  const kw = keyword.value.trim()
  if (!kw) {
    showToast('请输入地址')
    return
  }
  searching.value = true
  try {
    const r = await geocode(kw)
    if (!r) {
      showToast('未找到该地址')
      return
    }
    const point: MapPoint = { lng: r.lng, lat: r.lat, address: r.formattedAddress ?? kw }
    emitPoint(point)
    renderMap(point, false)
  } catch {
    // 5001/网络错误：createRequest 已 Toast，此处保持现状
  } finally {
    searching.value = false
  }
}

/** 地图中心跳转 + 打点 */
function renderMap(point: MapPoint, fitView: boolean) {
  if (!mapHandle || !mapEl.value) return
  if (marker) {
    ;(mapHandle.map as { remove: (o: unknown) => void }).remove(marker)
  }
  marker = addMarker(mapHandle, [point.lng, point.lat], {
    title: point.address,
    animation: 'AMAP_ANIMATION_DROP',
  })
  ;(mapHandle.map as { setZoomAndCenter: (z: number, c: unknown) => void }).setZoomAndCenter(15, [point.lng, point.lat])
  if (fitView) {
    ;(mapHandle.map as { setFitView: () => void }).setFitView()
  }
}

/** 点击地图选点：更新坐标 + 逆地理回填地址 */
function onMapClick(e: { lnglat: { lng: number; lat: number } }) {
  const { lng, lat } = e.lnglat
  const point: MapPoint = { lng, lat, address: props.modelValue?.address ?? '' }
  emitPoint(point)
  renderMap(point, false)
  regeo(lng, lat)
    .then((r) => {
      if (r?.formattedAddress) {
        emitPoint({ ...point, address: r.formattedAddress })
      }
    })
    .catch(() => {
      // 逆地理失败不阻塞选点（address 保持原值）
    })
}

/** 手动输入（降级模式） */
function applyManual() {
  const lng = Number(manualLng.value)
  const lat = Number(manualLat.value)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    showToast('请输入有效经纬度')
    return
  }
  emitPoint({ lng, lat, address: manualAddress.value.trim() || `${lng},${lat}` })
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      manualLng.value = String(v.lng)
      manualLat.value = String(v.lat)
      manualAddress.value = v.address ?? ''
    }
  },
  { immediate: true },
)

onMounted(async () => {
  const ok = await ensureAmap()
  if (!ok || !mapEl.value) return
  const center = props.modelValue
    ? ([props.modelValue.lng, props.modelValue.lat] as [number, number])
    : DEFAULT_CENTER
  mapHandle = createMap(mapEl.value, center, 14)
  // 地图点击选点
  ;(mapHandle.map as { on: (ev: string, cb: (e: { lnglat: { lng: number; lat: number } }) => void) => void }).on(
    'click',
    onMapClick,
  )
  if (props.modelValue) {
    renderMap(props.modelValue, false)
  }
})

onUnmounted(() => {
  if (mapEl.value) {
    destroyMap(mapEl.value)
    mapHandle = null
  }
})
</script>

<template>
  <div class="map-picker">
    <!-- 地址搜索 -->
    <div class="map-picker__search">
      <van-field
        v-model="keyword"
        placeholder="输入地址搜索定位"
        clearable
        @keyup.enter="search"
      >
        <template #button>
          <van-button size="small" type="primary" :loading="searching" @click="search">搜索</van-button>
        </template>
      </van-field>
    </div>

    <!-- 真实地图（就绪时） -->
    <div v-if="mapReady" ref="mapEl" class="map-picker__map" />

    <!-- 降级：地图未配置 -->
    <div v-else class="map-picker__fallback">
      <div class="map-picker__fallback-tip">地图未配置，可手动输入经纬度/地址</div>
      <van-field v-model="manualLng" label="经度" placeholder="如 120.1551" />
      <van-field v-model="manualLat" label="纬度" placeholder="如 30.2741" />
      <van-field v-model="manualAddress" label="地址" placeholder="选点地址（可留空）" />
      <van-button block type="primary" @click="applyManual">确认位置</van-button>
    </div>

    <!-- 当前选中点摘要 -->
    <div v-if="modelValue" class="map-picker__summary">
      <div class="map-picker__summary-addr">{{ modelValue.address }}</div>
      <div class="map-picker__summary-lnglat">
        {{ modelValue.lng.toFixed(6) }}, {{ modelValue.lat.toFixed(6) }}
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.map-picker {
  &__search {
    margin-bottom: $df-space-sm;
  }

  &__map {
    width: 100%;
    height: 320px;
    border-radius: $df-radius-md;
    overflow: hidden;
  }

  &__fallback {
    padding: $df-space-md;
    border: 1px dashed var(--df-border-color);
    border-radius: $df-radius-md;
  }

  &__fallback-tip {
    margin-bottom: $df-space-sm;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__summary {
    margin-top: $df-space-sm;
    padding: $df-space-sm $df-space-md;
    background: var(--df-bg-page);
    border-radius: $df-radius-md;
  }

  &__summary-addr {
    font-weight: 500;
    font-size: $df-font-size-base;
  }

  &__summary-lnglat {
    margin-top: 2px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }
}
</style>
