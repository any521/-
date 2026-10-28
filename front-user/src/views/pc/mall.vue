<script setup lang="ts">
// PC 积分市场（积分商城 × 跳蚤市场合并）：双 Tab——
// 成就徽章：内置 10 枚徽章，积分兑换虚拟收藏品（即到账，无需地址）；
// 闲置好物：用户之间 C2C 积分托管交易（原跳蚤市场，管理员实物同样在此流通）。
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  pageBadges, exchangeBadge, pageFleaItems, getCarbonAccount, badgeIconName,
  FLEA_CATEGORIES,
  type Badge, type FleaItem,
} from '@/api/business'
import { dfToast } from '@shared/feedback'
import { hasToken } from '@/api/token'

defineOptions({ name: 'pc-mall' })

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
  } finally {
    badgeLoading.value = false
  }
}

function onExchange(b: Badge) {
  if (!hasToken()) {
    router.push({ path: '/login', query: { redirect: '/pc/mall' } })
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
    exchangeTarget.value = null
    dfToast(`已获得「${b.name}」徽章`, 'success')
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
      { pageNum: itemPage, pageSize: 12 },
      category.value || undefined,
      keyword.value.trim() || undefined,
    )
    if (itemPage === 1) items.value = []
    items.value.push(...page.list)
    itemFinished.value = items.value.length >= page.total
    itemPage += 1
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
  router.push(`/pc/flea/detail/${item.id}`)
}

function onTabChange(tab: 'badge' | 'flea') {
  activeTab.value = tab
  // 首次进入闲置栏目时加载
  if (tab === 'flea' && items.value.length === 0 && !itemFinished.value) loadItems()
}

async function loadPoints() {
  if (!hasToken()) return
  const account = await getCarbonAccount()
  points.value = account.points
}

onMounted(() => {
  loadBadges()
  loadPoints()
})
</script>

<template>
  <div class="pc-mall">
    <div class="container">
      <div class="head">
        <div>
          <h1 class="page-title">积分市场</h1>
          <p class="page-desc">成就徽章 + 闲置好物，全部积分交易</p>
        </div>
        <div class="head__side">
          <span class="head__points">当前可用积分 <strong>{{ points }}</strong></span>
          <button class="head__link" @click="router.push('/pc/mall/badges')">我的徽章</button>
        </div>
      </div>

      <!-- 双栏目切换 -->
      <div class="tabs">
        <button
          class="tabs__item"
          :class="{ 'tabs__item--on': activeTab === 'badge' }"
          @click="onTabChange('badge')"
        >成就徽章</button>
        <button
          class="tabs__item"
          :class="{ 'tabs__item--on': activeTab === 'flea' }"
          @click="onTabChange('flea')"
        >闲置好物</button>
      </div>

      <!-- 成就徽章栏 -->
      <template v-if="activeTab === 'badge'">
        <div v-if="badgeLoading && badges.length === 0" class="loading">加载中…</div>
        <div v-else-if="badgeFinished && badges.length === 0" class="empty">暂无徽章上架</div>

        <div v-else class="badge-grid">
          <div v-for="b in badges" :key="b.id" class="badge-card">
            <div class="badge-card__icon">
              <img v-if="b.iconUrl" :src="b.iconUrl" class="badge-card__img" alt="" />
              <SvgIcon v-else :name="badgeIconName(b.code)" :size="64" color="var(--df-color-gold)" />
            </div>
            <div class="badge-card__name">{{ b.name }}</div>
            <div class="badge-card__desc">{{ b.description }}</div>
            <div class="badge-card__price">{{ b.price }} 积分</div>
            <button
              class="badge-card__btn"
              :disabled="b.owned"
              @click="onExchange(b)"
            >
              {{ b.owned ? '已拥有' : '立即兑换' }}
            </button>
          </div>
        </div>

        <button v-if="!badgeFinished && badges.length > 0" class="load-more" :disabled="badgeLoading" @click="loadBadges">
          {{ badgeLoading ? '加载中…' : '加载更多' }}
        </button>
        <p v-else-if="badgeFinished && badges.length > 0" class="no-more">没有更多了</p>
      </template>

      <!-- 闲置好物栏（原跳蚤市场） -->
      <template v-else>
        <div class="toolbar">
          <div class="search">
            <input
              v-model="keyword"
              class="search__input"
              placeholder="搜索闲置好物"
              @keyup.enter="onSearch"
            />
            <button class="btn-primary" @click="onSearch">搜索</button>
          </div>
          <button class="btn-primary btn-primary--solid" @click="router.push('/pc/flea/publish')">发布闲置</button>
        </div>

        <div class="chips">
          <span
            class="chips__item"
            :class="{ 'chips__item--on': category === '' }"
            @click="onCategory('')"
          >全部</span>
          <span
            v-for="c in FLEA_CATEGORIES"
            :key="c.code"
            class="chips__item"
            :class="{ 'chips__item--on': category === c.code }"
            @click="onCategory(c.code)"
          >{{ c.name }}</span>
        </div>

        <div class="card">
          <div v-if="itemLoading && items.length === 0" class="loading">加载中…</div>

          <div v-else-if="itemFinished && items.length === 0" class="empty">
            市场空空如也，快来发布第一件闲置
          </div>

          <div v-else class="grid">
            <div v-for="item in items" :key="item.id" class="goods" @click="onDetail(item)">
              <div class="goods__img">
                <img v-if="item.imageUrl" v-lazy="item.imageUrl" alt="" />
                <div v-else class="goods__img-fallback"><SvgIcon name="common-cube" :size="40" /></div>
              </div>
              <div class="goods__body">
                <div class="goods__title">{{ item.title }}</div>
                <span class="goods__tag">{{ item.categoryText }}</span>
                <div class="goods__foot">
                  <span class="goods__price">{{ item.price }} 积分</span>
                  <span class="goods__seller">{{ item.sellerName }} · 已售 {{ item.soldCount }}</span>
                </div>
              </div>
            </div>
          </div>

          <button v-if="!itemFinished && items.length > 0" class="load-more" :disabled="itemLoading" @click="loadItems">
            {{ itemLoading ? '加载中…' : '加载更多' }}
          </button>
          <p v-else-if="itemFinished && items.length > 0" class="no-more">没有更多了</p>
        </div>
      </template>
    </div>

    <!-- 徽章兑换确认弹层（虚拟商品即到账，无需收货地址） -->
    <div v-if="exchangeTarget" class="pc-mall__mask" @click.self="exchangeTarget = null">
      <div class="pc-mall__dialog">
        <h3 class="pc-mall__dialog-title">确认兑换徽章</h3>
        <div class="pc-mall__dialog-icon">
          <img v-if="exchangeTarget.iconUrl" :src="exchangeTarget.iconUrl" class="pc-mall__dialog-img" alt="" />
          <SvgIcon v-else :name="badgeIconName(exchangeTarget.code)" :size="64" color="var(--df-color-gold)" />
        </div>
        <div class="pc-mall__dialog-name">{{ exchangeTarget.name }}</div>
        <p class="pc-mall__dialog-desc">{{ exchangeTarget.description }}</p>
        <div class="pc-mall__dialog-item">
          <span>兑换后立即到账，可在「我的徽章」查看</span>
          <strong class="pc-mall__dialog-price">{{ exchangeTarget.price }} 积分</strong>
        </div>
        <div class="pc-mall__dialog-actions">
          <button class="pc-mall__dialog-cancel" @click="exchangeTarget = null">取消</button>
          <button class="pc-mall__dialog-ok" :disabled="exchanging" @click="confirmExchange">
            {{ exchanging ? '兑换中…' : '确认兑换' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-mall {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 32px;

  &__side {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  &__points {
    font-size: 15px;
    color: var(--df-text-regular);

    strong {
      font-size: 24px;
      font-weight: 700;
      color: var(--df-color-gold);
      margin-left: 8px;
    }
  }

  &__link {
    padding: 10px 20px;
    font-size: 14px;
    font-weight: 600;
    color: var(--df-color-gold);
    background: transparent;
    border: 2px solid var(--df-color-gold);
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      background: color-mix(in srgb, var(--df-color-gold) 10%, transparent);
    }
  }
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
  margin-bottom: 0;
  text-align: center;
}

// 双栏目切换
.tabs {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 40px;

  &__item {
    min-width: 140px;
    padding: 12px 32px;
    font-size: 16px;
    font-weight: 600;
    color: var(--df-text-secondary);
    background: var(--df-bg-card);
    border: 2px solid var(--df-border-color);
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      color: var(--df-color-primary);
      border-color: var(--df-color-primary);
    }

    &--on {
      color: #fff;
      background: linear-gradient(135deg, var(--df-color-primary) 0%, var(--df-color-primary-dark) 100%);
      border-color: transparent;
      box-shadow: 0 4px 16px color-mix(in srgb, var(--df-color-secondary) 30%, transparent);
    }
  }
}

// ---- 徽章卡片 ----
.badge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 28px;
}

.badge-card {
  padding: 28px 24px;
  background: var(--df-bg-card);
  border-radius: 16px;
  text-align: center;
  box-shadow: var(--df-shadow-card);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-6px);
    box-shadow: var(--df-shadow-float);
  }

  &__icon {
    height: 104px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 16px;
    background: linear-gradient(135deg, color-mix(in srgb, var(--df-color-gold) 8%, transparent) 0%, color-mix(in srgb, var(--df-color-secondary) 8%, transparent) 100%);
    border-radius: 50%;
    width: 104px;
    margin-left: auto;
    margin-right: auto;
  }

  &__img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 50%;
  }

  &__name {
    font-size: 20px;
    font-weight: 700;
    color: var(--df-text-primary);
    margin-bottom: 8px;
  }

  &__desc {
    min-height: 42px;
    font-size: 13px;
    line-height: 1.6;
    color: var(--df-text-secondary);
    margin-bottom: 12px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__price {
    font-size: 22px;
    font-weight: 700;
    color: var(--df-color-gold);
    margin-bottom: 20px;
  }

  &__btn {
    width: 100%;
    padding: 11px;
    font-size: 15px;
    font-weight: 600;
    color: #fff;
    background: linear-gradient(135deg, var(--df-color-gold) 0%, var(--df-color-gold-dark) 100%);
    border: none;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 16px color-mix(in srgb, var(--df-color-gold) 40%, transparent);
    }

    &:disabled {
      opacity: 0.65;
      cursor: not-allowed;
      background: var(--df-text-placeholder);
    }
  }
}

// ---- 闲置好物（原跳蚤市场样式） ----
.toolbar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.search {
  display: flex;
  gap: 12px;

  &__input {
    width: 360px;
    padding: 10px 16px;
    font-size: 15px;
    color: var(--df-text-primary);
    background: var(--df-bg-card);
    border: 2px solid var(--df-border-color);
    border-radius: 999px;
    outline: none;
    transition: all 0.3s ease;

    &:focus {
      border-color: var(--df-color-primary);
    }
  }
}

.btn-primary {
  padding: 10px 28px;
  font-size: 15px;
  font-weight: 600;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-color-primary);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;

  &--solid {
    color: #fff;
    background: var(--df-color-primary);
  }

  &:hover {
    opacity: 0.9;
  }
}

.chips {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;

  &__item {
    padding: 6px 20px;
    border-radius: 999px;
    background: var(--df-bg-card);
    color: var(--df-text-secondary);
    font-size: 14px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      color: var(--df-color-primary);
    }

    &--on {
      background: var(--df-color-primary);
      color: #fff;
      font-weight: 600;
    }
  }
}

.card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--df-shadow-card);
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.goods {
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    box-shadow: var(--df-shadow-float);
  }

  &__img {
    width: 100%;
    aspect-ratio: 4 / 3;
    background: transparent;

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

  &__body {
    padding: 16px;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--df-text-primary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__tag {
    display: inline-block;
    margin-top: 8px;
    padding: 2px 10px;
    border-radius: 6px;
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
    font-size: 12px;
  }

  &__foot {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    margin-top: 12px;
  }

  &__price {
    color: var(--df-color-gold);
    font-weight: 700;
    font-size: 17px;
  }

  &__seller {
    font-size: 13px;
    color: var(--df-text-secondary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.loading, .empty {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.load-more {
  display: block;
  width: 100%;
  max-width: 400px;
  margin: 32px auto 0;
  padding: 12px;
  font-size: 15px;
  font-weight: 500;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent);
  }

  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.no-more {
  text-align: center;
  color: var(--df-text-secondary);
  font-size: 14px;
  margin: 32px 0 0;
}

// ---- 兑换弹层 ----
.pc-mall__mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.pc-mall__dialog {
  width: 440px;
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

  &-icon {
    width: 96px;
    height: 96px;
    margin: 0 auto 16px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, color-mix(in srgb, var(--df-color-gold) 8%, transparent) 0%, color-mix(in srgb, var(--df-color-secondary) 8%, transparent) 100%);
    overflow: hidden;
  }

  &-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &-name {
    font-size: 22px;
    font-weight: 700;
    color: var(--df-text-primary);
    text-align: center;
    margin-bottom: 8px;
  }

  &-desc {
    font-size: 13px;
    line-height: 1.6;
    color: var(--df-text-secondary);
    text-align: center;
    margin: 0 0 16px;
  }

  &-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    padding: 16px 0;
    margin-bottom: 20px;
    border-top: 1px dashed var(--df-border-color);
    border-bottom: 1px solid var(--df-border-color);
    font-size: 13px;
    color: var(--df-text-secondary);
  }

  &-price {
    font-size: 20px;
    font-weight: 700;
    color: var(--df-color-gold);
    white-space: nowrap;
  }

  &-actions {
    display: flex;
    gap: 12px;
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
    background: linear-gradient(135deg, var(--df-color-gold) 0%, var(--df-color-gold-dark) 100%);
    border: none;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover:not(:disabled) {
      box-shadow: 0 4px 16px color-mix(in srgb, var(--df-color-gold) 40%, transparent);
    }

    &:disabled { opacity: 0.6; cursor: not-allowed; }
  }
}
</style>
