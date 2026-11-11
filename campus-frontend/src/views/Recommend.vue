<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '../api/product'
import ProductCard from '../components/ProductCard.vue'

const router = useRouter()

const products = ref<any[]>([])
const loading = ref(false)
const lastRefreshTime = ref<Date>(new Date())
const countdown = ref(180) // 3分钟倒计时
let refreshTimer: ReturnType<typeof setInterval> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null

async function loadProducts() {
  loading.value = true
  try {
    const res = await productApi.search({ page: 1, size: 100 })
    // 只显示已审核通过且在售的商品，按发布时间从早到晚排序
    products.value = (res.data?.data?.records || res.data?.records || [])
      .filter((p: any) => p.auditStatus === 1 && p.productStatus === 1)
      .sort((a: any, b: any) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime())
    lastRefreshTime.value = new Date()
    countdown.value = 180
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProducts()
  // 每3分钟自动刷新
  refreshTimer = setInterval(loadProducts, 3 * 60 * 1000)
  // 每秒更新倒计时
  countdownTimer = setInterval(() => {
    if (countdown.value > 0) {
      countdown.value--
    }
  }, 1000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (countdownTimer) clearInterval(countdownTimer)
})

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="recommend fade-up">
    <!-- 主视觉 -->
    <section class="hero">
      <div class="hero-copy">
        <h1>让闲置，遇见下一个主人</h1>
        <p>校内同学间的二手交易广场。教材、数码、单车、好物，安全又省心。</p>
        <div class="hero-actions">
          <button class="btn btn-primary lg" @click="router.push('/products/create')">发布闲置</button>
          <button class="btn btn-subtle lg" @click="router.push('/products')">浏览全部商品</button>
        </div>
        <div class="hero-stats">
          <div class="stat">
            <b>{{ products.length }}</b>
            <span>在售商品</span>
          </div>
          <div class="stat">
            <b>{{ formatCountdown(countdown) }}</b>
            <span>下次自动刷新</span>
          </div>
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <svg class="icon"><use href="#i-leaf" /></svg>
      </div>
    </section>

    <!-- 推荐区 -->
    <div class="section-head">
      <h2>为你推荐</h2>
      <button class="link-more" @click="router.push('/products')">
        查看全部
        <svg class="icon-sm" aria-hidden="true"><use href="#i-arrow" /></svg>
      </button>
    </div>

    <div class="refresh-bar">
      <span class="refresh-info">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-clock" /></svg>
        最后更新 {{ lastRefreshTime.toLocaleTimeString() }} · 每 3 分钟自动刷新
      </span>
      <button class="btn btn-ghost sm" :disabled="loading" @click="loadProducts">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-refresh" /></svg>
        {{ loading ? '刷新中…' : '立即刷新' }}
      </button>
    </div>

    <div v-loading="loading" class="grid-area">
      <div v-if="products.length === 0 && !loading" class="empty-state">
        <div class="art"><svg class="ic"><use href="#i-inbox" /></svg></div>
        <h3>集市还空着</h3>
        <p>发布第一件闲置，让同学看到你的好物</p>
        <button class="btn btn-primary" @click="router.push('/products/create')">发布闲置</button>
      </div>

      <TransitionGroup v-else name="product-list" tag="div" class="cm-grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </TransitionGroup>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 主视觉 ---------- */
.hero {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  padding: var(--space-10);
  border: 1px solid var(--green-100);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--green-50), #fff 60%);
}
.hero-copy {
  flex: 1;
  min-width: 0;
}
.hero h1 {
  font-size: var(--fs-display);
  max-width: 14ch;
}
.hero p {
  margin: var(--space-4) 0 var(--space-6);
  max-width: 42ch;
  color: var(--text-muted);
}
.hero-actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}
.hero-stats {
  display: flex;
  gap: var(--space-8);
  margin-top: var(--space-8);
  padding-top: var(--space-5);
  border-top: 1px solid var(--green-100);
}
.hero-stats .stat b {
  display: block;
  font-family: var(--font-display);
  font-size: 24px;
  color: var(--green-700);
}
.hero-stats .stat span {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}
.hero-art {
  flex: none;
  width: 160px;
  height: 160px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-lg);
  color: #fff;
  background: linear-gradient(140deg, var(--green-500), var(--green-300));
  box-shadow: var(--shadow-md);
}
.hero-art .icon {
  width: 72px;
  height: 72px;
}

/* ---------- 刷新条 ---------- */
.refresh-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}
.refresh-info {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: var(--fs-xs);
}

/* ---------- 列表 ---------- */
.grid-area {
  min-height: 200px;
}

.product-list-enter-active,
.product-list-leave-active {
  transition: all var(--dur) var(--ease);
}
.product-list-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.product-list-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

@media (max-width: 1023px) {
  .hero {
    padding: var(--space-8);
  }
  .hero-art {
    width: 120px;
    height: 120px;
  }
  .hero-art .icon {
    width: 56px;
    height: 56px;
  }
}
@media (max-width: 767px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    text-align: left;
    padding: var(--space-6);
  }
  .hero-art {
    display: none;
  }
  .hero h1 {
    max-width: none;
  }
}
</style>
