<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '../api/order'
import { useRouter } from 'vue-router'

const router = useRouter()
const orders = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待支付', type: 'warning' },
  1: { text: '已支付', type: 'primary' },
  2: { text: '已发货', type: 'success' },
  3: { text: '已完成', type: '' },
  4: { text: '已取消', type: 'danger' }
}

async function loadOrders() {
  loading.value = true
  try {
    const res = await orderApi.myOrders(page.value, 10)
    orders.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

function goPay(order: any) {
  router.push(`/payment/${order.id}`)
}

function goDetail(id: number) {
  router.push(`/orders/${id}`)
}

async function handleCancel(orderId: number) {
  try {
    await ElMessageBox.confirm('确认取消该订单？', '提示', { type: 'warning' })
    await orderApi.cancel(orderId)
    ElMessage.success('已取消')
    loadOrders()
  } catch {
    /* 用户取消 */
  }
}

async function handleConfirm(orderId: number) {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '确认收货', { type: 'warning' })
    await orderApi.confirm(orderId)
    ElMessage.success('确认收货成功')
    loadOrders()
  } catch {
    /* 用户取消 */
  }
}

function onPageChange(p: number) {
  page.value = p
  loadOrders()
}

onMounted(loadOrders)
</script>

<template>
  <div class="orders-page fade-up">
    <div class="page-head">
      <div>
        <h1>我的订单</h1>
        <p class="sub">共 {{ total }} 笔订单 · 待支付订单 15 分钟内未付款将自动关闭</p>
      </div>
      <button class="btn btn-ghost" @click="router.push('/products')">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-grid" /></svg>
        继续逛集市
      </button>
    </div>

    <div v-loading="loading" class="table-wrap">
      <el-table v-if="orders.length > 0" :data="orders" stripe>
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <span class="t-price">¥{{ row.totalAmount || row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
              {{ statusMap[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="goDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 0" size="small" type="primary" @click="goPay(row)">去支付</el-button>
            <el-button v-if="row.status === 2" size="small" type="success" @click="handleConfirm(row.id)">确认收货</el-button>
            <el-button v-if="row.status === 0" size="small" type="danger" @click="handleCancel(row.id)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else-if="!loading" class="empty-state">
        <div class="art"><svg class="ic"><use href="#i-receipt" /></svg></div>
        <h3>还没有订单</h3>
        <p>去集市挑一件心仪的好物，或发布自己的闲置</p>
        <button class="btn btn-primary" @click="router.push('/products')">去逛集市</button>
      </div>
      <div v-else class="table-skeleton" />
    </div>

    <div v-if="total > 10" class="pagination-wrapper">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="10"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.table-wrap {
  min-height: 220px;
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.table-skeleton {
  height: 180px;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}
@media (max-width: 767px) {
  .table-wrap {
    padding: var(--space-3);
  }
}
</style>
