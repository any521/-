<script setup lang="ts">
// PC 跳蚤市场商品详情（Upgrade U4）：大图 + 卖家信息 + 下单弹层（AddressPicker 必填 + 冻结提示）
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getFleaItem, createFleaOrder, type FleaItem } from '@/api/business'
import { listAddresses } from '@/api/addressBook'
import AddressPicker from '@shared/components/AddressPicker.vue'
import { addressTextOf, type AddressValue } from '@shared/components/address'
import { useUserStore } from '@/store/user'
import { hasToken } from '@/api/token'

defineOptions({ name: 'pc-flea-detail' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const item = ref<FleaItem | null>(null)
const loading = ref(true)

// ---------------- 下单弹层 ----------------
const showBuy = ref(false)
const orderAddress = ref<AddressValue>({ areaCode: '', areaName: '', detail: '' })
const ordering = ref(false)
/** 地址簿一键填入状态：loading 拉取中 / filled 已填入 / empty 地址簿为空（去地址簿填写） */
const addressState = ref<'loading' | 'filled' | 'empty' | ''>('')

/** 地址簿一键填入：默认地址优先，无默认取第一条；地址簿为空则引导去地址簿填写 */
async function fillDefaultAddress() {
  addressState.value = 'loading'
  try {
    const list = await listAddresses()
    const def = list.find((a) => a.isDefault === 1) ?? list[0]
    if (def) {
      orderAddress.value = {
        areaCode: def.areaCode,
        areaName: def.areaName,
        province: def.province,
        city: def.city,
        district: def.district,
        detail: def.detail,
        lng: def.lng,
        lat: def.lat,
      }
      addressState.value = 'filled'
    } else {
      addressState.value = 'empty'
    }
  } catch {
    addressState.value = 'empty'
  }
}

/** 地址簿为空：关闭弹层，跳地址簿页新增（返回后重新点购买即自动填入） */
function goAddressBook() {
  showBuy.value = false
  router.push('/pc/address-book')
}

/** 本人商品：不显示购买按钮（商品 id 与当前用户 id 均可能为雪花，字符串化比较） */
const isMine = computed(
  () => !!item.value && hasToken() && String(userStore.profile?.id ?? '') === item.value.sellerId,
)

const addressText = (a: AddressValue) => addressTextOf(a)

function onBuy() {
  if (!hasToken()) {
    router.push({ path: '/pc/login', query: { redirect: route.fullPath } })
    return
  }
  orderAddress.value = { areaCode: '', areaName: '', detail: '' }
  showBuy.value = true
  fillDefaultAddress()
}

async function confirmBuy() {
  const it = item.value
  if (!it) return
  if ((!orderAddress.value.areaCode && !orderAddress.value.province) || !orderAddress.value.detail.trim()) {
    showToast('请填写收货地址')
    return
  }
  ordering.value = true
  try {
    await createFleaOrder(it.id, addressText(orderAddress.value))
    showSuccessToast('下单成功，积分已冻结')
    showBuy.value = false
    // 跳「我买到的」（tab 参数由 mine 页读取）
    router.replace('/pc/flea/mine?tab=bought')
  } finally {
    ordering.value = false
  }
}

onMounted(async () => {
  try {
    // 路由参数为原始雪花字符串，直传防精度丢失
    item.value = await getFleaItem(String(route.params.id))
  } catch {
    showToast('商品不存在或已下架')
    router.back()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="pc-flea-detail">
    <div class="container">
      <h1 class="page-title">商品详情</h1>

      <div v-if="item" class="card detail">
        <div class="detail__img">
          <img v-if="item.imageUrl" :src="item.imageUrl" alt="" />
          <div v-else class="detail__img-fallback"><SvgIcon name="common-cube" :size="80" /></div>
        </div>

        <div class="detail__body">
          <div class="detail__price">{{ item.price }} 积分</div>
          <div class="detail__title">{{ item.title }}</div>
          <span class="detail__tag">{{ item.categoryText }}</span>

          <div class="detail__seller">
            <div class="detail__avatar"><SvgIcon name="nav-user" :size="20" /></div>
            <div>
              <div class="detail__seller-name">{{ item.sellerName }}</div>
              <div class="detail__seller-sub">累计已售 {{ item.soldCount }} 件</div>
            </div>
          </div>

          <div class="detail__desc-title">商品描述</div>
          <div class="detail__desc">{{ item.description }}</div>
          <div class="detail__time">发布于 {{ item.createTime }}</div>

          <div v-if="isMine" class="detail__mine-tip">这是我发布的</div>
          <button v-else class="btn-buy" @click="onBuy">立即购买</button>
        </div>
      </div>

      <div v-else-if="!loading" class="empty">商品不存在或已下架</div>
    </div>

    <!-- 下单确认弹层：收货地址必填 + 冻结提示 -->
    <div v-if="showBuy && item" class="pc-flea-detail__mask" @click.self="showBuy = false">
      <div class="pc-flea-detail__dialog">
        <h3 class="pc-flea-detail__dialog-title">确认下单</h3>
        <div class="pc-flea-detail__dialog-item">
          <span class="van-ellipsis">{{ item.title }}</span>
          <strong class="pc-flea-detail__dialog-price">{{ item.price }} 积分</strong>
        </div>
        <p class="pc-flea-detail__dialog-tip">将冻结 {{ item.price }} 积分，确认收货后支付给卖家</p>
        <div class="pc-flea-detail__dialog-label">收货地址 <em class="pc-flea-detail__dialog-required">*</em></div>
        <div v-if="addressState === 'loading'" class="pc-flea-detail__dialog-addr">正在获取收货地址…</div>
        <template v-else-if="addressState === 'filled'">
          <AddressPicker v-model="orderAddress" />
        </template>
        <div v-else class="pc-flea-detail__dialog-addr pc-flea-detail__dialog-addr--empty">
          <span>暂无收货地址，去地址簿填写后自动填入</span>
          <button type="button" class="pc-flea-detail__dialog-addr-btn" @click="goAddressBook">去地址簿填写</button>
        </div>
        <div class="pc-flea-detail__dialog-actions">
          <button class="pc-flea-detail__dialog-cancel" @click="showBuy = false">取消</button>
          <button class="pc-flea-detail__dialog-ok" :disabled="ordering" @click="confirmBuy">
            {{ ordering ? '下单中…' : '确认下单' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-flea-detail {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 980px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 48px;
  text-align: center;
}

.card {
  background: var(--df-bg-card);
  border-radius: 16px;
  box-shadow: var(--df-shadow-card);
  overflow: hidden;
}

.detail {
  display: grid;
  grid-template-columns: 1fr 1.2fr;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }

  &__img {
    background: var(--df-bg-card);

    img {
      width: 100%;
      height: 100%;
      max-height: 520px;
      object-fit: cover;
      display: block;
    }
  }

  &__img-fallback {
    width: 100%;
    height: 520px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--df-text-secondary);
  }

  &__body {
    padding: 32px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }

  &__price {
    color: var(--df-color-gold);
    font-weight: 700;
    font-size: 32px;
  }

  &__title {
    margin-top: 8px;
    font-size: 24px;
    font-weight: 700;
    color: var(--df-text-primary);
  }

  &__tag {
    display: inline-block;
    margin-top: 12px;
    padding: 4px 14px;
    border-radius: 8px;
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
    font-size: 13px;
  }

  &__seller {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 24px 0;
    padding: 16px;
    border: 2px solid var(--df-border-color);
    border-radius: 12px;
    width: 100%;
  }

  &__avatar {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
  }

  &__seller-name {
    font-size: 16px;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__seller-sub {
    margin-top: 2px;
    font-size: 13px;
    color: var(--df-text-secondary);
  }

  &__desc-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__desc {
    margin-top: 8px;
    font-size: 15px;
    color: var(--df-text-regular);
    line-height: 1.8;
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__time {
    margin-top: 16px;
    font-size: 13px;
    color: var(--df-text-secondary);
  }

  &__mine-tip {
    margin-top: 24px;
    padding: 12px 0;
    text-align: center;
    width: 100%;
    color: var(--df-text-secondary);
    font-size: 15px;
    background: var(--df-bg-card);
    border-radius: 12px;
  }
}

.btn-buy {
  margin-top: 24px;
  padding: 14px 0;
  width: 100%;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  background: var(--df-color-primary);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover { opacity: 0.9; }
}

.empty {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.pc-flea-detail__mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.pc-flea-detail__dialog {
  width: 560px;
  max-width: calc(100vw - 48px);
  padding: 32px;
  background: var(--df-bg-card);
  border-radius: 16px;
  box-shadow: var(--df-shadow-float);

  &-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--df-text-primary);
    text-align: center;
    margin-bottom: 20px;
  }

  &-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    padding: 14px 0;
    border-bottom: 1px solid var(--df-border-color);
  }

  &-price {
    color: var(--df-color-gold);
    font-weight: 700;
  }

  &-tip {
    margin: 12px 0;
    padding: 10px 14px;
    border-radius: 8px;
    background: rgba(184, 135, 31, 0.08);
    color: var(--df-color-warning);
    font-size: 13px;
    line-height: 1.6;
  }

  &-label {
    font-size: 14px;
    margin-bottom: 8px;
  }

  &-addr {
    padding: 12px 14px;
    border-radius: 10px;
    background: var(--df-bg-card);
    font-size: 13px;
    color: var(--df-text-secondary);

    &--empty {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      border: 2px dashed var(--df-border-color);
    }
  }

  &-addr-btn {
    flex-shrink: 0;
    padding: 6px 14px;
    font-size: 13px;
    font-weight: 600;
    color: #fff;
    background: var(--df-color-primary);
    border: none;
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover { opacity: 0.9; }
  }

  &-required {
    color: var(--df-color-danger);
    font-style: normal;
  }

  &-actions {
    display: flex;
    gap: 12px;
    margin-top: 24px;
  }

  &-cancel {
    flex: 1;
    padding: 12px;
    font-size: 15px;
    font-weight: 600;
    color: var(--df-text-regular);
    background: transparent;
    border: 2px solid var(--df-border-color);
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      border-color: var(--df-color-primary);
      color: var(--df-color-primary);
    }
  }

  &-ok {
    flex: 1;
    padding: 12px;
    font-size: 15px;
    font-weight: 600;
    color: #fff;
    background: var(--df-color-primary);
    border: none;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover:not(:disabled) { opacity: 0.9; }
    &:disabled { opacity: 0.6; cursor: not-allowed; }
  }
}
</style>
