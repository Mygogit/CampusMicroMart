<script setup lang="ts">
import { ref, onMounted } from 'vue'
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
  } catch { /* cancelled */ }
}

async function handleCancel(id: number) {
  try {
    await ElMessageBox.confirm('确认取消该商品？', '提示', { type: 'warning' })
    await productApi.cancel(id)
    ElMessage.success('已取消')
    loadProducts()
  } catch { /* cancelled */ }
}

onMounted(loadProducts)
</script>

<template>
  <div>
    <div class="header">
      <h2>我的商品</h2>
      <el-button type="primary" @click="router.push('/products/create')">发布新商品</el-button>
    </div>

    <el-table v-loading="loading" :data="products" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="商品名称" min-width="160" />
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">&yen;{{ row.price }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.auditStatus === 1 ? 'success' : row.auditStatus === 2 ? 'danger' : 'warning'" size="small">
            {{ auditMap[row.auditStatus] || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商品状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.productStatus]?.type || 'info'" size="small">
            {{ statusMap[row.productStatus]?.text || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="router.push(`/products/${row.id}`)">查看</el-button>
          <el-button
            v-if="row.productStatus === 1"
            size="small"
            type="warning"
            @click="handleOffShelf(row.id)"
          >下架</el-button>
          <el-button
            v-if="row.productStatus === 0 || row.productStatus === 1"
            size="small"
            type="danger"
            @click="handleCancel(row.id)"
          >取消</el-button>
        </template>
      </el-table-column>
    </el-table>

  </div>
</template>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
h2 { margin: 0; }
</style>