<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { orderApi } from '../api/order'
import { paymentApi } from '../api/payment'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const order = ref<any>({})
const loading = ref(false)
const paying = ref(false)
const paymentMethod = ref(1)

const methods = [
  { value: 1, label: '微信支付', icon: 'Wallet' },
  { value: 2, label: '支付宝', icon: 'BankCard' },
  { value: 3, label: '校园卡', icon: 'CreditCard' }
]

async function loadOrder() {
  loading.value = true
  try {
    const res = await orderApi.getById(Number(route.params.orderId))
    order.value = res.data.data || {}
  } finally {
    loading.value = false
  }
}

async function handlePay() {
  paying.value = true
  try {
    const amount = order.value.totalAmount || (order.value.price * order.value.quantity)
    const createRes = await paymentApi.create({
      userId: Number(auth.userId),
      orderId: order.value.id,
      orderNo: order.value.orderNo,
      amount,
      paymentMethod: paymentMethod.value
    })
    if (createRes.data.code !== 200) {
      ElMessage.error(createRes.data.message)
      return
    }
    const paymentId = createRes.data.data.id
    const simRes = await paymentApi.simulate(paymentId)
    if (simRes.data.code === 200) {
      ElMessage.success('支付成功')
      router.push(`/orders/${order.value.id}`)
    } else {
      ElMessage.error(simRes.data.message || '支付失败')
    }
  } finally {
    paying.value = false
  }
}

onMounted(loadOrder)
</script>

<template>
  <div class="payment-page" v-loading="loading">
    <el-card v-if="order.id">
      <h2>订单支付</h2>

      <el-descriptions :column="2" border style="margin:20px 0">
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="商品">{{ order.productName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ order.quantity }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">
          <span class="amount">&yen;{{ order.totalAmount || (order.price * order.quantity) }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider />
      <h3>选择支付方式</h3>
      <el-radio-group v-model="paymentMethod" style="margin:16px 0">
        <el-radio v-for="m in methods" :key="m.value" :value="m.value" style="margin-right:24px">
          {{ m.label }}
        </el-radio>
      </el-radio-group>

      <div>
        <el-button type="primary" size="large" :loading="paying" @click="handlePay">
          确认支付
        </el-button>
        <el-button size="large" @click="router.back()">返回</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.payment-page { max-width: 700px; margin: 0 auto; }
h2, h3 { margin: 0; }
.amount { color: #f56c6c; font-size: 24px; font-weight: bold; }
</style>
