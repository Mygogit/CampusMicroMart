<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { productApi } from '../api/product'
import ProductCard from '../components/ProductCard.vue'

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
  <div class="recommend-page">
    <div class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="gradient-text">校园微集市</span>
        </h1>
        <p class="hero-subtitle">发现身边的宝藏好物，开启便捷校园交易</p>
        <div class="hero-stats">
          <div class="stat-item">
            <span class="stat-value">{{ products.length }}</span>
            <span class="stat-label">在售商品</span>
          </div>
          <div class="stat-divider" />
          <div class="stat-item">
            <span class="stat-value">{{ formatCountdown(countdown) }}</span>
            <span class="stat-label">下次刷新</span>
          </div>
        </div>
      </div>
    </div>

    <div class="refresh-bar">
      <div class="refresh-info">
        <el-icon class="refresh-icon"><Clock /></el-icon>
        <span>最后更新: {{ lastRefreshTime.toLocaleTimeString() }}</span>
      </div>
      <el-button :loading="loading" size="small" text @click="loadProducts">
        <el-icon><Refresh /></el-icon>
        立即刷新
      </el-button>
    </div>

    <div v-loading="loading" class="product-grid" element-loading-text="正在加载商品...">
      <div v-if="products.length === 0 && !loading" class="empty-state">
        <div class="empty-icon-wrapper">
          <el-icon class="empty-icon"><Box /></el-icon>
        </div>
        <h3>暂无商品</h3>
        <p>快去发布第一个商品吧</p>
      </div>
      <TransitionGroup name="product-list" tag="div" class="grid-wrapper">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </TransitionGroup>
    </div>
  </div>
</template>

<style scoped>
.recommend-page {
  max-width: 1300px;
  margin: 0 auto;
  padding: 0 8px;
}

.hero-section {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%);
  border-radius: 20px;
  padding: 48px 40px;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
}

.hero-section::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 50%;
}

.hero-section::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: 5%;
  width: 200px;
  height: 200px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 50%;
}

.hero-content {
  position: relative;
  z-index: 1;
}

.hero-title {
  font-size: 36px;
  font-weight: 800;
  margin: 0 0 12px;
}

.gradient-text {
  background: linear-gradient(to right, #fff, #f0e6ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  color: rgba(255, 255, 255, 0.85);
  font-size: 16px;
  margin: 0 0 24px;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 24px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: white;
}

.stat-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(255, 255, 255, 0.3);
}

.refresh-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 10px;
  margin-bottom: 20px;
}

.refresh-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-size: 13px;
}

.refresh-icon {
  color: #667eea;
}

.product-grid {
  min-height: 200px;
}

.grid-wrapper {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
}

.empty-icon-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  border-radius: 50%;
  margin-bottom: 16px;
}

.empty-icon {
  font-size: 36px;
  color: #a8b2c1;
}

.empty-state h3 {
  color: #303133;
  margin: 0 0 8px;
}

.empty-state p {
  color: #909399;
  margin: 0;
}

/* 列表过渡动画 */
.product-list-enter-active,
.product-list-leave-active {
  transition: all 0.5s ease;
}

.product-list-enter-from {
  opacity: 0;
  transform: translateY(30px);
}

.product-list-leave-to {
  opacity: 0;
  transform: translateY(-30px);
}
</style>
