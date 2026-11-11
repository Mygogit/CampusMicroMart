<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
  } catch {
    /* 用户取消 */
  }
}

async function handleConfirm() {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '确认收货', { type: 'warning' })
    await orderApi.confirm(order.value.id)
    ElMessage.success('确认收货成功')
    loadOrder()
  } catch {
    /* 用户取消 */
  }
}

function goPay() {
  router.push(`/payment/${order.value.id}`)
}

onMounted(loadOrder)
</script>

<template>
  <div v-if="order.id" v-loading="loading" class="order-detail fade-up">
    <div class="page-head">
      <div>
        <h1>订单详情</h1>
        <p class="sub">订单号 {{ order.orderNo }}</p>
      </div>
      <el-tag :type="statusMap[order.status]?.type || 'info'" size="large">
        {{ statusMap[order.status]?.text || order.status }}
      </el-tag>
    </div>

    <div class="panel">
      <div class="amount-band">
        <span class="label">应付金额</span>
        <span class="amount">¥{{ order.totalAmount || (order.price * order.quantity) }}</span>
      </div>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ order.id }}</el-descriptions-item>
        <el-descriptions-item label="商品名称">{{ order.productName }}</el-descriptions-item>
        <el-descriptions-item label="单价">¥{{ order.price }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ order.quantity }}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{ order.totalAmount || (order.price * order.quantity) }}</el-descriptions-item>
        <el-descriptions-item label="收货地址">{{ order.shippingAddress || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ order.buyerPhone || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ order.remark || '无' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ order.createTime }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ order.payTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="order.shipping">
        <div class="section-head" style="margin-top: var(--space-8)">
          <h2>物流信息</h2>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="快递公司">{{ order.shipping.carrier || '-' }}</el-descriptions-item>
          <el-descriptions-item label="快递单号">{{ order.shipping.trackingNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发货时间">{{ order.shipping.shippedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签收时间">{{ order.shipping.deliveredTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <div class="actions">
        <button v-if="order.status === 0" class="btn btn-primary" @click="goPay">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-wallet" /></svg>
          去支付
        </button>
        <button v-if="order.status === 2" class="btn btn-primary" @click="handleConfirm">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-check" /></svg>
          确认收货
        </button>
        <button v-if="order.status === 0" class="btn btn-danger" @click="handleCancel">取消订单</button>
        <button class="btn btn-subtle" @click="router.back()">返回</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.amount-band {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  margin-bottom: var(--space-5);
  border: 1px solid var(--green-100);
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--green-50), #fff 70%);
}
.amount-band .label {
  color: var(--text-muted);
  font-size: var(--fs-sm);
}
.amount-band .amount {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--green-600);
}
.actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-top: var(--space-8);
  padding-top: var(--space-5);
  border-top: 1px solid var(--border);
}
</style>
