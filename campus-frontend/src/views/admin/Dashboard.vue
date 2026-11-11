<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi } from '../../api/order'
import { productApi } from '../../api/product'
import { userApi } from '../../api/user'

const router = useRouter()

const stats = ref({ totalUsers: 0, totalProducts: 0, totalOrders: 0, pendingProducts: 0 })
const loading = ref(false)

const cards = [
  { key: 'totalUsers', label: '注册用户', icon: 'i-users', to: '/admin/users' },
  { key: 'totalProducts', label: '平台商品', icon: 'i-box', to: '/admin/audit' },
  { key: 'totalOrders', label: '累计订单', icon: 'i-receipt', to: '/admin/orders' },
  { key: 'pendingProducts', label: '待审核商品', icon: 'i-list', to: '/admin/audit' }
] as const

async function loadStats() {
  loading.value = true
  try {
    const [userRes, productRes, orderRes, pendingRes] = await Promise.all([
      userApi.listUsers(1, 1),
      productApi.list(1, 1),
      orderApi.list(1, 1),
      productApi.getPending(1, 1).catch(() => null)
    ])
    stats.value.totalUsers = userRes.data.data?.total || 0
    stats.value.totalProducts = productRes.data.data?.total || 0
    stats.value.totalOrders = orderRes.data.data?.total || 0
    stats.value.pendingProducts = pendingRes?.data?.data?.total || 0
  } finally {
    loading.value = false
  }
}

const monitors = [
  { name: 'Grafana 监控', url: 'http://localhost:3001' },
  { name: 'Prometheus', url: 'http://localhost:9090' },
  { name: 'Jaeger 链路追踪', url: 'http://localhost:16686' },
  { name: 'Sentinel 控制台', url: 'http://localhost:8858' }
]

onMounted(loadStats)
</script>

<template>
  <div v-loading="loading" class="dashboard fade-up">
    <div class="page-head">
      <div>
        <h1>数据概览</h1>
        <p class="sub">平台核心指标与治理入口</p>
      </div>
      <button class="btn btn-ghost" @click="loadStats">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-refresh" /></svg>
        刷新数据
      </button>
    </div>

    <div class="stat-grid">
      <button v-for="c in cards" :key="c.key" class="stat-card" @click="router.push(c.to)">
        <span class="stat-icon">
          <svg class="icon" aria-hidden="true"><use :href="`#${c.icon}`" /></svg>
        </span>
        <span class="stat-body">
          <b>{{ stats[c.key] }}</b>
          <span>{{ c.label }}</span>
        </span>
      </button>
    </div>

    <div class="lower-grid">
      <section class="panel">
        <div class="panel-head">
          <h2>快捷操作</h2>
        </div>
        <div class="quick-actions">
          <button class="btn btn-primary" @click="router.push('/admin/audit')">
            <svg class="icon-sm" aria-hidden="true"><use href="#i-check" /></svg>
            商品审核
          </button>
          <button class="btn btn-ghost" @click="router.push('/admin/users')">
            <svg class="icon-sm" aria-hidden="true"><use href="#i-users" /></svg>
            用户管理
          </button>
          <button class="btn btn-ghost" @click="router.push('/admin/orders')">
            <svg class="icon-sm" aria-hidden="true"><use href="#i-receipt" /></svg>
            订单管理
          </button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>可观测性入口</h2>
        </div>
        <ul class="monitors">
          <li v-for="m in monitors" :key="m.name">
            <span>{{ m.name }}</span>
            <a :href="m.url" target="_blank" rel="noopener">{{ m.url }}</a>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 指标卡 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-5);
  margin-bottom: var(--space-6);
}
.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  text-align: left;
  font-family: inherit;
  transition: all var(--dur) var(--ease);
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--green-300);
}
.stat-icon {
  width: 44px;
  height: 44px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--green-50);
  color: var(--green-600);
}
.stat-body b {
  display: block;
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1.2;
  color: var(--text);
}
.stat-body span {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

/* ---------- 下半区 ---------- */
.lower-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-6);
}
.panel-head {
  margin-bottom: var(--space-5);
}
.panel-head h2 {
  font-size: var(--fs-h2);
}
.quick-actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.monitors {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.monitors li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) 0;
  border-bottom: 1px dashed var(--border);
  font-size: var(--fs-sm);
}
.monitors li:last-child {
  border-bottom: none;
}
.monitors a {
  color: var(--green-600);
  font-weight: 500;
  font-size: var(--fs-xs);
  word-break: break-all;
}
.monitors a:hover {
  text-decoration: underline;
}

@media (max-width: 1023px) {
  .lower-grid {
    grid-template-columns: 1fr;
  }
}
</style>
