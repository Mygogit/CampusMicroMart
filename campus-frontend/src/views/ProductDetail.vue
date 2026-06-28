<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi } from '../api/product'
import { orderApi } from '../api/order'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const product = ref<any>({})
const loading = ref(false)
const orderForm = ref({ quantity: 1, shippingAddress: '', buyerPhone: '', remark: '' })

async function loadProduct() {
  const res = await productApi.getById(Number(route.params.id))
  product.value = res.data.data
}

async function handleBuy() {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  loading.value = true
  try {
    const res = await orderApi.create({
      userId: Number(auth.userId),
      productId: product.value.id,
      productName: product.value.name,
      price: product.value.price,
      quantity: orderForm.value.quantity,
      shippingAddress: orderForm.value.shippingAddress,
      buyerPhone: orderForm.value.buyerPhone,
      remark: orderForm.value.remark
    })
    if (res.data.code === 200) {
      ElMessage.success('下单成功')
      router.push(`/payment/${res.data.data.id}`)
    } else {
      ElMessage.error(res.data.message)
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadProduct)
</script>

<template>
  <div v-if="product.id" class="detail">
    <div class="detail-main">
      <img v-if="product.images" :src="product.images.split(',')[0]" class="detail-image" />
      <div class="detail-info">
        <h1>{{ product.name }}</h1>
        <p class="price">&yen;{{ product.price }}</p>
        <p class="stock">库存: {{ product.stock }}</p>
        <p v-if="product.courseCode">课程代码: {{ product.courseCode }}</p>
        <p v-if="product.dormitory">宿舍: {{ product.dormitory }}</p>
        <p>{{ product.description }}</p>

        <el-divider />
        <h3>下单</h3>
        <el-form label-width="80px">
          <el-form-item label="数量">
            <el-input-number v-model="orderForm.quantity" :min="1" :max="Math.max(product.stock || 0, 1)" />
          </el-form-item>
          <el-form-item label="收货地址">
            <el-input v-model="orderForm.shippingAddress" placeholder="请输入收货地址" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="orderForm.buyerPhone" placeholder="请输入联系电话" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="orderForm.remark" type="textarea" placeholder="备注（选填）" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleBuy">立即购买</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-main {
  display: flex;
  gap: 40px;
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}
.detail-image {
  width: 400px;
  height: 400px;
  object-fit: cover;
  border-radius: 8px;
}
.price {
  color: #f56c6c;
  font-size: 28px;
  font-weight: bold;
}
.stock {
  color: #909399;
}
</style>
