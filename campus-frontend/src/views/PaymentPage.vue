<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
  { value: 1, label: '微信支付', desc: '扫码完成支付', icon: 'i-wallet' },
  { value: 2, label: '支付宝', desc: '跳转支付宝完成支付', icon: 'i-bank' },
  { value: 3, label: '校园卡', desc: '从校园卡余额扣款', icon: 'i-card' }
]

const amount = computed(() => order.value.totalAmount || order.value.price * order.value.quantity || 0)

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
    const createRes = await paymentApi.create({
      userId: Number(auth.userId),
      orderId: order.value.id,
      orderNo: order.value.orderNo,
      amount: amount.value,
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
  <div v-if="order.id" v-loading="loading" class="payment-page fade-up">
    <div class="page-head">
      <div>
        <h1>订单支付</h1>
        <p class="sub">订单号 {{ order.orderNo }} · 请在 15 分钟内完成支付</p>
      </div>
      <button class="btn btn-subtle" @click="router.back()">返回</button>
    </div>

    <div class="pay-grid">
      <!-- 订单摘要 -->
      <div class="panel summary">
        <h2>订单摘要</h2>
        <div class="summary-row">
          <span>商品</span>
          <span>{{ order.productName }}</span>
        </div>
        <div class="summary-row">
          <span>单价 × 数量</span>
          <span>¥{{ order.price }} × {{ order.quantity }}</span>
        </div>
        <div class="summary-row">
          <span>收货地址</span>
          <span>{{ order.shippingAddress || '未填写' }}</span>
        </div>
        <div class="summary-total">
          <span>应付金额</span>
          <b>¥{{ amount }}</b>
        </div>
      </div>

      <!-- 支付方式 -->
      <div class="panel method-panel">
        <h2>选择支付方式</h2>
        <div class="methods" role="radiogroup" aria-label="支付方式">
          <button
            v-for="m in methods"
            :key="m.value"
            type="button"
            class="method"
            :class="{ on: paymentMethod === m.value }"
            role="radio"
            :aria-checked="paymentMethod === m.value"
            @click="paymentMethod = m.value"
          >
            <span class="method-icon">
              <svg class="icon" aria-hidden="true"><use :href="`#${m.icon}`" /></svg>
            </span>
            <span class="method-text">
              <span class="method-label">{{ m.label }}</span>
              <span class="method-desc">{{ m.desc }}</span>
            </span>
            <span class="method-dot" aria-hidden="true" />
          </button>
        </div>

        <div class="sim-note">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-alert" /></svg>
          当前为模拟支付通道，确认后直接写入支付成功状态，不产生真实扣款。
        </div>

        <button class="btn btn-primary lg block" :disabled="paying" @click="handlePay">
          {{ paying ? '支付处理中…' : `确认支付 ¥${amount}` }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pay-grid {
  display: grid;
  grid-template-columns: 1fr 1.15fr;
  gap: var(--space-6);
  align-items: start;
}
.panel h2 {
  font-size: var(--fs-h2);
  margin-bottom: var(--space-5);
}

/* ---------- 订单摘要 ---------- */
.summary-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) 0;
  font-size: var(--fs-sm);
  border-bottom: 1px dashed var(--border);
}
.summary-row > span:first-child {
  color: var(--text-subtle);
  flex: none;
}
.summary-row > span:last-child {
  text-align: right;
  word-break: break-all;
}
.summary-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-5);
  padding: var(--space-4);
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--green-50), #fff 70%);
  border: 1px solid var(--green-100);
}
.summary-total span {
  color: var(--text-muted);
  font-size: var(--fs-sm);
}
.summary-total b {
  font-family: var(--font-display);
  font-size: 26px;
  color: var(--green-600);
}

/* ---------- 支付方式 ---------- */
.methods {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}
.method {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  width: 100%;
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  text-align: left;
  font-family: inherit;
  transition: all var(--dur-fast) var(--ease);
}
.method:hover {
  border-color: var(--green-300);
  background: var(--green-50);
}
.method.on {
  border-color: var(--green-500);
  background: var(--green-50);
  box-shadow: 0 0 0 3px var(--green-50);
}
.method-icon {
  width: 40px;
  height: 40px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-muted);
}
.method.on .method-icon {
  background: var(--surface);
  color: var(--green-600);
}
.method-text {
  flex: 1;
  min-width: 0;
}
.method-label {
  display: block;
  font-size: var(--fs-sm);
  font-weight: 600;
}
.method-desc {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}
.method-dot {
  width: 18px;
  height: 18px;
  flex: none;
  border-radius: 50%;
  border: 2px solid var(--border);
  transition: all var(--dur-fast) var(--ease);
}
.method.on .method-dot {
  border-color: var(--green-500);
  background:
    radial-gradient(circle, var(--green-500) 0 4px, transparent 5px),
    var(--surface);
}

.sim-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: var(--space-5);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--amber-500) 12%, #fff);
  color: #8a5e14;
  font-size: var(--fs-xs);
  line-height: 1.6;
}
.sim-note .icon-sm {
  margin-top: 2px;
  flex: none;
}

@media (max-width: 1023px) {
  .pay-grid {
    grid-template-columns: 1fr;
  }
}
</style>
