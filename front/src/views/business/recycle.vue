<script setup lang="ts">
// 回收订单（B 线 AdminRecycleController）：只读列表 + 详情 / 改派 / 报价配置（M-03 交付）
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pageRecycleOrders, getRecycleOrder, reassignRecycleOrder, listRecyclePrice, saveRecyclePrice,
  adminAreaNameOf,
  type RecycleOrder, type RecycleOrderDetail, type RecyclePrice, type RecyclerOption,
} from '@/api/business'
import { request } from '@/api/request'
import { ORDER_STATUS_TEXT, OrderStatus, type PageResult } from '@shared/http/types'
import { formatMoney, formatWeight } from '@shared/utils/format'
import TablePro from '@/components/TablePro.vue'
import FormDialog from '@/components/FormDialog.vue'

const statusTagType = {
  [OrderStatus.PENDING]: 'warning',
  [OrderStatus.ACCEPTED]: 'primary',
  [OrderStatus.PROCESSING]: 'primary',
  [OrderStatus.FINISHED]: 'success',
  [OrderStatus.CANCELED]: 'info',
  [OrderStatus.ABNORMAL]: 'danger',
} as const

// ---- 详情弹窗（M-03）：仅展示，无写操作 ----
const detailVisible = ref(false)
const detail = ref<RecycleOrderDetail | null>(null)
const detailLoading = ref(false)

async function openDetail(row: RecycleOrder) {
  detailLoading.value = true
  try {
    detail.value = await getRecycleOrder(String(row.id))
    detailVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

// ---- 改派回收员（M-03）：0待接单/1已接单/5异常 可改派，重置 0 后列表刷新 ----
const tableRef = ref<{ refresh: () => void }>()
const reassignVisible = ref(false)
const reassignTarget = ref<RecycleOrder | null>(null)
const recyclerList = ref<RecyclerOption[]>([])
const recyclerId = ref('')
const reassigning = ref(false)

async function openReassign(row: RecycleOrder) {
  reassignTarget.value = row
  recyclerId.value = ''
  // 后端仅过滤 staffType/auditStatus（status 参数忽略），前端再筛在岗（status=1）
  const res = await request.get<PageResult<RecyclerOption & { status?: number }>>('/admin/staff', {
    pageNum: 1, pageSize: 100, staffType: 1, auditStatus: 1, status: 1,
  })
  recyclerList.value = (res.list ?? []).filter((s) => s.status === 1)
  reassignVisible.value = true
}

async function submitReassign() {
  if (!reassignTarget.value || !recyclerId.value) return
  reassigning.value = true
  try {
    // staffId 保持字符串透传：雪花 ID 经 Number() 丢精度会查无此员工，后端报“改派目标不是在岗回收员”
    await reassignRecycleOrder(String(reassignTarget.value.id), recyclerId.value)
    ElMessage.success('改派成功')
    reassignVisible.value = false
    tableRef.value?.refresh()
  } finally {
    reassigning.value = false
  }
}

// ---- 报价配置（M-03）：列表 + 新增/编辑（category 幂等 upsert，编辑时禁用） ----
const priceVisible = ref(false)
const priceList = ref<RecyclePrice[]>([])
const priceLoading = ref(false)
const priceForm = reactive({ id: '', category: '', pricePerKg: 0, status: 1, updateTime: '', city: '' })
const priceDialog = ref(false)
const priceSaving = ref(false)

async function openPrice() {
  priceLoading.value = true
  try {
    priceList.value = await listRecyclePrice()
    priceVisible.value = true
  } finally {
    priceLoading.value = false
  }
}

function openPriceEdit(row?: RecyclePrice) {
  if (row) {
    priceForm.id = row.id
    priceForm.category = row.category
    priceForm.pricePerKg = row.pricePerKg
    priceForm.status = row.status
    // 编辑时展示当前行的时间/地区（新增时为空）
    priceForm.updateTime = row.updateTime ?? ''
    priceForm.city = row.city ?? ''
  } else {
    priceForm.id = ''
    priceForm.category = ''
    priceForm.pricePerKg = 0
    priceForm.status = 1
    priceForm.updateTime = ''
    priceForm.city = ''
  }
  priceDialog.value = true
}

async function submitPrice() {
  priceSaving.value = true
  try {
    await saveRecyclePrice({
      category: priceForm.category,
      pricePerKg: priceForm.pricePerKg,
      status: priceForm.status as 0 | 1,
    })
    ElMessage.success(priceForm.id ? '报价已更新' : '报价已新增')
    priceDialog.value = false
    priceList.value = await listRecyclePrice()
  } finally {
    priceSaving.value = false
  }
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="tableRef" :fetcher="pageRecycleOrders">
      <template #search="{ query }">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="订单号 / 用户" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <!-- 字典库无 order_status，改 el-select 直接取共享常量，与列表状态列同源 -->
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(text, val) in ORDER_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
      </template>
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column prop="userName" label="用户" width="90" />
      <el-table-column prop="areaName" label="片区" width="110" />
      <el-table-column prop="categories" label="品类" width="100" />
      <el-table-column label="重量" width="90">
        <template #default="{ row }">{{ formatWeight(row.weight) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="100">
        <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="减碳(kg)" width="90">
        <template #default="{ row }">{{ row.carbon }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType[row.status as OrderStatus]">
            {{ ORDER_STATUS_TEXT[row.status as OrderStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="staffName" label="回收员" width="90" />
      <el-table-column prop="appointTime" label="预约时间" width="170" />
      <el-table-column prop="createTime" label="下单时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'business:recycle:detail'" link type="primary" @click="openDetail(row as RecycleOrder)">详情</el-button>
          <el-button v-permission="'business:recycle:reassign'" link type="warning"
            :disabled="![0, 1, 5].includes((row as RecycleOrder).status)" @click="openReassign(row as RecycleOrder)">指派</el-button>
          <el-button v-permission="'business:recycle:price'" link type="success" @click="openPrice">报价</el-button>
        </template>
      </el-table-column>
    </TablePro>

    <!-- 订单详情（M-03）：FormDialog 固定取消/确定 footer 不满足「仅关闭」，此处用 el-dialog 仅展示 -->
    <el-dialog v-model="detailVisible" title="回收订单详情" width="680px">
      <template v-if="detail">
        <div v-loading="detailLoading">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.statusDesc }}</el-descriptions-item>
            <el-descriptions-item label="用户 ID">{{ detail.userId }}</el-descriptions-item>
            <el-descriptions-item label="片区">{{ detail.areaName || adminAreaNameOf(detail.areaId) }}</el-descriptions-item>
            <el-descriptions-item label="品类">{{ detail.category }}</el-descriptions-item>
            <el-descriptions-item label="预估重量">{{ formatWeight(detail.estWeight) }}</el-descriptions-item>
            <el-descriptions-item label="实收重量">{{ formatWeight(detail.weight) }}</el-descriptions-item>
            <el-descriptions-item label="件数">{{ detail.quantity }}</el-descriptions-item>
            <el-descriptions-item label="金额">{{ formatMoney(detail.amount) }}</el-descriptions-item>
            <el-descriptions-item label="预约时间">{{ detail.appointTime }}</el-descriptions-item>
            <el-descriptions-item label="接单回收员">{{ detail.staffName ?? '未指派' }}</el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ detail.createTime }}</el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ detail.address }}</el-descriptions-item>
            <el-descriptions-item label="完成时间" :span="2">{{ detail.finishTime ?? '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.repairRef" label="联动维修单" :span="2">{{ detail.repairRef }}</el-descriptions-item>
          </el-descriptions>

          <el-alert v-if="detail.cancelReason" :title="`取消原因：${detail.cancelReason}`" type="warning"
            :closable="false" class="recycle-detail__alert" />
          <el-alert v-if="detail.exceptionReason" :title="`异常原因：${detail.exceptionReason}`" type="error"
            :closable="false" class="recycle-detail__alert" />

          <template v-if="detail.items?.length">
            <h4 class="recycle-detail__sub">实收明细</h4>
            <el-table :data="detail.items" size="small" border>
              <el-table-column prop="category" label="品类" min-width="140" />
              <el-table-column label="重量(kg)" width="120">
                <template #default="{ row }">{{ formatWeight(row.weight) }}</template>
              </el-table-column>
              <el-table-column label="金额(元)" width="120">
                <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
              </el-table-column>
            </el-table>
          </template>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 改派回收员（M-03） -->
    <FormDialog v-model="reassignVisible" title="改派回收员" :loading="reassigning" @confirm="submitReassign">
      <template v-if="reassignTarget">
        <el-descriptions :column="2" border size="small" class="reassign-info">
          <el-descriptions-item label="订单号">{{ reassignTarget.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ ORDER_STATUS_TEXT[reassignTarget.status] }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="!recyclerList.length" title="暂无在岗回收员" type="warning" :closable="false"
          class="reassign-alert" />
        <el-form label-width="80px">
          <el-form-item label="回收员">
            <el-select v-model="recyclerId" filterable placeholder="选择本片区回收员" style="width: 100%">
              <el-option v-for="r in recyclerList" :key="r.id" :label="`${r.staffName} ${r.phone}`" :value="r.id" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>
    </FormDialog>

    <!-- 报价配置（M-03）：列表 + 新增/编辑 -->
    <FormDialog v-model="priceVisible" title="回收报价配置" width="760px" @confirm="priceVisible = false">
      <div class="price-table__toolbar">
        <el-button v-permission="'business:recycle:price'" type="primary" size="small" icon="Plus"
          @click="openPriceEdit()">新增报价</el-button>
      </div>
      <el-table :data="priceList" v-loading="priceLoading" height="320px" border size="small">
        <el-table-column prop="category" label="品类" min-width="140" />
        <el-table-column label="报价(元/kg)" width="140">
          <template #default="{ row }">{{ row.pricePerKg }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '生效' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地区" width="130">
          <template #default="{ row }">{{ row.city || '—' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ row.updateTime?.slice(0, 16) || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPriceEdit(row as RecyclePrice)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 内嵌新增/编辑（category 幂等 upsert，编辑时禁用） -->
      <FormDialog v-model="priceDialog" :title="priceForm.id ? '编辑报价' : '新增报价'" :loading="priceSaving"
        @confirm="submitPrice">
        <el-form label-width="110px">
          <el-form-item label="品类">
            <el-input v-model="priceForm.category" placeholder="如：纸类" :disabled="!!priceForm.id" />
          </el-form-item>
          <el-form-item label="报价(元/kg)">
            <el-input-number v-model="priceForm.pricePerKg" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <template v-if="priceForm.id">
            <el-form-item label="地区">
              <el-input :model-value="priceForm.city || '—'" disabled />
            </el-form-item>
            <el-form-item label="更新时间">
              <el-input :model-value="priceForm.updateTime?.slice(0, 16) || '—'" disabled />
            </el-form-item>
          </template>
          <el-form-item label="状态">
            <el-switch v-model="priceForm.status" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-form>
      </FormDialog>
    </FormDialog>
  </div>
</template>

<style scoped>
.recycle-detail__alert {
  margin-top: 12px;
}

.recycle-detail__sub {
  margin: 14px 0 8px;
}

.reassign-info {
  margin-bottom: 12px;
}

.reassign-alert {
  margin-bottom: 12px;
}

.price-table__toolbar {
  margin-bottom: 10px;
}
</style>
