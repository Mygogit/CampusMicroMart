<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
  <div class="audit-page fade-up">
    <div class="page-head">
      <div>
        <h1>商品审核</h1>
        <p class="sub">共 {{ total }} 件待审核商品 · 通过后即时上架集市</p>
      </div>
      <button class="btn btn-ghost" @click="loadPending">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-refresh" /></svg>
        刷新列表
      </button>
    </div>

    <div v-loading="loading" class="table-wrap">
      <el-table v-if="products.length > 0" :data="products" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">
            <span class="t-price">¥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column label="课程代码" width="120">
          <template #default="{ row }">{{ row.courseCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="宿舍" width="120">
          <template #default="{ row }">{{ row.dormitory || '-' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="showApprove(row.id)">通过</el-button>
            <el-button size="small" type="danger" @click="showReject(row.id)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else-if="!loading" class="empty-state">
        <div class="art"><svg class="ic"><use href="#i-check" /></svg></div>
        <h3>没有待审核商品</h3>
        <p>所有提交都已处理完毕，稍后再来看看</p>
      </div>
      <div v-else class="table-skeleton" />
    </div>

    <div v-if="total > 10" class="pagination-wrapper">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="10"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <el-dialog
      v-model="reasonDialog"
      :title="currentAction === 'approve' ? '确认通过' : '拒绝原因'"
      width="420px"
    >
      <el-input
        v-if="currentAction === 'reject'"
        v-model="reason"
        type="textarea"
        :rows="3"
        placeholder="请说明拒绝原因，将同步给卖家"
      />
      <p v-else>确认通过该商品的审核？通过后将立即上架集市。</p>
      <template #footer>
        <el-button @click="reasonDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAudit">确认</el-button>
      </template>
    </el-dialog>
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
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}
@media (max-width: 767px) {
  .table-wrap {
    padding: var(--space-3);
  }
}
</style>
