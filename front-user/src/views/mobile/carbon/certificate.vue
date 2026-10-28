<script setup lang="ts">
// 碳减排可信证书（LoopFill B4 G7）：哈希链 + Merkle 锚定校验结果展示
// 入口：碳流水页「可信证书」icon 带 logId；mine 页入口无 logId 时引导先去碳账单选流水
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCarbonCertificate, type CarbonCertificate } from '@/api/business'

defineOptions({ name: 'carbon-certificate' })

const route = useRoute()
const router = useRouter()

// 雪花 ID 保持字符串（Number 会丢精度，后端 selectById 查不到 →「流水不存在」）
const logId = computed(() => {
  const v = route.query.logId
  return Array.isArray(v) ? String(v[0] ?? '') : v != null ? String(v) : ''
})

const cert = ref<CarbonCertificate | null>(null)
const loading = ref(false)
const loadError = ref('')

/** 综合校验通过：链完整且（已锚定时）可验证到根 */
const verified = computed(() => Boolean(cert.value?.valid))

/** 锚定根 hash 截断展示（前 12 位 + …） */
const shortHash = (h?: string) => (h && h.length > 16 ? `${h.slice(0, 12)}…${h.slice(-6)}` : h ?? '—')

async function load() {
  if (!logId.value) return
  loading.value = true
  loadError.value = ''
  cert.value = null
  try {
    cert.value = await getCarbonCertificate(logId.value)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '证书加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page cert">
    <van-nav-bar title="碳减排可信证书" left-arrow @click-left="router.back()" />

    <!-- 无 logId：引导入口 -->
    <div v-if="!logId" class="card cert__guide">
      <SvgIcon name="nav-link" size="48" />
      <p>请从碳流水中选择一条已锚定流水，查看其可信证书</p>
      <van-button round type="primary" @click="router.push('/carbon/bill')">去碳账单选流水</van-button>
    </div>

    <!-- 加载中 -->
    <van-skeleton v-else-if="loading" title :row="6" />

    <!-- 加载失败（含非本人 403） -->
    <div v-else-if="loadError" class="card cert__guide">
      <SvgIcon name="nav-cross-mark" size="48" />
      <p>{{ loadError }}</p>
      <van-button round plain type="primary" @click="router.push('/carbon/bill')">返回碳账单</van-button>
    </div>

    <template v-else-if="cert">
      <!-- 顶部大状态 -->
      <div class="card cert__hero" :class="verified ? 'cert__hero--ok' : 'cert__hero--bad'">
        <SvgIcon :name="verified ? 'nav-check-mark' : 'nav-cross-mark'" size="56" />
        <div class="cert__hero-title">
          {{ verified ? '哈希链 + Merkle 锚定校验通过' : '校验失败' }}
        </div>
        <div class="cert__hero-sub">
          {{ verified ? '该笔减排记录真实入链，不可篡改' : '请核对流水是否被篡改或锚定是否完成' }}
        </div>
      </div>

      <!-- 证书详情 -->
      <div class="card">
        <div class="card__title">证书详情</div>
        <van-cell-group :border="false">
          <van-cell title="流水号" :value="String(cert.logId)" />
          <van-cell title="业务类型" :value="cert.bizType" />
          <van-cell title="积分变动" :value="cert.points >= 0 ? `+${cert.points}` : String(cert.points)" />
          <van-cell title="碳减排" :value="`${cert.carbonReduced} kgCO₂`" />
          <van-cell title="备注" :value="cert.remark || '—'" />
          <van-cell title="入链时间" :value="cert.createTime" />
        </van-cell-group>
      </div>

      <!-- 锚定信息 -->
      <div class="card">
        <div class="card__title">锚定信息</div>
        <template v-if="cert.anchored">
          <div class="cert__anchor-row">
            <span class="cert__anchor-label">锚定日期</span>
            <span>{{ cert.anchorDate }}</span>
          </div>
          <div class="cert__anchor-row">
            <span class="cert__anchor-label">Merkle 根</span>
            <span class="cert__anchor-hash" :title="cert.merkleRoot">{{ shortHash(cert.merkleRoot) }}</span>
          </div>
          <div class="cert__anchor-row">
            <span class="cert__anchor-label">路径重算</span>
            <span :class="cert.rootMatched ? 'cert__anchor-ok' : 'cert__anchor-bad'">
              {{ cert.rootMatched ? '与锚定根一致' : '不一致' }}
            </span>
          </div>
          <div class="cert__proof">
            <div class="cert__proof-title">Merkle 验证路径（{{ cert.proof.length }} 层）</div>
            <div v-for="(n, i) in cert.proof" :key="i" class="cert__proof-row">
              <van-tag :type="n.left ? 'primary' : 'success'" plain size="medium">
                {{ n.left ? '左' : '右' }}
              </van-tag>
              <span class="cert__proof-hash" :title="n.hash">{{ shortHash(n.hash) }}</span>
            </div>
          </div>
        </template>
        <div v-else class="cert__pending">
          该流水已入链，每日 0:10 锚定任务运行后可查完整 Merkle 路径
        </div>
      </div>

      <!-- 不可篡改说明 -->
      <div class="card cert__note">
        <div class="card__title">为什么不可篡改？</div>
        <p>
          每笔碳流水入账时按「上一条哈希 + 业务内容」计算新哈希，逐条链接成链；篡改任意字段都会导致整链校验失败。
          每日 0:10 系统将所有流水聚合为 Merkle 树并锚定当日根，任何一条记录的增减都可被第三方沿验证路径复核。
        </p>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.cert {
  padding-top: 0;

  &__guide {
    padding: $df-space-xl;
    text-align: center;
    color: var(--df-text-secondary);

    p {
      margin: $df-space-md 0;
      font-size: $df-font-size-sm;
    }
  }

  &__hero {
    text-align: center;
    padding: $df-space-xl;

    &--ok {
      background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
      color: #fff;
    }

    &--bad {
      background: linear-gradient(135deg, var(--df-color-danger), #c0392b);
      color: #fff;
    }
  }

  &__hero-title {
    margin-top: $df-space-sm;
    font-size: $df-font-size-lg;
    font-weight: 600;
  }

  &__hero-sub {
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    opacity: 0.9;
  }

  &__anchor-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $df-space-sm 0;
    font-size: $df-font-size-sm;
    color: var(--df-text-regular);
  }

  &__anchor-label {
    color: var(--df-text-secondary);
  }

  &__anchor-hash {
    font-family: monospace;
    font-size: $df-font-size-xs;
    max-width: 55%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__anchor-ok {
    color: var(--df-color-primary);
    font-weight: 500;
  }

  &__anchor-bad {
    color: var(--df-color-danger);
    font-weight: 500;
  }

  &__proof {
    margin-top: $df-space-md;
    border-top: 1px dashed var(--df-border-color);
    padding-top: $df-space-sm;
  }

  &__proof-title {
    font-size: $df-font-size-sm;
    font-weight: 500;
    margin-bottom: $df-space-xs;
  }

  &__proof-row {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    padding: $df-space-xs 0;
  }

  &__proof-hash {
    font-family: monospace;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__pending {
    padding: $df-space-md 0;
    color: var(--df-color-warning);
    font-size: $df-font-size-sm;
  }

  &__note {
    p {
      margin: $df-space-sm 0 0;
      color: var(--df-text-secondary);
      font-size: $df-font-size-sm;
      line-height: 1.7;
    }
  }
}
</style>
