<script setup lang="ts">
// 科普专栏（配套辅助）：分类知识文章列表（本地静态内容，后续接 CMS）
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

interface Article {
  id: number
  icon: string
  title: string
  summary: string
  content: string
  readCount: number
}

const articles: Article[] = [
  {
    id: 1,
    icon: 'recycle-trash-bin',
    title: '四分类到底怎么分？一张图看懂',
    summary: '可回收物、厨余垃圾、有害垃圾、其他垃圾的判定口诀',
    content:
      '记住口诀：能卖拿去卖（可回收），易腐单独放（厨余），有害要慎重（有害），没人要的其他装。\n\n可回收物：废纸、塑料、金属、织物、玻璃、家电数码；\n厨余垃圾：剩饭剩菜、果皮菜叶、过期食品（需破袋投放）；\n有害垃圾：电池、灯管、过期药品、油漆桶；\n其他垃圾：污损纸张、烟蒂、尘土。',
    readCount: 2861,
  },
  {
    id: 2,
    icon: 'carbon-sprout',
    title: '你的每一次分类，减了多少碳？',
    summary: '碳减排系数科普：1kg 废纸 ≈ 1.4kgCO₂',
    content:
      '回收 1kg 废纸约减排 1.4kgCO₂，1kg 塑料约 2.1kgCO₂，1kg 金属约 4.3kgCO₂，家电与电子产品的单位减排量最高。\n\n平台按冻结的碳因子表计账，每笔减排写入可信碳账本（哈希链），支持第三方验证，攒下的碳积分可在商城兑换好物。',
    readCount: 1972,
  },
  {
    id: 3,
    icon: 'kitchen-leafy-green',
    title: '厨余为什么要破袋投放？',
    summary: '破袋是厨余资源化的第一道关口',
    content:
      '塑料袋混入厨余会污染整桶资源化原料，因此投放时需破袋倒入厨余桶、袋子另投其他垃圾。\n\n本平台每晚 20:00-23:00 开放投放上报，拍照后 AI 自动审核破袋与分类质量，合格每袋 +2 积分。',
    readCount: 1408,
  },
  {
    id: 4,
    icon: 'repair-wrench',
    title: '小家电坏了先别扔，维修更划算',
    summary: '维修 vs 换新的碳账与钱账',
    content:
      '一台电饭煲维修均价 30-60 元，换新则要 200 元以上；维修还能避免整机报废产生的约 8.7kgCO₂。\n\n平台支持在线预约维修，AI 预诊会提前告知可能故障与费用区间；确实无法修复的，可一键转回收，残值直接抵积分。',
    readCount: 986,
  },
  {
    id: 5,
    icon: 'recycle-battery',
    title: '有害垃圾的正确打开方式',
    summary: '电池灯管药品，去哪儿投？',
    content:
      '废电池（充电电池、纽扣电池）、荧光灯管、过期药品、杀虫剂罐都属于有害垃圾。\n\n请投入小区红色有害垃圾桶；大量废弃时可在 App 预约上门，工作人员将转运至有资质的处置单位。',
    readCount: 754,
  },
]

const active = ref<Article | null>(null)
</script>

<template>
  <div class="science">
    <van-nav-bar
      :title="active ? '文章详情' : '科普专栏'"
      left-arrow
      @click-left="active ? (active = null) : router.back()"
    />

    <!-- 详情 -->
    <div v-if="active" class="page">
      <div class="card">
        <h2 class="science__title"><SvgIcon :name="active.icon" /> {{ active.title }}</h2>
        <div class="science__meta">{{ active.readCount }} 人已读</div>
        <p v-for="(para, i) in active.content.split('\n\n')" :key="i" class="science__para">{{ para }}</p>
      </div>
    </div>

    <!-- 列表 -->
    <div v-else class="page">
      <div v-for="a in articles" :key="a.id" class="card science__item" @click="active = a">
        <div class="science__emoji"><SvgIcon :name="a.icon" /></div>
        <div class="science__info">
          <div class="science__item-title">{{ a.title }}</div>
          <div class="science__summary">{{ a.summary }}</div>
          <div class="science__meta">{{ a.readCount }} 人已读</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.science {
  &__item {
    display: flex;
    gap: $df-space-md;
  }

  &__emoji {
    font-size: 32px;
  }

  &__item-title {
    font-weight: 600;
    font-size: $df-font-size-base;
  }

  &__summary {
    margin-top: $df-space-xs;
    color: var(--df-text-regular);
    font-size: $df-font-size-sm;
  }

  &__meta {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__title {
    margin: 0;
    font-size: $df-font-size-lg;
  }

  &__para {
    line-height: 1.8;
    color: var(--df-text-regular);
  }
}
</style>
