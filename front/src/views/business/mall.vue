<script setup lang="ts">
// 积分商城管理（C3 对接真实契约）：商品 CRUD/上下架/库存调整 + 兑换单发货/完成
// U2：商品图 el-upload 上传（/file/upload?biz=product）+ 列表封面缩略列
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/request'
import {
  pageMallProducts, saveMallProduct, changeMallProductStatus, adjustMallStock,
  pageMallExchanges, shipExchange, completeExchange,
  pageFleaItems, createFleaItem, forbiddenFleaItem,
  EXCHANGE_STATUS_TEXT,
  type MallProduct, type MallProductSave, type FleaItem, type FleaItemCreate,
} from '@/api/business'

const activeTab = ref('products')
const productTable = ref<{ refresh: () => void }>()
const exchangeTable = ref<{ refresh: () => void }>()

const exchangeTagType: Record<number, 'warning' | 'primary' | 'success' | 'info'> = {
  0: 'warning', 1: 'primary', 2: 'success', 3: 'info',
}

// ---- 商品新增/编辑 ----
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<MallProductSave>({ productName: '', pointsPrice: 100, stock: 0, status: 0, description: '' })

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: undefined, productName: '', coverUrl: '', pointsPrice: 100, stock: 0, status: 0, description: '' })
  dialogVisible.value = true
}

function openEdit(row: MallProduct) {
  isEdit.value = true
  // 编辑不提交库存（后端库存走独立调整接口，防覆盖并发扣减）
  Object.assign(form, {
    id: row.id, productName: row.productName, coverUrl: row.coverUrl ?? '',
    pointsPrice: row.pointsPrice, stock: undefined, status: row.status, description: row.description ?? '',
  })
  dialogVisible.value = true
}

// ---- 商品图上传（U2：走项目 request 封装，上传成功回填 form.coverUrl） ----
async function uploadCover(options: { file: File }) {
  const fd = new FormData()
  fd.append('file', options.file)
  const r = await request.post<{ url?: string } | string>('/file/upload?biz=product', fd, { allowDuplicate: true })
  form.coverUrl = typeof r === 'string' ? r : r.url ?? ''
  if (form.coverUrl) ElMessage.success('商品图已上传')
}

function beforeCoverUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return false
  }
  return true
}

async function submitProduct() {
  if (!form.productName || !form.pointsPrice) {
    ElMessage.warning('商品名与所需积分必填')
    return
  }
  saving.value = true
  try {
    await saveMallProduct({ ...form })
    ElMessage.success(isEdit.value ? '商品已更新' : '商品已创建')
    dialogVisible.value = false
    productTable.value?.refresh()
  } finally {
    saving.value = false
  }
}

// ---- 上下架 / 库存调整 ----
async function toggleStatus(row: MallProduct) {
  const onSale = row.status !== 1
  await ElMessageBox.confirm(`确认${onSale ? '上架' : '下架'}「${row.productName}」？`, '提示', { type: 'warning' })
  await changeMallProductStatus(row.id, onSale)
  ElMessage.success(onSale ? '已上架' : '已下架')
  productTable.value?.refresh()
}

async function adjustStock(row: MallProduct) {
  const { value } = await ElMessageBox.prompt(
    `当前库存 ${row.stock}，输入调整量（正数入库 / 负数核减，禁止负库存）`,
    `库存调整 · ${row.productName}`,
    { inputPattern: /^-?[1-9]\d*$/, inputErrorMessage: '请输入非零整数' },
  )
  await adjustMallStock(row.id, Number(value))
  ElMessage.success('库存已调整')
  productTable.value?.refresh()
}

// ---- 兑换单发货/完成 ----
async function ship(id: string) {
  await shipExchange(id)
  ElMessage.success('已发货')
  exchangeTable.value?.refresh()
}

async function complete(id: string) {
  await completeExchange(id)
  ElMessage.success('已完成')
  exchangeTable.value?.refresh()
}

// ---- 闲置好物（跳蚤市场并入积分商城：V53 停用 209 菜单，列表/发布/违规下架移入本页） ----
const fleaTable = ref<{ refresh: () => void }>()
const fleaTagType: Record<number, 'info' | 'success' | 'warning' | 'danger'> = {
  0: 'info', 1: 'success', 2: 'info', 3: 'danger',
}

const FLEA_CATEGORIES = [
  { code: 'REUSABLE', name: '可回收再利用' },
  { code: 'TOY', name: '玩具' },
  { code: 'BOOK', name: '书籍' },
  { code: 'ELECTRONIC', name: '电子产品' },
  { code: 'HOME', name: '家居' },
  { code: 'OTHER', name: '其他' },
] as const

const fleaPublishVisible = ref(false)
const fleaPublishing = ref(false)
const fleaForm = reactive<FleaItemCreate>({ title: '', description: '', category: 'REUSABLE', price: 100, imageUrl: '' })

function openFleaPublish() {
  Object.assign(fleaForm, { title: '', description: '', category: 'REUSABLE', price: 100, imageUrl: '' })
  fleaPublishVisible.value = true
}

// ---- 实物图上传（卖家=当前管理员账号，C2C 积分托管交易） ----
async function uploadFleaCover(options: { file: File }) {
  const fd = new FormData()
  fd.append('file', options.file)
  const r = await request.post<{ url?: string } | string>('/file/upload?biz=flea', fd, { allowDuplicate: true })
  fleaForm.imageUrl = typeof r === 'string' ? r : r.url ?? ''
  if (fleaForm.imageUrl) ElMessage.success('封面已上传')
}

function beforeFleaCoverUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return false
  }
  return true
}

async function submitFleaPublish() {
  if (!fleaForm.title || !fleaForm.description || !fleaForm.price) {
    ElMessage.warning('标题、描述与积分定价必填')
    return
  }
  fleaPublishing.value = true
  try {
    await createFleaItem({ ...fleaForm })
    ElMessage.success('实物已发布，用户端「闲置好物」栏可兑换')
    fleaPublishVisible.value = false
    fleaTable.value?.refresh()
  } finally {
    fleaPublishing.value = false
  }
}

async function forbiddenFlea(row: FleaItem) {
  await ElMessageBox.confirm(
    `确认将「${row.title}」违规下架？将通知卖家（${row.sellerNickname}），下架后不可恢复上架`,
    `违规下架 · ${row.title}`,
    { type: 'warning', confirmButtonText: '确认下架' },
  )
  await forbiddenFleaItem(row.id)
  ElMessage.success('已违规下架并通知卖家')
  fleaTable.value?.refresh()
}
</script>

<template>
  <div class="app-page">
    <el-tabs v-model="activeTab">
      <!-- 商品管理 -->
      <el-tab-pane label="商品管理" name="products">
        <TablePro ref="productTable" :fetcher="pageMallProducts">
          <template #search="{ query }">
            <el-form-item label="关键词">
              <el-input v-model="query.keyword" placeholder="商品名" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
                <el-option label="上架" :value="1" />
                <el-option label="下架" :value="0" />
              </el-select>
            </el-form-item>
          </template>
          <template #toolbar>
            <el-button v-permission="'business:mall:add'" type="primary" icon="Plus" @click="openCreate">新增商品</el-button>
          </template>
          <el-table-column label="封面" width="80">
            <template #default="{ row }">
              <el-image
                v-if="row.coverUrl" :src="row.coverUrl" :preview-src-list="[row.coverUrl]"
                fit="cover" style="width:48px;height:48px;border-radius:6px"
              />
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="商品名称" min-width="180" />
          <el-table-column label="兑换积分" width="110">
            <template #default="{ row }">
              <span style="color: #e8bc3f; font-weight: 600">{{ row.pointsPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="stock" label="库存" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'business:mall:edit'" link type="primary" @click="openEdit(row as MallProduct)">编辑</el-button>
              <el-button v-permission="'business:mall:edit'" link type="warning" @click="adjustStock(row as MallProduct)">库存</el-button>
              <el-button
                v-permission="'business:mall:edit'" link
                :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row as MallProduct)"
              >
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>

      <!-- 兑换订单 -->
      <el-tab-pane label="兑换订单" name="exchanges">
        <TablePro ref="exchangeTable" :fetcher="pageMallExchanges">
          <template #search="{ query }">
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
                <el-option v-for="(text, val) in EXCHANGE_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
              </el-select>
            </el-form-item>
          </template>
          <el-table-column prop="exchangeNo" label="兑换单号" width="190" />
          <el-table-column prop="productName" label="商品" min-width="150" />
          <el-table-column prop="quantity" label="数量" width="70" />
          <el-table-column prop="pointsCost" label="扣减积分" width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="exchangeTagType[row.status] ?? 'info'">{{ EXCHANGE_STATUS_TEXT[row.status] ?? row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="address" label="收货地址" min-width="160" show-overflow-tooltip />
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 0" v-permission="'business:mall:edit'" link type="primary" @click="ship(row.id)">发货</el-button>
              <el-button v-if="row.status === 1" v-permission="'business:mall:edit'" link type="success" @click="complete(row.id)">完成</el-button>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>

      <!-- 闲置好物（跳蚤市场并入积分商城，V53） -->
      <el-tab-pane label="闲置好物" name="flea">
        <TablePro ref="fleaTable" :fetcher="pageFleaItems">
          <template #search="{ query }">
            <el-form-item label="标题">
              <el-input v-model="query.keyword" placeholder="商品标题关键字" clearable style="width: 200px" />
            </el-form-item>
            <el-form-item label="用户 ID">
              <el-input v-model="query.userId" placeholder="卖家用户 ID" clearable style="width: 160px" />
            </el-form-item>
          </template>
          <template #toolbar>
            <el-button v-permission="'business:flea:list'" type="primary" icon="Plus" @click="openFleaPublish">发布实物</el-button>
          </template>
          <el-table-column label="商品" min-width="240">
            <template #default="{ row }">
              <div style="display: flex; align-items: center; gap: 8px">
                <el-image
                  v-if="row.imageUrl" :src="row.imageUrl" fit="cover" lazy
                  style="width: 44px; height: 44px; border-radius: 6px; flex-shrink: 0"
                  :preview-src-list="[row.imageUrl]" preview-teleported
                />
                <el-image v-else style="width: 44px; height: 44px; border-radius: 6px" fit="cover"
                          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 44 44'%3E%3Crect width='44' height='44' fill='%23f2f4f7'/%3E%3C/svg%3E" />
                <div style="min-width: 0">
                  <div class="ellipsis" :title="row.title">{{ row.title }}</div>
                  <div style="color: var(--el-text-color-secondary); font-size: 12px; margin-top: 2px">
                    {{ row.description?.slice(0, 40) }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="110">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.category }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="价格(积分)" width="110" align="right">
            <template #default="{ row }">
              <span style="color: #e8bc3f; font-weight: 600">{{ row.price }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sellerNickname" label="卖家" width="110" show-overflow-tooltip />
          <el-table-column prop="userId" label="用户 ID" width="130" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="fleaTagType[row.status] ?? 'info'">{{ row.statusDesc }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="发布时间" width="170" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 1"
                v-permission="'business:flea:forbidden'"
                link type="danger" @click="forbiddenFlea(row as FleaItem)"
              >违规下架</el-button>
              <span v-else style="color: var(--el-text-color-placeholder)">—</span>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>
    </el-tabs>

    <!-- 商品新增/编辑弹窗 -->
    <FormDialog v-model="dialogVisible" :title="isEdit ? '编辑商品' : '新增商品'" :loading="saving" @confirm="submitProduct">
      <el-form :model="form" label-width="90px">
        <el-form-item label="商品名称" required>
          <el-input v-model="form.productName" maxlength="50" placeholder="如：环保帆布袋" />
        </el-form-item>
        <el-form-item label="所需积分" required>
          <el-input-number v-model="form.pointsPrice" :min="1" :max="1000000" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="初始库存">
          <el-input-number v-model="form.stock" :min="0" :max="100000" />
        </el-form-item>
        <el-form-item label="上架状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
        <el-form-item label="商品图">
          <el-upload
            :show-file-list="false" accept="image/*"
            :before-upload="beforeCoverUpload" :http-request="uploadCover"
          >
            <el-image
              v-if="form.coverUrl" :src="form.coverUrl" fit="cover"
              style="width:64px;height:64px;border-radius:8px;cursor:pointer"
            />
            <el-button v-else type="primary" plain icon="Upload">选择图片</el-button>
          </el-upload>
          <el-button v-if="form.coverUrl" link type="danger" @click="form.coverUrl = ''">清除</el-button>
          <div class="el-upload__tip">支持 jpg/png 等图片，≤5MB；上传后自动回填 URL</div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
    </FormDialog>

    <!-- 发布实物弹窗（闲置好物） -->
    <FormDialog v-model="fleaPublishVisible" title="发布实物" :loading="fleaPublishing" @confirm="submitFleaPublish">
      <el-form :model="fleaForm" label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="fleaForm.title" maxlength="64" placeholder="如：九成新空气炸锅" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="fleaForm.category" style="width: 220px">
            <el-option v-for="c in FLEA_CATEGORIES" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="fleaForm.description" type="textarea" :rows="3" maxlength="500" placeholder="成色、购入时间、转让原因等" />
        </el-form-item>
        <el-form-item label="积分定价" required>
          <el-input-number v-model="fleaForm.price" :min="1" :max="1000000" />
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
            :show-file-list="false" accept="image/*"
            :before-upload="beforeFleaCoverUpload" :http-request="uploadFleaCover"
          >
            <el-image
              v-if="fleaForm.imageUrl" :src="fleaForm.imageUrl" fit="cover"
              style="width:64px;height:64px;border-radius:8px;cursor:pointer"
            />
            <el-button v-else type="primary" plain icon="Upload">选择图片</el-button>
          </el-upload>
          <el-button v-if="fleaForm.imageUrl" link type="danger" @click="fleaForm.imageUrl = ''">清除</el-button>
          <div class="el-upload__tip">支持 jpg/png 等图片，≤5MB；发布后展示在用户端闲置好物栏</div>
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<style scoped lang="scss">
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}
</style>
