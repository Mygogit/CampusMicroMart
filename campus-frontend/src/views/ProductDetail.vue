<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
const activeImage = ref(0)

const images = computed<string[]>(() => {
  const raw = product.value?.images
  return raw ? String(raw).split(',').filter(Boolean) : []
})

const maxQty = computed(() => Math.max(Number(product.value?.stock ?? 0), 1))

const publishTime = computed(() => {
  const t = product.value?.createTime
  return t ? String(t).replace('T', ' ').slice(0, 16) : '-'
})

/** 状态徽章：以商品与审核状态推导，避免展示无依据的信任标签 */
const statusBadges = computed(() => {
  const list: { cls: string; text: string; icon: string }[] = []
  const stock = Number(product.value?.stock ?? 0)
  if (product.value?.auditStatus === 1) list.push({ cls: 'green', text: '已通过审核', icon: 'i-shield' })
  if (product.value?.productStatus === 1) list.push({ cls: 'green', text: '在售中', icon: 'i-check' })
  if (product.value?.productStatus === 2) list.push({ cls: 'gray', text: '已售出', icon: 'i-box' })
  if (product.value?.productStatus === 3) list.push({ cls: 'gray', text: '已下架', icon: 'i-box' })
  if (stock > 0 && stock <= 5) list.push({ cls: 'coral', text: `仅剩 ${stock} 件`, icon: 'i-warning' })
  return list
})

async function loadProduct() {
  loading.value = true
  try {
    const res = await productApi.getById(Number(route.params.id))
    product.value = res.data.data || {}
  } finally {
    loading.value = false
  }
}

async function handleBuy() {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  if (!orderForm.value.shippingAddress || !orderForm.value.buyerPhone) {
    ElMessage.warning('请填写收货地址与联系电话')
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
  <div v-if="product.id" v-loading="loading" class="detail fade-up">
    <nav class="crumbs" aria-label="面包屑">
      <router-link to="/">首页</router-link>
      <svg class="icon-sm" aria-hidden="true"><use href="#i-arrow" /></svg>
      <router-link to="/products">商品集市</router-link>
      <svg class="icon-sm" aria-hidden="true"><use href="#i-arrow" /></svg>
      <span class="current">{{ product.name }}</span>
    </nav>

    <div class="detail-grid">
      <!-- 图集 -->
      <div class="gallery">
        <div class="main-img">
          <img v-if="images[activeImage]" :src="images[activeImage]" :alt="product.name" />
          <svg v-else class="ic" aria-hidden="true"><use href="#i-image" /></svg>
        </div>
        <div v-if="images.length > 1" class="thumbs">
          <button
            v-for="(img, i) in images"
            :key="i"
            class="t"
            :class="{ on: i === activeImage }"
            :aria-label="`查看第 ${i + 1} 张图片`"
            @click="activeImage = i"
          >
            <img :src="img" alt="" />
          </button>
        </div>
      </div>

      <!-- 信息 -->
      <div class="info">
        <div class="badge-row">
          <span v-for="b in statusBadges" :key="b.text" class="badge" :class="b.cls">
            <svg class="ic" aria-hidden="true"><use :href="`#${b.icon}`" /></svg>
            {{ b.text }}
          </span>
        </div>

        <h1>{{ product.name }}</h1>

        <div class="price-row">
          <span class="price">
            <small>¥</small>{{ product.price }}
          </span>
          <span class="stock-note">库存 {{ product.stock }} 件</span>
        </div>

        <p class="desc">{{ product.description || '卖家暂未填写商品描述。' }}</p>

        <div class="spec">
          <div class="row"><span>课程代码</span><span>{{ product.courseCode || '未关联' }}</span></div>
          <div class="row"><span>宿舍楼栋</span><span>{{ product.dormitory || '未填写' }}</span></div>
          <div class="row"><span>发布于</span><span>{{ publishTime }}</span></div>
          <div class="row"><span>商品编号</span><span>#{{ product.id }}</span></div>
        </div>

        <div class="assurance">
          <svg class="icon" aria-hidden="true"><use href="#i-shield" /></svg>
          <div class="assurance-text">
            <div class="assurance-title">平台担保交易</div>
            <div class="assurance-sub">下单后经平台支付通道结算，校内实名用户可查信用分</div>
          </div>
        </div>

        <!-- 下单 -->
        <div class="order-form">
          <div class="order-form-head">
            <h2>确认下单</h2>
            <span class="t-xs t-subtle">数量上限受库存约束</span>
          </div>

          <div class="order-field">
            <label for="qty">购买数量</label>
            <el-input-number id="qty" v-model="orderForm.quantity" :min="1" :max="maxQty" />
          </div>

          <div class="order-field">
            <label for="addr">收货地址<span class="req" aria-hidden="true">*</span></label>
            <input id="addr" v-model="orderForm.shippingAddress" class="input" placeholder="例如：东区 3 号宿舍楼 502" />
          </div>

          <div class="order-field">
            <label for="phone">联系电话<span class="req" aria-hidden="true">*</span></label>
            <input id="phone" v-model="orderForm.buyerPhone" class="input" placeholder="便于卖家联系你" />
          </div>

          <div class="order-field">
            <label for="remark">备注</label>
            <textarea id="remark" v-model="orderForm.remark" class="input" placeholder="期望的交接时间、地点等（选填）" />
          </div>

          <div class="buy-row">
            <button class="btn btn-primary lg" :disabled="loading" @click="handleBuy">
              <svg class="icon-sm" aria-hidden="true"><use href="#i-cart" /></svg>
              立即购买
            </button>
            <button class="btn btn-subtle lg" @click="router.back()">返回</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 面包屑 ---------- */
.crumbs {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--space-4);
  color: var(--text-subtle);
  font-size: var(--fs-xs);
}
.crumbs a:hover {
  color: var(--green-600);
}
.crumbs .current {
  max-width: 32ch;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-muted);
}

/* ---------- 主体两栏 ---------- */
.detail-grid {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  gap: var(--space-10);
  align-items: start;
}

.gallery .main-img {
  aspect-ratio: 4 / 3;
  display: grid;
  place-items: center;
  overflow: hidden;
  color: #fff;
  border-radius: var(--radius-lg);
  background: linear-gradient(140deg, var(--green-500), var(--green-300));
  box-shadow: var(--shadow-md);
}
.gallery .main-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.gallery .main-img .ic {
  width: 72px;
  height: 72px;
  opacity: 0.9;
}
.thumbs {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-3);
  flex-wrap: wrap;
}
.thumbs .t {
  width: 72px;
  height: 72px;
  padding: 0;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  transition: border-color var(--dur-fast) var(--ease);
}
.thumbs .t.on {
  border-color: var(--green-500);
}
.thumbs .t img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ---------- 信息区 ---------- */
.info h1 {
  font-size: var(--fs-h1);
  line-height: 1.3;
}
.badge-row {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-bottom: var(--space-3);
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-4);
  margin: var(--space-5) 0;
}
.price {
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 700;
  color: var(--green-600);
  line-height: 1;
}
.price small {
  font-size: 18px;
  margin-right: 2px;
}
.stock-note {
  color: var(--text-subtle);
  font-size: var(--fs-sm);
}
.desc {
  color: var(--text-muted);
  line-height: 1.75;
}

.spec {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: var(--space-5) 0;
}
.spec .row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  font-size: var(--fs-sm);
}
.spec .row span:first-child {
  color: var(--text-subtle);
  flex: none;
}
.spec .row span:last-child {
  min-width: 0;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assurance {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px dashed var(--border);
  border-radius: var(--radius);
}
.assurance > .icon {
  color: var(--green-500);
}
.assurance-title {
  font-weight: 600;
  font-size: var(--fs-sm);
}
.assurance-sub {
  color: var(--text-subtle);
  font-size: var(--fs-xs);
  margin-top: 2px;
}

/* ---------- 下单表单 ---------- */
.order-form {
  margin-top: var(--space-6);
  padding: var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.order-form-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.order-form-head h2 {
  font-size: var(--fs-h2);
}
.order-field {
  margin-bottom: var(--space-4);
}
.order-field > label {
  display: block;
  margin-bottom: 6px;
  font-size: var(--fs-sm);
  font-weight: 600;
}
.buy-row {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-5);
}
.buy-row .btn-primary {
  flex: 1;
}

@media (max-width: 1023px) {
  .detail-grid {
    grid-template-columns: 1fr;
    gap: var(--space-6);
  }
}
@media (max-width: 767px) {
  .spec {
    grid-template-columns: 1fr;
  }
  .price {
    font-size: 28px;
  }
}
</style>
