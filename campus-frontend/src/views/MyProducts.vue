<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { productApi } from '../api/product'

const router = useRouter()
const products = ref<any[]>([])
const loading = ref(false)

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '在售', type: 'success' },
  2: { text: '已售出', type: 'info' },
  3: { text: '已下架', type: 'info' },
  4: { text: '已取消', type: 'danger' }
}

const auditMap: Record<number, string> = {
  0: '待审核',
  1: '已通过',
  2: '已拒绝'
}

async function loadProducts() {
  loading.value = true
  try {
    const res = await productApi.myProducts(1, 100)
    const all = res.data.data?.records || []
    products.value = all
  } finally {
    loading.value = false
  }
}

async function handleOffShelf(id: number) {
  try {
    await ElMessageBox.confirm('确认下架该商品？', '提示', { type: 'warning' })
    await productApi.offShelf(id)
    ElMessage.success('已下架')
    loadProducts()
  } catch {
    /* 用户取消 */
  }
}

async function handleCancel(id: number) {
  try {
    await ElMessageBox.confirm('确认取消该商品？', '提示', { type: 'warning' })
    await productApi.cancel(id)
    ElMessage.success('已取消')
    loadProducts()
  } catch {
    /* 用户取消 */
  }
}

onMounted(loadProducts)
</script>

<template>
  <div class="mine-page fade-up">
    <div class="page-head">
      <div>
        <h1>我的发布</h1>
        <p class="sub">共 {{ products.length }} 件商品 · 审核通过后自动上架集市</p>
      </div>
      <button class="btn btn-primary" @click="router.push('/products/create')">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-plus" /></svg>
        发布新商品
      </button>
    </div>

    <div v-loading="loading" class="table-wrap">
      <el-table v-if="products.length > 0" :data="products" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="价格" width="110">
          <template #default="{ row }">
            <span class="t-price">¥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.auditStatus === 1 ? 'success' : row.auditStatus === 2 ? 'danger' : 'warning'" size="small">
              {{ auditMap[row.auditStatus] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.productStatus]?.type || 'info'" size="small">
              {{ statusMap[row.productStatus]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="router.push(`/products/${row.id}`)">查看</el-button>
            <el-button v-if="row.productStatus === 1" size="small" type="warning" @click="handleOffShelf(row.id)">下架</el-button>
            <el-button
              v-if="row.productStatus === 0 || row.productStatus === 1"
              size="small"
              type="danger"
              @click="handleCancel(row.id)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else-if="!loading" class="empty-state">
        <div class="art"><svg class="ic"><use href="#i-box" /></svg></div>
        <h3>还没有发布过商品</h3>
        <p>把宿舍里的闲置拍张照挂上来，让它找到新主人</p>
        <button class="btn btn-primary" @click="router.push('/products/create')">发布闲置</button>
      </div>
      <div v-else class="table-skeleton" />
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
@media (max-width: 767px) {
  .table-wrap {
    padding: var(--space-3);
  }
}
</style>
