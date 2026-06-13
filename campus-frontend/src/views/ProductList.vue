<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { productApi } from '../api/product'
import { categoryApi } from '../api/category'
import ProductCard from '../components/ProductCard.vue'

const products = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const categories = ref<any[]>([])
const categoryId = ref<number | undefined>()
const courseCode = ref('')
const dormitory = ref('')

async function loadProducts() {
  loading.value = true
  try {
    const res = await productApi.search({
      page: page.value,
      size: 12,
      categoryId: categoryId.value,
      courseCode: courseCode.value || undefined,
      dormitory: dormitory.value || undefined
    })
    products.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  loadProducts()
}

onMounted(() => {
  loadProducts()
  categoryApi.list().then(res => {
    categories.value = res.data.data || []
  }).catch(() => {})
})
</script>

<template>
  <div class="product-list-page">
    <div class="page-header">
      <h1 class="page-title">
        <el-icon><Shop /></el-icon>
        商品集市
      </h1>
      <p class="page-subtitle">发现你想要的，开始校园购物之旅</p>
    </div>

    <div class="search-section">
      <div class="search-wrapper">
        <div class="search-input-group">
          <el-select v-model="categoryId" placeholder="全部分类" clearable size="large" class="search-select" @change="loadProducts">
            <el-option
              v-for="cat in categories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
          <el-input v-model="courseCode" placeholder="课程代码" size="large" class="search-input" @change="loadProducts">
            <template #prefix><el-icon><Document /></el-icon></template>
          </el-input>
          <el-input v-model="dormitory" placeholder="宿舍楼栋" size="large" class="search-input" @change="loadProducts">
            <template #prefix><el-icon><LocationFilled /></el-icon></template>
          </el-input>
        </div>
        <el-button type="primary" size="large" @click="loadProducts" class="search-btn">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
      </div>
    </div>

    <div v-if="products.length === 0 && !loading" class="empty-state">
      <div class="empty-icon">
        <el-icon><Box /></el-icon>
      </div>
      <h3>暂无商品</h3>
      <p>换个搜索条件试试吧</p>
    </div>

    <div v-else class="product-section">
      <div class="product-info-bar">
        <span class="product-count">
          <el-icon><Goods /></el-icon>
          共 {{ total }} 件商品
        </span>
      </div>

      <div v-loading="loading" class="product-grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </div>

      <div v-if="total > 12" class="pagination-wrapper">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="12"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-list-page {
  width: 100%;
}

.page-header {
  text-align: center;
  margin-bottom: 32px;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.page-title .el-icon {
  font-size: 36px;
}

.page-subtitle {
  color: #909399;
  font-size: 15px;
  margin: 0;
}

.search-section {
  margin-bottom: 32px;
}

.search-wrapper {
  background: white;
  padding: 24px;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.search-input-group {
  display: flex;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.search-select {
  min-width: 160px;
}

.search-input {
  flex: 1;
  min-width: 140px;
}

.search-btn {
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  font-weight: 600;
  padding: 0 24px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: all 0.3s;
}

.search-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
}

.empty-state {
  text-align: center;
  padding: 80px 40px;
}

.empty-icon {
  width: 120px;
  height: 120px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
}

.empty-icon .el-icon {
  font-size: 56px;
  color: #909399;
}

.empty-state h3 {
  font-size: 20px;
  color: #606266;
  margin: 0 0 8px;
  font-weight: 600;
}

.empty-state p {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.product-section {
  width: 100%;
}

.product-info-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.product-count {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-weight: 600;
  font-size: 14px;
}

.product-count .el-icon {
  color: #667eea;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid #ebeef5;
}
</style>
