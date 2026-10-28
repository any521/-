<script setup lang="ts">
// ============================================================
// 站点管理（V43）：管理员地图选点设置垃圾站点，用户端据此展示最近站点与最优路线
// 高德 JS API 2.0：中心点标固定不动，拖动地图使点标对准目标位置 →
// moveend 时以地图中心回填 lng/lat（6 位小数）+ 逆地理回填地址
// key 优先取环境变量，缺省走后端 /api/map/config 下发（系统-地图配置页热更新）
// ============================================================
import { nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageStations, removeStation, saveStation, updateStation, type StationRow, type StationSave, type StationType } from '@/api/business'
import type { PageQuery } from '@shared/http/types'
import { request } from '@/api/request'

// ---- 站点表单 ----
const stationDialog = ref(false)
const saving = ref(false)
const stationsRef = ref<{ refresh: () => void }>()
const form = reactive({
  id: undefined as string | undefined,
  name: '',
  stationType: 'KITCHEN' as StationType,
  lng: undefined as number | undefined,
  lat: undefined as number | undefined,
  address: '',
  openHours: '',
  status: 1,
})

// ---- 营业时间（上下滑动选择开始-结束，与 form.openHours 字符串同步） ----
const openHoursRange = ref<[string, string] | null>(null)
watch(openHoursRange, (v) => {
  form.openHours = v ? `${v[0]}-${v[1]}` : ''
})

// ---- 地图选点（高德 JS API 2.0）：中心点标固定，拖动地图选点 ----
let mapInstance: any = null
const locating = ref(false)

async function ensureAmap(): Promise<boolean> {
  if ((window as unknown as { AMap?: unknown }).AMap) return true
  try {
    // key 优先环境变量；未配置时从 /api/map/config 拉取（管理端「系统-地图配置」维护）
    let webKey = import.meta.env.VITE_AMAP_WEB_KEY
    let securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE
    if (!webKey) {
      const cfg = await request.get<{ webKey?: string; securityCode?: string }>('/map/config')
      webKey = cfg?.webKey ?? ''
      if (!securityCode) securityCode = cfg?.securityCode ?? ''
    }
    if (!webKey) return false
    const w = window as unknown as { _AMapSecurityConfig?: { securityJsCode?: string } }
    if (securityCode) w._AMapSecurityConfig = { securityJsCode: securityCode }
    await loadScript(`https://webapi.amap.com/maps?v=2.0&key=${webKey}&plugin=AMap.Geocoder,AMap.Geolocation`)
    return !!(window as unknown as { AMap?: unknown }).AMap
  } catch {
    return false
  }
}

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

/** 初始化编辑器地图（弹窗打开后调用）：中心点标固定，拖动地图使点标对准目标位置 */
async function initEditorMap() {
  if (!(await ensureAmap()) || !(window as unknown as { AMap?: unknown }).AMap) {
    ElMessage.warning('高德地图不可用，请先在「系统-地图配置」填写高德 Web Key')
    return
  }
  const AMap = (window as unknown as { AMap: any }).AMap
  const container = document.getElementById('map-editor')
  if (!container) return

  // 销毁旧实例（编辑/重复打开）
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }

  const center: [number, number] = form.lng && form.lat ? [Number(form.lng), Number(form.lat)] : [120.1536, 30.2875]
  mapInstance = new AMap.Map('map-editor', { zoom: 15, center, scrollWheel: true, dragEnable: true })

  // 初始化即回填当前中心坐标（新增：默认中心；编辑：已有坐标）
  await placeCenter()
  // 拖动/缩放地图结束：以地图中心作为选点坐标回填
  mapInstance.on('moveend', placeCenter)
}

/** 以地图中心作为选点：回填经纬度 + 逆地理地址 */
async function placeCenter() {
  if (!mapInstance) return
  const lnglat = mapInstance.getCenter()
  const lng = Number(lnglat.getLng().toFixed(6))
  const lat = Number(lnglat.getLat().toFixed(6))
  form.lng = lng
  form.lat = lat
  try {
    const AMap = (window as unknown as { AMap: any }).AMap
    const geocoder = new AMap.Geocoder()
    const addr = await new Promise<any>((res) =>
      geocoder.getAddress([lng, lat], (status: string, result: any) => res(status === 'complete' ? result : {})),
    )
    form.address = addr?.regeocode?.formattedAddress ?? ''
  } catch {
    /* 逆地理失败不影响选点 */
  }
}

/** 定位到当前位置（高德 Geolocation 优先，navigator.geolocation 兜底） */
function locateMe(): Promise<[number, number]> {
  const AMap = (window as unknown as { AMap?: any }).AMap
  if (AMap?.Geolocation) {
    return new Promise((resolve, reject) => {
      const geo = new AMap.Geolocation({ enableHighAccuracy: true, timeout: 10000, zoomToAccuracy: false })
      try {
        geo.getCurrentPosition((status: string, result: any) => {
          const p = result?.position as { getLng?: () => number; getLat?: () => number; lng?: number; lat?: number } | undefined
          if (status === 'complete' && p) {
            const lng = typeof p.getLng === 'function' ? p.getLng() : (p.lng as number | undefined)
            const lat = typeof p.getLat === 'function' ? p.getLat() : (p.lat as number | undefined)
            if (typeof lng === 'number' && typeof lat === 'number') resolve([lng, lat])
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
      (p) => resolve([p.coords.longitude, p.coords.latitude]),
      () => reject(new Error('定位失败')),
      { timeout: 8000 },
    )
  })
}

/** 定位按钮：地图中心移动到当前位置（中心点标即对准），并回填坐标 */
async function locateToCurrent() {
  if (!mapInstance || locating.value) return
  locating.value = true
  try {
    const [lng, lat] = await locateMe()
    mapInstance.setCenter([lng, lat])
    await placeCenter()
  } catch {
    ElMessage.warning('定位失败，请检查浏览器定位权限')
  } finally {
    locating.value = false
  }
}

/** 一键填入表单：将当前地图中心（点标位置）坐标 + 逆地理地址回填到左侧表单 */
async function fillForm() {
  if (!mapInstance) return
  await placeCenter()
  ElMessage.success('已将当前点标位置填入表单')
}

/** 弹窗关闭后销毁地图实例，避免残留 */
function onDialogClosed() {
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', stationType: 'KITCHEN', lng: undefined, lat: undefined, address: '', openHours: '', status: 1 })
  openHoursRange.value = null
  stationDialog.value = true
  // el-dialog 打开动画后容器才有尺寸，稍候初始化地图
  nextTick(() => setTimeout(initEditorMap, 100))
}

function openEdit(row: StationRow) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    stationType: row.stationType,
    lng: row.lng,
    lat: row.lat,
    address: row.address || '',
    openHours: row.openHours || '',
    status: row.status,
  })
  // 解析「HH:mm-HH:mm」为时间范围；格式不符时留空待选
  const parts = (row.openHours ?? '').split('-')
  const start = parts[0]?.trim()
  const end = parts[1]?.trim()
  openHoursRange.value =
    parts.length === 2 &&
    start &&
    end &&
    /^\d{2}:\d{2}$/.test(start) &&
    /^\d{2}:\d{2}$/.test(end)
      ? [start, end]
      : null
  stationDialog.value = true
  nextTick(() => setTimeout(initEditorMap, 100))
}

async function submit() {
  if (!form.name || form.lng === undefined || form.lat === undefined) {
    ElMessage.warning('站点名称、经纬度必填（需地图选点）')
    return
  }
  saving.value = true
  try {
    // Number() 强制归一化：后端 StationSaveDTO.status 为 Integer，防止布尔值导致 JSON 反序列化失败
    const data: StationSave = { id: form.id, name: form.name, stationType: form.stationType, lng: form.lng, lat: form.lat, address: form.address || undefined, openHours: form.openHours || undefined, status: Number(form.status) }
    if (form.id) await updateStation(form.id, data)
    else await saveStation(data)
    ElMessage.success(form.id ? '站点已保存' : '站点已创建')
    stationDialog.value = false
    stationsRef.value?.refresh()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: StationRow) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateStation(row.id, { id: row.id, name: row.name, stationType: row.stationType, lng: row.lng, lat: row.lat, address: row.address || undefined, openHours: row.openHours || undefined, status: newStatus })
  ElMessage.success(newStatus === 1 ? '已启用' : '已停用')
  stationsRef.value?.refresh()
}

async function confirmDelete(row: StationRow) {
  await ElMessageBox.confirm(`确认删除站点「${row.name}」？删除后不可恢复`, '删除站点', { type: 'warning' })
  await removeStation(row.id)
  ElMessage.success('已删除')
  stationsRef.value?.refresh()
}

onBeforeUnmount(onDialogClosed)
</script>

<template>
  <div class="app-page" style="display: flex; height: calc(100vh - 80px); overflow: hidden;">
    <!-- 左侧：列表 -->
    <el-card shadow="never" style="flex: 1; margin: 12px; display: flex; flex-direction: column; min-width: 600px;">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span style="font-weight: bold; font-size: 18px;">垃圾站点管理</span>
          <el-button v-permission="'business:station:save'" type="primary" icon="Plus" @click="openCreate">新增站点</el-button>
        </div>
      </template>

      <TablePro ref="stationsRef" :fetcher="pageStations">
        <template #search="{ query }">
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="站点名称模糊搜索" clearable style="width: 200px" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="query.stationType" placeholder="全部" clearable style="width: 120px">
              <el-option label="回收站" value="RECYCLE" />
              <el-option label="垃圾站" value="KITCHEN" />
              <el-option label="集散点" value="COLLECT" />
            </el-select>
          </el-form-item>
        </template>
        <el-table-column prop="name" label="站点名称" width="180" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="(row as StationRow).stationType === 'RECYCLE'" type="success">回收站</el-tag>
            <el-tag v-else-if="(row as StationRow).stationType === 'KITCHEN'" type="danger">垃圾站</el-tag>
            <el-tag v-else type="warning">集散点</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="坐标" width="180">
          <template #default="{ row }">{{ Number((row as StationRow).lng).toFixed(5) }}, {{ Number((row as StationRow).lat).toFixed(5) }}</template>
        </el-table-column>
        <el-table-column prop="openHours" label="营业时间" width="140" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="(row as StationRow).status === 1 ? 'success' : 'info'">{{ (row as StationRow).status === 1 ? '开放' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'business:station:save'" link type="primary" @click="openEdit(row as StationRow)">编辑</el-button>
            <el-button v-permission="'business:station:save'" link type="warning" @click="toggleStatus(row as StationRow)">{{ (row as StationRow).status === 1 ? '停用' : '启用' }}</el-button>
            <el-button v-permission="'business:station:save'" link type="danger" @click="confirmDelete(row as StationRow)">删除</el-button>
          </template>
        </el-table-column>
      </TablePro>
    </el-card>
  </div>

  <!-- 新增/编辑弹窗：左侧表单 + 右侧地图选点 -->
  <el-dialog
    v-model="stationDialog"
    :title="form.id ? '编辑站点' : '新增站点'"
    width="920px"
    :close-on-click-modal="false"
    @closed="onDialogClosed"
  >
    <div style="display: flex; gap: 12px;">
      <el-form :model="form" label-width="80px" style="flex: 1; min-width: 300px;">
        <el-form-item label="站点名称" required>
          <el-input v-model="form.name" placeholder="站点名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.stationType" style="width: 100%">
            <el-option label="回收站 (RECYCLE)" value="RECYCLE" />
            <el-option label="垃圾站 (KITCHEN)" value="KITCHEN" />
            <el-option label="集散点 (COLLECT)" value="COLLECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="经纬度">
          <el-input :value="form.lng && form.lat ? `${Number(form.lng).toFixed(6)}, ${Number(form.lat).toFixed(6)}` : '未选择'" disabled style="color: #999;" />
          <span style="font-size: 12px; color: #909399;">拖动地图，使中心点标对准站点位置</span>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="逆地理填充（可选）" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-time-picker
            v-model="openHoursRange"
            is-range
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="HH:mm"
            value-format="HH:mm"
            step="00:30"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <!-- active-value/inactive-value 保持 0/1 数字：后端 StationSaveDTO.status 为 Integer，el-switch 默认布尔 true/false 会致 JSON 反序列化报错 -->
          <el-switch v-model="form.status" inline-prompt active-text="启用" inactive-text="停用" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <div style="flex: 0 0 500px; border: 1px solid #ddd; border-radius: 8px; overflow: hidden; position: relative;">
        <div id="map-editor" style="width: 100%; height: 400px; background: #f5f5f5;" />
        <!-- 中心固定点标：不随地图移动，尖端所指即选点坐标 -->
        <svg class="map-pin" viewBox="0 0 24 24" width="34" height="34" aria-hidden="true">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5a2.5 2.5 0 1 1 0-5 2.5 2.5 0 0 1 0 5z" fill="#e64340" stroke="#fff" stroke-width="1.5" />
        </svg>
        <!-- 定位到当前位置：地图中心（点标）移动到定位点 -->
        <button class="map-locate" type="button" :disabled="locating" :title="locating ? '定位中…' : '定位到当前位置'" @click="locateToCurrent">
          <svg v-if="!locating" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/>
            <circle cx="12" cy="10" r="3"/>
          </svg>
          <span v-else class="map-locate__loading" />
        </button>
        <!-- 一键填入表单：当前点标位置坐标 + 地址回填左侧表单 -->
        <button class="map-fill" type="button" @click="fillForm">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="20 6 9 17 4 12" />
          </svg>
          填入表单
        </button>
        <div class="map-tip">拖动地图，将中心点标对准站点位置</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="stationDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ form.id ? '保存修改' : '创建站点' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.app-page {
  --df-bg-page: #f5f7fa;
}

/* 中心固定点标：尖端对准地图中心，不随地图移动 */
.map-pin {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -100%);
  z-index: 10;
  pointer-events: none;
  filter: drop-shadow(0 2px 3px rgba(0, 0, 0, 0.3));
}

/* 定位按钮：地图右上角（高德定位控件风格） */
.map-locate {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: var(--df-bg-card);
  color: #409eff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  cursor: pointer;

  svg {
    width: 18px;
    height: 18px;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &__loading {
    width: 16px;
    height: 16px;
    border: 2px solid rgba(64, 158, 255, 0.3);
    border-top-color: #409eff;
    border-radius: 50%;
    animation: map-locate-spin 0.8s linear infinite;
  }
}

@keyframes map-locate-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 一键填入表单：地图右下角 */
.map-fill {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: 16px;
  background: var(--df-bg-card);
  color: #409eff;
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  cursor: pointer;

  svg {
    width: 13px;
    height: 13px;
  }

  &:hover {
    background: #ecf5ff;
  }
}

.map-tip {
  position: absolute;
  left: 50%;
  bottom: 10px;
  transform: translateX(-50%);
  z-index: 10;
  pointer-events: none;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 12px;
  white-space: nowrap;
}
</style>
