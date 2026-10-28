<script setup lang="ts">
// 跳蚤市场商品详情（Upgrade U4）：大图 + 卖家信息 + 下单弹层（AddressPicker 必填 + 冻结提示）
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getFleaItem, createFleaOrder, type FleaItem } from '@/api/business'
import { listAddresses } from '@/api/addressBook'
import AddressPicker from '@shared/components/AddressPicker.vue'
import { addressTextOf, type AddressValue } from '@shared/components/address'
import { useUserStore } from '@/store/user'
import { hasToken } from '@/api/token'

defineOptions({ name: 'flea-detail' })

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
  router.push('/mobile/address-book')
}

/** 本人商品：不显示购买按钮（商品 id 与当前用户 id 均可能为雪花，字符串化比较） */
const isMine = computed(
  () => !!item.value && hasToken() && String(userStore.profile?.id ?? '') === item.value.sellerId,
)

const addressText = (a: AddressValue) => addressTextOf(a)

function onBuy() {
  if (!hasToken()) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
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
    router.replace('/mobile/flea/mine?tab=bought')
  } catch {
    // 下单失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    ordering.value = false
  }
}

function onPopupClose() {
  if (!ordering.value) showBuy.value = false
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
  <div class="page flea-detail">
    <van-nav-bar title="商品详情" left-arrow @click-left="router.back()" />

    <div v-if="item" class="flea-detail__body">
      <div class="flea-detail__img">
        <img v-if="item.imageUrl" :src="item.imageUrl" alt="" />
        <div v-else class="flea-detail__img-fallback"><SvgIcon name="common-cube" :size="64" /></div>
      </div>

      <div class="card flea-detail__card">
        <div class="flea-detail__price">{{ item.price }} 积分</div>
        <div class="flea-detail__title">{{ item.title }}</div>
        <div class="flea-detail__tag">{{ item.categoryText }}</div>
      </div>

      <div class="card flea-detail__card flea-detail__seller">
        <div class="flea-detail__avatar"><SvgIcon name="nav-user" :size="20" /></div>
        <div class="flea-detail__seller-info">
          <div class="flea-detail__seller-name">{{ item.sellerName }}</div>
          <div class="flea-detail__seller-sub">累计已售 {{ item.soldCount }} 件</div>
        </div>
      </div>

      <div class="card flea-detail__card">
        <div class="flea-detail__desc-title">商品描述</div>
        <div class="flea-detail__desc">{{ item.description }}</div>
        <div class="flea-detail__time">发布于 {{ item.createTime }}</div>
      </div>
    </div>

    <div v-else-if="!loading" class="flea-detail__empty">商品不存在或已下架</div>

    <!-- 底部操作栏 -->
    <div v-if="item" class="flea-detail__bar">
      <div v-if="isMine" class="flea-detail__mine-tip">这是我发布的</div>
      <van-button v-else round type="primary" block @click="onBuy">立即购买</van-button>
    </div>

    <!-- 下单确认弹层：收货地址必填 + 冻结提示 -->
    <van-popup :show="showBuy" position="bottom" round class="flea-detail__pop" @update:show="onPopupClose">
      <template v-if="item">
        <div class="flea-detail__pop-title">确认下单</div>
        <div class="flea-detail__pop-item">
          <span class="van-ellipsis">{{ item.title }}</span>
          <strong class="flea-detail__price">{{ item.price }} 积分</strong>
        </div>
        <div class="flea-detail__pop-tip">将冻结 {{ item.price }} 积分，确认收货后支付给卖家</div>
        <div class="flea-detail__pop-label">收货地址 <em class="flea-detail__pop-required">*</em></div>
        <div v-if="addressState === 'loading'" class="flea-detail__addr-tip">正在获取收货地址…</div>
        <template v-else-if="addressState === 'filled'">
          <AddressPicker v-model="orderAddress" />
        </template>
        <van-button v-else round block plain type="primary" @click="goAddressBook">
          暂无收货地址，去地址簿填写
        </van-button>
        <van-button type="primary" round block :loading="ordering" class="flea-detail__pop-btn" @click="confirmBuy">
          确认下单
        </van-button>
      </template>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.flea-detail {
  &__body {
    padding-bottom: 80px;
  }

  &__img {
    width: 100%;
    aspect-ratio: 4 / 3;
    background: var(--df-bg-card);

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
  }

  &__img-fallback {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--df-text-secondary);
  }

  &__card {
    margin: $df-space-md;
    padding: $df-space-md;
  }

  &__price {
    color: var(--df-color-gold);
    font-weight: 700;
    font-size: $df-font-size-xl;
  }

  &__title {
    margin-top: $df-space-xs;
    font-size: $df-font-size-lg;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__tag {
    display: inline-block;
    margin-top: $df-space-sm;
    padding: 2px $df-space-sm;
    border-radius: $df-radius-xs;
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
    font-size: $df-font-size-xs;
  }

  &__seller {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
  }

  &__avatar {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
  }

  &__seller-name {
    font-size: $df-font-size-base;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__seller-sub {
    margin-top: 2px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__desc-title {
    font-size: $df-font-size-base;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__desc {
    margin-top: $df-space-xs;
    font-size: $df-font-size-sm;
    color: var(--df-text-regular);
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__time {
    margin-top: $df-space-sm;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__empty {
    padding: $df-space-xl 0;
    text-align: center;
    color: var(--df-text-secondary);
  }

  &__bar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 10;
    padding: $df-space-sm $df-space-md calc($df-space-sm + env(safe-area-inset-bottom));
    background: var(--df-bg-card);
    box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.06);
  }

  &__mine-tip {
    text-align: center;
    padding: $df-space-sm;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__pop {
    padding: $df-space-lg $df-space-md calc($df-space-lg + env(safe-area-inset-bottom));
  }

  &__pop-title {
    font-size: $df-font-size-lg;
    font-weight: 600;
    text-align: center;
    margin-bottom: $df-space-md;
  }

  &__pop-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: $df-space-sm;
    padding: $df-space-md 0;
    margin-bottom: $df-space-sm;
    border-bottom: 1px solid var(--df-border-color);
  }

  &__pop-tip {
    padding: $df-space-sm $df-space-md;
    margin-bottom: $df-space-md;
    border-radius: $df-radius-sm;
    background: rgba(184, 135, 31, 0.08);
    color: var(--df-color-warning);
    font-size: $df-font-size-xs;
    line-height: 1.5;
  }

  &__pop-label {
    font-size: $df-font-size-sm;
    margin-bottom: $df-space-sm;
  }

  &__addr-tip {
    margin: 0 $df-space-md;
    padding: $df-space-sm;
    border-radius: $df-radius-sm;
    background: var(--df-bg-page);
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
    text-align: center;
  }

  &__pop-required {
    color: var(--df-color-danger);
    font-style: normal;
  }

  &__pop-btn {
    margin-top: $df-space-lg;
  }
}
</style>
