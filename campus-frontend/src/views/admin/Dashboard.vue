<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { orderApi } from '../../api/order'
import { productApi } from '../../api/product'
import { userApi } from '../../api/user'

const stats = ref({ totalUsers: 0, totalProducts: 0, totalOrders: 0, totalRevenue: 0 })
const loading = ref(false)

async function loadStats() {
  loading.value = true
  try {
    const [userRes, productRes, orderRes] = await Promise.all([
      userApi.listUsers(1, 1),
      productApi.list(1, 1),
      orderApi.list(1, 1)
    ])
    stats.value.totalUsers = userRes.data.data?.total || 0
    stats.value.totalProducts = productRes.data.data?.total || 0
    stats.value.totalOrders = orderRes.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <h2>管理后台</h2>
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ stats.totalUsers }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">商品总数</div>
          <div class="stat-value">{{ stats.totalProducts }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">订单总数</div>
          <div class="stat-value">{{ stats.totalOrders }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">平台收入</div>
          <div class="stat-value">&yen;{{ stats.totalRevenue }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="12">
        <el-card>
          <h3>快捷操作</h3>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/admin/audit')">商品审核</el-button>
            <el-button type="success" @click="$router.push('/admin/users')">用户管理</el-button>
            <el-button type="warning" @click="$router.push('/admin/orders')">订单管理</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <h3>系统信息</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Grafana 监控">
              <a href="http://localhost:3000" target="_blank">http://localhost:3000</a>
            </el-descriptions-item>
            <el-descriptions-item label="Prometheus">
              <a href="http://localhost:9090" target="_blank">http://localhost:9090</a>
            </el-descriptions-item>
            <el-descriptions-item label="Jaeger 链路追踪">
              <a href="http://localhost:16686" target="_blank">http://localhost:16686</a>
            </el-descriptions-item>
            <el-descriptions-item label="Sentinel 控制台">
              <a href="http://localhost:8858" target="_blank">http://localhost:8858</a>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard { max-width: 1200px; margin: 0 auto; }
h2, h3 { margin: 0; }
.stat-card { text-align: center; }
.stat-label { color: #909399; font-size: 14px; margin-bottom: 8px; }
.stat-value { font-size: 32px; font-weight: bold; color: #303133; }
.quick-actions { display: flex; gap: 12px; margin-top: 12px; }
</style>
