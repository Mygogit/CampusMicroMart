<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi } from '../../api/product'

const products = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const reasonDialog = ref(false)
const reason = ref('')
const currentId = ref(0)
const currentAction = ref<'approve' | 'reject'>('approve')

async function loadPending() {
  loading.value = true
  try {
    const res = await productApi.getPending(page.value, 10)
    products.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

function showApprove(id: number) {
  currentId.value = id
  currentAction.value = 'approve'
  reason.value = ''
  reasonDialog.value = true
}

function showReject(id: number) {
  currentId.value = id
  currentAction.value = 'reject'
  reason.value = ''
  reasonDialog.value = true
}

async function handleAudit() {
  if (currentAction.value === 'reject' && !reason.value) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  try {
    await productApi.audit({
      productId: currentId.value,
      approved: currentAction.value === 'approve',
      reason: reason.value || undefined
    })
    ElMessage.success(currentAction.value === 'approve' ? '已通过审核' : '已拒绝')
    reasonDialog.value = false
    loadPending()
  } catch {
    ElMessage.error('操作失败')
  }
}

function onPageChange(p: number) {
  page.value = p
  loadPending()
}

onMounted(loadPending)
</script>

<template>
  <div>
    <h2>商品审核</h2>

    <el-table v-loading="loading" :data="products" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="商品名称" min-width="150" />
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">&yen;{{ row.price }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="70" />
      <el-table-column label="课程代码" width="120">
        <template #default="{ row }">{{ row.courseCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="宿舍" width="100">
        <template #default="{ row }">{{ row.dormitory || '-' }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="showApprove(row.id)">通过</el-button>
          <el-button size="small" type="danger" @click="showReject(row.id)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="products.length === 0 && !loading" class="empty">暂无待审核商品</div>

    <el-pagination
      v-if="total > 10"
      layout="prev, pager, next"
      :total="total"
      :page-size="10"
      :current-page="page"
      @current-change="onPageChange"
      style="justify-content:center;margin-top:20px"
    />

    <el-dialog
      v-model="reasonDialog"
      :title="currentAction === 'approve' ? '确认通过' : '拒绝原因'"
      width="400px"
    >
      <el-input
        v-if="currentAction === 'reject'"
        v-model="reason"
        type="textarea"
        placeholder="请输入拒绝原因"
      />
      <p v-else>确认通过该商品的审核？</p>
      <template #footer>
        <el-button @click="reasonDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAudit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
h2 { margin-bottom: 20px; }
.empty { text-align: center; color: #909399; padding: 60px 0; font-size: 16px; }
</style>
