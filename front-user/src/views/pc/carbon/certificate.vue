<script setup lang="ts">
// PC 碳减排可信证书（LoopFill B4 G7）：哈希链 + Merkle 锚定校验结果展示
// 入口：碳流水页「可信证书」icon 带 logId；mine 页入口无 logId 时引导先去碳账单选流水
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCarbonCertificate, type CarbonCertificate } from '@/api/business'

defineOptions({ name: 'pc-carbon-certificate' })

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
  <div class="pc-cert">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">碳减排可信证书</h1>
      </div>

      <!-- 无 logId：引导入口 -->
      <div v-if="!logId" class="section-card guide">
        <SvgIcon name="nav-link" :size="48" />
        <p>请从碳流水中选择一条已锚定流水，查看其可信证书</p>
        <button class="primary-btn" @click="router.push('/pc/carbon/bill')">去碳账单选流水</button>
      </div>

      <!-- 加载中 -->
      <div v-else-if="loading" class="section-card loading">证书校验中…</div>

      <!-- 加载失败（含非本人 403） -->
      <div v-else-if="loadError" class="section-card guide">
        <SvgIcon name="nav-cross-mark" :size="48" />
        <p>{{ loadError }}</p>
        <button class="primary-btn" @click="router.push('/pc/carbon/bill')">返回碳账单</button>
      </div>

      <template v-else-if="cert">
        <!-- 顶部大状态 -->
        <div class="hero-card" :class="verified ? 'hero-card--ok' : 'hero-card--bad'">
          <SvgIcon :name="verified ? 'nav-check-mark' : 'nav-cross-mark'" :size="56" />
          <div class="hero-title">{{ verified ? '哈希链 + Merkle 锚定校验通过' : '校验失败' }}</div>
          <div class="hero-sub">{{ verified ? '该笔减排记录真实入链，不可篡改' : '请核对流水是否被篡改或锚定是否完成' }}</div>
        </div>

        <!-- 证书详情 -->
        <div class="section-card">
          <h3 class="section-title">证书详情</h3>
          <div class="detail-row"><span>流水号</span><span>{{ cert.logId }}</span></div>
          <div class="detail-row"><span>业务类型</span><span>{{ cert.bizType }}</span></div>
          <div class="detail-row"><span>积分变动</span><span>{{ cert.points >= 0 ? `+${cert.points}` : cert.points }}</span></div>
          <div class="detail-row"><span>碳减排</span><span>{{ cert.carbonReduced }} kgCO₂</span></div>
          <div class="detail-row"><span>备注</span><span>{{ cert.remark || '—' }}</span></div>
          <div class="detail-row"><span>入链时间</span><span>{{ cert.createTime }}</span></div>
        </div>

        <!-- 锚定信息 -->
        <div class="section-card">
          <h3 class="section-title">锚定信息</h3>
          <template v-if="cert.anchored">
            <div class="detail-row"><span>锚定日期</span><span>{{ cert.anchorDate }}</span></div>
            <div class="detail-row">
              <span>Merkle 根</span>
              <span class="mono" :title="cert.merkleRoot">{{ shortHash(cert.merkleRoot) }}</span>
            </div>
            <div class="detail-row">
              <span>路径重算</span>
              <span :class="cert.rootMatched ? 'ok' : 'bad'">{{ cert.rootMatched ? '与锚定根一致' : '不一致' }}</span>
            </div>
            <div class="proof">
              <div class="proof-title">Merkle 验证路径（{{ cert.proof.length }} 层）</div>
              <div v-for="(n, i) in cert.proof" :key="i" class="proof-row">
                <span class="proof-side" :class="n.left ? 'proof-side--left' : 'proof-side--right'">
                  {{ n.left ? '左' : '右' }}
                </span>
                <span class="mono proof-hash" :title="n.hash">{{ shortHash(n.hash) }}</span>
              </div>
            </div>
          </template>
          <div v-else class="pending">该流水已入链，每日 0:10 锚定任务运行后可查完整 Merkle 路径</div>
        </div>

        <!-- 不可篡改说明 -->
        <div class="section-card">
          <h3 class="section-title">为什么不可篡改？</h3>
          <p class="note">
            每笔碳流水入账时按「上一条哈希 + 业务内容」计算新哈希，逐条链接成链；篡改任意字段都会导致整链校验失败。
            每日 0:10 系统将所有流水聚合为 Merkle 树并锚定当日根，任何一条记录的增减都可被第三方沿验证路径复核。
          </p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-cert {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container { max-width: 800px; margin: 0 auto; }

.header {
  display: flex; align-items: center; gap: 16px; margin-bottom: 48px;
}

.back-btn {
  display: flex; align-items: center; gap: 6px; padding: 8px 16px;
  font-size: 14px; color: var(--df-text-regular); background: var(--df-bg-card);
  border: 2px solid var(--df-border-color); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
}

.page-title { font-size: 32px; font-weight: 700; color: var(--df-text-primary); margin: 0; }

.section-card {
  background: var(--df-bg-card); border-radius: 16px; padding: 28px; margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.section-title { font-size: 18px; font-weight: 600; color: var(--df-text-primary); margin: 0 0 16px; }

.guide {
  text-align: center; padding: 60px 28px; color: var(--df-text-secondary);
  p { margin: 16px 0 24px; font-size: 15px; }
}

.primary-btn {
  padding: 12px 32px; font-size: 15px; font-weight: 600; color: #fff;
  background: var(--df-color-primary); border: none; border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { opacity: 0.9; }
}

.loading { text-align: center; padding: 60px 0; color: var(--df-text-secondary); font-size: 16px; }

.hero-card {
  display: flex; flex-direction: column; align-items: center;
  border-radius: 16px; padding: 36px; margin-bottom: 24px; color: #fff;
  &--ok { background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark)); }
  &--bad { background: linear-gradient(135deg, var(--df-color-danger), #c0392b); }
}
.hero-title { margin-top: 12px; font-size: 20px; font-weight: 600; }
.hero-sub { margin-top: 6px; font-size: 13px; opacity: 0.9; }

.detail-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 0; border-bottom: 1px solid var(--df-border-color);
  font-size: 14px; color: var(--df-text-regular);
  &:last-child { border-bottom: none; }
  span:first-child { color: var(--df-text-secondary); }
}

.mono { font-family: monospace; font-size: 13px; max-width: 60%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ok { color: var(--df-color-primary); font-weight: 500; }
.bad { color: var(--df-color-danger); font-weight: 500; }

.proof { margin-top: 16px; border-top: 1px dashed var(--df-border-color); padding-top: 12px; }
.proof-title { font-size: 14px; font-weight: 500; margin-bottom: 8px; color: var(--df-text-primary); }
.proof-row { display: flex; align-items: center; gap: 12px; padding: 6px 0; }
.proof-side {
  min-width: 28px; text-align: center; padding: 2px 8px; border-radius: 4px;
  font-size: 12px;
  &--left { color: var(--df-color-primary); background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); }
  &--right { color: var(--df-color-carbon); background: rgba(43,102,194,0.1); }
}
.proof-hash { color: var(--df-text-secondary); }

.pending { color: var(--df-color-warning); font-size: 14px; }

.note { margin: 0; color: var(--df-text-secondary); font-size: 14px; line-height: 1.8; }
</style>
