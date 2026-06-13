<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '../api/order'

const route = useRoute()
const router = useRouter()
const order = ref<any>({})
const loading = ref(false)

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待支付', type: 'warning' },
  1: { text: '已支付', type: 'primary' },
  2: { text: '已发货', type: 'success' },
  3: { text: '已完成', type: '' },
  4: { text: '已取消', type: 'danger' }
}

async function loadOrder() {
  loading.value = true
  try {
    const res = await orderApi.getById(Number(route.params.id))
    order.value = res.data.data || {}
  } finally {
    loading.value = false
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确认取消该订单？', '提示', { type: 'warning' })
    await orderApi.cancel(order.value.id)
    ElMessage.success('已取消')
    loadOrder()
  } catch { /* cancelled */ }
}

async function handleConfirm() {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '确认收货', { type: 'warning' })
    await orderApi.confirm(order.value.id)
    ElMessage.success('确认收货成功')
    loadOrder()
  } catch { /* cancelled */ }
}

function goPay() {
  router.push(`/payment/${order.value.id}`)
}

onMounted(loadOrder)
</script>

<template>
  <div v-if="order.id" class="order-detail" v-loading="loading">
    <el-card>
      <div class="order-header">
        <h2>订单详情</h2>
        <el-tag :type="statusMap[order.status]?.type || 'info'" size="large">
          {{ statusMap[order.status]?.text || order.status }}
        </el-tag>
      </div>

      <el-descriptions :column="2" border style="margin-top:20px">
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ order.id }}</el-descriptions-item>
        <el-descriptions-item label="商品名称">{{ order.productName }}</el-descriptions-item>
        <el-descriptions-item label="单价">&yen;{{ order.price }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ order.quantity }}</el-descriptions-item>
        <el-descriptions-item label="总金额">&yen;{{ order.totalAmount || (order.price * order.quantity) }}</el-descriptions-item>
        <el-descriptions-item label="收货地址">{{ order.shippingAddress || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ order.buyerPhone || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ order.remark || '无' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ order.createTime }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ order.payTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="order.shipping" style="margin-top:20px">
        <h3>物流信息</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="快递公司">{{ order.shipping.carrier || '-' }}</el-descriptions-item>
          <el-descriptions-item label="快递单号">{{ order.shipping.trackingNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发货时间">{{ order.shipping.shippedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签收时间">{{ order.shipping.deliveredTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="actions">
        <el-button
          v-if="order.status === 0"
          type="primary"
          @click="goPay"
        >去支付</el-button>
        <el-button
          v-if="order.status === 2"
          type="success"
          @click="handleConfirm"
        >确认收货</el-button>
        <el-button
          v-if="order.status === 0"
          type="danger"
          @click="handleCancel"
        >取消订单</el-button>
        <el-button @click="router.back()">返回</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.order-detail { max-width: 900px; margin: 0 auto; }
.order-header { display: flex; justify-content: space-between; align-items: center; }
h2, h3 { margin: 0; }
.actions { margin-top: 24px; display: flex; gap: 12px; }
</style>
