<script setup lang="ts">
import { ref, onMounted } from 'vue'
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
  } catch { /* cancelled */ }
}

async function handleConfirm(orderId: number) {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '确认收货', { type: 'warning' })
    await orderApi.confirm(orderId)
    ElMessage.success('确认收货成功')
    loadOrders()
  } catch { /* cancelled */ }
}

function onPageChange(p: number) {
  page.value = p
  loadOrders()
}

onMounted(loadOrders)
</script>

<template>
  <div>
    <h2>我的订单</h2>

    <el-table v-loading="loading" :data="orders" stripe>
      <el-table-column prop="id" label="订单ID" width="80" />
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column prop="productName" label="商品" min-width="150" />
      <el-table-column label="金额" width="100">
        <template #default="{ row }">&yen;{{ row.totalAmount || row.price }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="60" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
            {{ statusMap[row.status]?.text || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="goDetail(row.id)">详情</el-button>
          <el-button
            v-if="row.status === 0"
            size="small"
            type="primary"
            @click="goPay(row)"
          >去支付</el-button>
          <el-button
            v-if="row.status === 2"
            size="small"
            type="success"
            @click="handleConfirm(row.id)"
          >确认收货</el-button>
          <el-button
            v-if="row.status === 0"
            size="small"
            type="danger"
            @click="handleCancel(row.id)"
          >取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="orders.length === 0 && !loading" class="empty">暂无订单</div>

    <el-pagination
      v-if="total > 10"
      layout="prev, pager, next"
      :total="total"
      :page-size="10"
      :current-page="page"
      @current-change="onPageChange"
      style="justify-content:center;margin-top:20px"
    />
  </div>
</template>

<style scoped>
h2 { margin-bottom: 20px; }
.empty { text-align: center; color: #909399; padding: 60px 0; font-size: 16px; }
</style>
