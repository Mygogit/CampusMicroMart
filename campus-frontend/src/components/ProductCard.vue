<script setup lang="ts">
import { useRouter } from 'vue-router'

const props = defineProps<{
  product: any
}>()

const router = useRouter()

function goDetail() {
  router.push(`/products/${props.product.id}`)
}
</script>

<template>
  <div class="product-card" @click="goDetail">
    <div class="product-image-wrapper">
      <img v-if="product.images" :src="product.images.split(',')[0]" class="product-image" alt="" />
      <div v-else class="product-image-placeholder">
        <el-icon :size="64"><Picture /></el-icon>
      </div>
      <div class="product-badge" v-if="product.stock > 0 && product.stock <= 5">
        <el-icon><Warning /></el-icon>
        仅剩{{ product.stock }}件
      </div>
    </div>
    <div class="product-info">
      <h3 class="product-name">{{ product.name }}</h3>
      <p class="product-price">
        <span class="price-symbol">¥</span>
        <span class="price-value">{{ product.price }}</span>
      </p>
      <div class="product-meta">
        <div class="meta-tags">
          <el-tag v-if="product.courseCode" size="small" class="tag-course">
            <el-icon><Document /></el-icon>
            {{ product.courseCode }}
          </el-tag>
          <el-tag v-if="product.dormitory" size="small" class="tag-dormitory">
            <el-icon><LocationFilled /></el-icon>
            {{ product.dormitory }}
          </el-tag>
        </div>
        <div class="stock-info">
          <el-icon><Box /></el-icon>
          <span>{{ product.stock > 0 ? `库存: ${product.stock}` : '已售罄' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-card {
  cursor: pointer;
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.product-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 32px rgba(102, 126, 234, 0.25);
}

.product-image-wrapper {
  position: relative;
  overflow: hidden;
}

.product-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  transition: transform 0.4s;
}

.product-card:hover .product-image {
  transform: scale(1.08);
}

.product-image-placeholder {
  width: 100%;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  color: #a8b2c1;
}

.product-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  box-shadow: 0 4px 12px rgba(245, 87, 108, 0.3);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

.product-info {
  padding: 16px;
}

.product-name {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  display: flex;
  align-items: baseline;
  margin: 0 0 12px;
}

.price-symbol {
  font-size: 16px;
  color: #f56c6c;
  font-weight: 600;
}

.price-value {
  font-size: 26px;
  font-weight: 700;
  color: #f56c6c;
  margin-left: 2px;
}

.product-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.meta-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tag-course {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: white;
}

.tag-dormitory {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  border: none;
  color: white;
}

.stock-info {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #909399;
  font-size: 13px;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: 6px;
}

.stock-info .el-icon {
  font-size: 14px;
}
</style>
