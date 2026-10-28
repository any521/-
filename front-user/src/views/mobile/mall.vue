<script setup lang="ts">
// 移动端积分市场（积分商城 × 跳蚤市场合并）：双 Tab——
// 成就徽章：内置 10 枚徽章，积分兑换虚拟收藏品（即到账，无需地址）；
// 闲置好物：用户之间 C2C 积分托管交易（原跳蚤市场，管理员实物同样在此流通）。
import { ref, onMounted, onActivated } from 'vue'
import { showToast, showSuccessToast } from 'vant'
import {
  pageBadges, exchangeBadge, pageFleaItems, getCarbonAccount, badgeIconName,
  FLEA_CATEGORIES,
  type Badge, type FleaItem,
} from '@/api/business'
import { hasToken } from '@/api/token'
import { useRouter } from 'vue-router'

defineOptions({ name: 'mall' })

const router = useRouter()

/** 双栏目：badge 成就徽章 / flea 闲置好物 */
const activeTab = ref<'badge' | 'flea'>('badge')
const points = ref(0)

// ---------------- 成就徽章 ----------------
const badges = ref<Badge[]>([])
const badgeLoading = ref(false)
const badgeFinished = ref(false)
let badgePage = 1

/** 徽章兑换弹层（虚拟商品，无需收货地址） */
const exchangeTarget = ref<Badge | null>(null)
const exchanging = ref(false)

async function loadBadges() {
  badgeLoading.value = true
  try {
    const page = await pageBadges({ pageNum: badgePage, pageSize: 8 })
    if (badgePage === 1) badges.value = []
    badges.value.push(...page.list)
    badgeFinished.value = badges.value.length >= page.total
    badgePage += 1
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    badgeLoading.value = false
  }
}

function onExchange(b: Badge) {
  if (!hasToken()) {
    router.push({ path: '/login', query: { redirect: '/mall' } })
    return
  }
  exchangeTarget.value = b
}

async function confirmExchange() {
  const b = exchangeTarget.value
  if (!b) return
  exchanging.value = true
  try {
    const { restPoints } = await exchangeBadge(b.id)
    points.value = restPoints
    b.owned = true
    showSuccessToast(`已获得「${b.name}」徽章`)
    exchangeTarget.value = null
  } catch {
    // 兑换失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    exchanging.value = false
  }
}

// ---------------- 闲置好物（原跳蚤市场） ----------------
const items = ref<FleaItem[]>([])
const itemLoading = ref(false)
const itemFinished = ref(false)
let itemPage = 1
const category = ref('')
const keyword = ref('')

async function loadItems() {
  itemLoading.value = true
  try {
    const page = await pageFleaItems(
      { pageNum: itemPage, pageSize: 8 },
      category.value || undefined,
      keyword.value.trim() || undefined,
    )
    if (itemPage === 1) items.value = []
    items.value.push(...page.list)
    itemFinished.value = items.value.length >= page.total
    itemPage += 1
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    itemLoading.value = false
  }
}

function reloadItems() {
  itemPage = 1
  itemFinished.value = false
  loadItems()
}

function onSearch() {
  reloadItems()
}

function onCategory(code: string) {
  category.value = code
  reloadItems()
}

function onDetail(item: FleaItem) {
  router.push(`/mobile/flea/detail/${item.id}`)
}

function onTabChange(tab: 'badge' | 'flea') {
  activeTab.value = tab
  // 首次进入闲置栏目时加载
  if (tab === 'flea' && items.value.length === 0 && !itemFinished.value) loadItems()
}

async function loadPoints() {
  if (!hasToken()) return
  try {
    const account = await getCarbonAccount()
    points.value = account.points
  } catch {
    // 积分加载失败：拦截器已 Toast，此处避免未捕获异常
  }
}

function onRefresh() {
  badgePage = 1
  badgeFinished.value = false
  itemPage = 1
  itemFinished.value = false
  loadBadges()
  if (activeTab.value === 'flea') loadItems()
  loadPoints()
}

onMounted(() => {
  loadBadges()
  loadPoints()
})

// KeepAlive 恢复自愈：徽章/闲置为空（上次加载失败）时自动重试，避免切回空白
onActivated(() => {
  if (badgeLoading.value || itemLoading.value) return
  if (badges.value.length === 0) loadBadges()
  if (activeTab.value === 'flea' && items.value.length === 0) loadItems()
  loadPoints()
})
</script>

<template>
  <!-- 单根包裹：Transition 动画要求组件渲染单一根元素（van-popup 弹层不能作为第二根） -->
  <div class="mall-root">
  <van-pull-refresh @refresh="onRefresh">
    <div class="page mall">
      <!-- 紧凑金色头图：积分 + 我的徽章入口（一行内） -->
      <div class="mall__points">
        <div class="mall__points-info">
          <span class="mall__points-label">可用积分</span>
          <strong class="mall__points-num">{{ points }}</strong>
        </div>
        <button class="mall__points-link" @click="router.push('/mall/badges')">
          <SvgIcon name="nav-star" :size="14" /> 我的徽章
        </button>
      </div>

      <!-- 双栏目切换（紧凑胶囊） -->
      <div class="mall__tabs">
        <button
          class="mall__tabs-item"
          :class="{ 'mall__tabs-item--on': activeTab === 'badge' }"
          @click="onTabChange('badge')"
        >成就徽章</button>
        <button
          class="mall__tabs-item"
          :class="{ 'mall__tabs-item--on': activeTab === 'flea' }"
          @click="onTabChange('flea')"
        >闲置好物</button>
      </div>

      <!-- 成就徽章栏 -->
      <template v-if="activeTab === 'badge'">
        <van-list v-model:loading="badgeLoading" :finished="badgeFinished" finished-text="没有更多了" @load="loadBadges">
          <div class="mall__grid">
            <div v-for="b in badges" :key="b.id" class="mall__badge">
              <div class="mall__badge-icon">
                <img v-if="b.iconUrl" :src="b.iconUrl" class="mall__badge-img" alt="" />
                <SvgIcon v-else :name="badgeIconName(b.code)" :size="36" color="var(--df-color-gold)" />
              </div>
              <div class="mall__badge-name van-ellipsis">{{ b.name }}</div>
              <div class="mall__badge-price">{{ b.price }} 积分</div>
              <button
                class="mall__badge-btn"
                :disabled="b.owned"
                @click.stop="onExchange(b)"
              >
                {{ b.owned ? '已拥有' : '兑换' }}
              </button>
            </div>
          </div>
        </van-list>
      </template>

      <!-- 闲置好物栏（原跳蚤市场） -->
      <template v-else>
        <div class="mall__search">
          <van-search
            v-model="keyword"
            placeholder="搜索闲置好物"
            shape="round"
            @search="onSearch"
          />
          <button class="mall__publish" @click="router.push('/mobile/flea/publish')">
            <SvgIcon name="common-plus" :size="14" /> 发布
          </button>
        </div>

        <div class="mall__chips">
          <span
            class="mall__chip"
            :class="{ 'mall__chip--on': category === '' }"
            @click="onCategory('')"
          >全部</span>
          <span
            v-for="c in FLEA_CATEGORIES"
            :key="c.code"
            class="mall__chip"
            :class="{ 'mall__chip--on': category === c.code }"
            @click="onCategory(c.code)"
          >{{ c.name }}</span>
        </div>

        <van-list v-model:loading="itemLoading" :finished="itemFinished" finished-text="没有更多了" @load="loadItems">
          <div class="mall__grid">
            <div v-for="item in items" :key="item.id" class="mall__goods" @click="onDetail(item)">
              <div class="mall__goods-img">
                <img v-if="item.imageUrl" v-lazy="item.imageUrl" alt="" />
                <div v-else class="mall__goods-fallback"><SvgIcon name="common-cube" :size="28" /></div>
              </div>
              <div class="mall__goods-body">
                <div class="mall__goods-title van-ellipsis">{{ item.title }}</div>
                <div class="mall__goods-foot">
                  <span class="mall__goods-price">{{ item.price }} 积分</span>
                  <span class="mall__goods-seller van-ellipsis">{{ item.sellerName }}</span>
                </div>
              </div>
            </div>
          </div>
          <van-empty
            v-if="itemFinished && items.length === 0"
            description="市场空空如也"
          />
        </van-list>
      </template>
    </div>
  </van-pull-refresh>

  <!-- 徽章兑换确认弹层（虚拟商品即到账，无需收货地址） -->
  <van-popup
    :show="!!exchangeTarget" position="bottom" round class="mall__pop"
    @update:show="(v: boolean) => { if (!v && !exchanging) exchangeTarget = null }"
  >
    <template v-if="exchangeTarget">
      <div class="mall__pop-title">确认兑换徽章</div>
      <div class="mall__pop-badge">
        <img v-if="exchangeTarget.iconUrl" :src="exchangeTarget.iconUrl" class="mall__pop-badge-img" alt="" />
        <SvgIcon v-else :name="badgeIconName(exchangeTarget.code)" :size="48" color="var(--df-color-gold)" />
      </div>
      <div class="mall__pop-name">{{ exchangeTarget.name }}</div>
      <div class="mall__pop-desc">{{ exchangeTarget.description }}</div>
      <div class="mall__pop-item">
        <span>兑换后立即到账</span>
        <strong class="mall__pop-price">{{ exchangeTarget.price }} 积分</strong>
      </div>
      <van-button type="warning" round block :loading="exchanging" class="mall__pop-btn" @click="confirmExchange">
        确认兑换
      </van-button>
    </template>
  </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

// 紧凑金色积分头图：单行布局，左侧标签+数值，右侧「我的徽章」入口
.mall__points {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $df-space-sm;
  padding: $df-space-sm $df-space-md;
  background: linear-gradient(135deg, var(--df-color-gold) 0%, var(--df-color-gold-dark, #b8871f) 100%);
  color: #fff;
  border-radius: $df-radius-md;
  margin-bottom: $df-space-sm;
  position: relative;
  overflow: hidden;

  // 报刊装饰圆（右上角，缩小避免遮挡）
  &::after {
    content: '';
    position: absolute;
    right: -24px;
    top: -24px;
    width: 64px;
    height: 64px;
    border: 1px dashed rgba(255, 255, 255, 0.3);
    border-radius: 50%;
    pointer-events: none;
  }
}

.mall__points-info {
  display: flex;
  align-items: baseline;
  gap: $df-space-xs;
}

.mall__points-label {
  font-size: $df-font-size-xs;
  opacity: 0.85;
}

.mall__points-num {
  font-size: $df-font-size-xl;
  font-family: $df-font-serif;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1;
}

.mall__points-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: $df-font-size-xs;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: $df-radius-round;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

// 双栏目切换：紧凑胶囊
.mall__tabs {
  display: flex;
  gap: $df-space-xs;
  margin-bottom: $df-space-sm;
  padding: 4px;
  background: var(--df-bg-card);
  border-radius: $df-radius-round;
  box-shadow: var(--df-shadow-card);

  &-item {
    flex: 1;
    padding: 6px 0;
    border: none;
    border-radius: $df-radius-round;
    background: transparent;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    text-align: center;
    cursor: pointer;
    transition: all 0.2s;
    -webkit-tap-highlight-color: transparent;

    &--on {
      color: #fff;
      background: linear-gradient(135deg, var(--df-color-primary) 0%, var(--df-color-primary-dark) 100%);
      font-weight: 600;
    }
  }
}

// 搜索 + 发布：van-search 紧凑模式（去除内外 padding，避免被卡片遮挡）
.mall__search {
  display: flex;
  align-items: center;
  gap: $df-space-xs;
  margin-bottom: $df-space-xs;

  :deep(.van-search) {
    flex: 1;
    padding: 0;
    // 紧凑：去除 van-search 外层 padding
    --van-search-padding: 0;
  }

  :deep(.van-search__content) {
    padding: 0 8px;
    height: 32px;
    border-radius: $df-radius-round;
  }

  :deep(.van-field__left-icon) {
    display: flex;
    align-items: center;
  }

  :deep(.van-field__body) {
    height: 32px;
    font-size: $df-font-size-xs;
  }
}

.mall__publish {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  font-size: $df-font-size-xs;
  font-weight: 600;
  color: #fff;
  background: var(--df-color-primary);
  border: none;
  border-radius: $df-radius-round;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  height: 32px;
}

// 分类 chips：横向滚动
.mall__chips {
  display: flex;
  gap: 6px;
  padding: 0 $df-space-sm $df-space-sm;
  overflow-x: auto;
  white-space: nowrap;
  -webkit-overflow-scrolling: touch;

  &::-webkit-scrollbar { display: none; }
}

.mall__chip {
  flex-shrink: 0;
  padding: 4px 12px;
  border-radius: $df-radius-round;
  background: var(--df-bg-card);
  color: var(--df-text-secondary);
  font-size: $df-font-size-xs;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: all 0.2s;

  &--on {
    background: var(--df-color-primary);
    color: #fff;
    font-weight: 600;
  }
}

// 网格：2 列紧凑
.mall__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $df-space-sm;
}

// 徽章卡片：紧凑
.mall__badge {
  padding: $df-space-sm;
  background: var(--df-bg-card);
  border-radius: $df-radius-md;
  text-align: center;
  box-shadow: var(--df-shadow-card);
}

.mall__badge-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto $df-space-xs;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, color-mix(in srgb, var(--df-color-gold) 8%, transparent) 0%, color-mix(in srgb, var(--df-color-secondary, #2f6b5e) 8%, transparent) 100%);
  overflow: hidden;
}

.mall__badge-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mall__badge-name {
  font-size: $df-font-size-sm;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 2px;
}

.mall__badge-price {
  font-size: $df-font-size-xs;
  font-weight: 700;
  color: var(--df-color-gold);
  margin-bottom: $df-space-xs;
}

.mall__badge-btn {
  width: 100%;
  padding: 4px;
  font-size: $df-font-size-xs;
  font-weight: 600;
  color: #fff;
  background: var(--df-color-gold);
  border: none;
  border-radius: $df-radius-sm;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;

  &:disabled {
    opacity: 0.5;
    background: var(--df-text-placeholder);
  }
}

// 闲置好物卡片：紧凑
.mall__goods {
  background: var(--df-bg-card);
  border-radius: $df-radius-md;
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--df-shadow-card);
  -webkit-tap-highlight-color: transparent;

  &:active {
    opacity: 0.85;
  }
}

.mall__goods-img {
  width: 100%;
  aspect-ratio: 4 / 3;
  background: var(--df-bg-page);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.mall__goods-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--df-text-placeholder);
}

.mall__goods-body {
  padding: $df-space-xs $df-space-sm;
}

.mall__goods-title {
  font-size: $df-font-size-sm;
  font-weight: 600;
  color: var(--df-text-primary);
}

.mall__goods-foot {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 4px;
  margin-top: 4px;
}

.mall__goods-price {
  color: var(--df-color-gold);
  font-weight: 700;
  font-size: $df-font-size-sm;
  flex-shrink: 0;
}

.mall__goods-seller {
  font-size: 10px;
  color: var(--df-text-secondary);
  max-width: 50%;
}

// 弹层：紧凑
.mall__pop {
  padding: $df-space-md $df-space-md calc($df-space-md + env(safe-area-inset-bottom));
}

.mall__pop-title {
  font-size: $df-font-size-base;
  font-weight: 600;
  text-align: center;
  margin-bottom: $df-space-sm;
}

.mall__pop-badge {
  width: 72px;
  height: 72px;
  margin: 0 auto $df-space-xs;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, color-mix(in srgb, var(--df-color-gold) 8%, transparent) 0%, color-mix(in srgb, var(--df-color-secondary, #2f6b5e) 8%, transparent) 100%);
  overflow: hidden;
}

.mall__pop-badge-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mall__pop-name {
  font-size: $df-font-size-base;
  font-weight: 600;
  text-align: center;
}

.mall__pop-desc {
  font-size: $df-font-size-xs;
  color: var(--df-text-secondary);
  text-align: center;
  margin: 4px 0 $df-space-sm;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.mall__pop-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $df-space-sm;
  padding: $df-space-sm 0;
  margin-bottom: $df-space-sm;
  border-top: 1px dashed var(--df-border-color);
  border-bottom: 1px solid var(--df-border-color);
  font-size: $df-font-size-xs;
  color: var(--df-text-secondary);
}

.mall__pop-price {
  font-size: $df-font-size-base;
  font-weight: 700;
  color: var(--df-color-gold);
}

.mall__pop-btn {
  margin-top: $df-space-sm;
}
</style>
