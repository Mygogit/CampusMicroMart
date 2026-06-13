<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../../api/user'

const users = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)

async function loadUsers() {
  loading.value = true
  try {
    const res = await userApi.listUsers(page.value, 10)
    users.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function handleToggleStatus(user: any) {
  const newStatus = user.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}用户 "${user.username}"？`, '提示', { type: 'warning' })
    await userApi.updateUserStatus(user.id, newStatus)
    ElMessage.success(`已${action}`)
    loadUsers()
  } catch { /* cancelled */ }
}

async function handleChangeRole(user: any) {
  const newRole = user.role === 'ADMIN' ? 'STUDENT' : 'ADMIN'
  const roleText = newRole === 'ADMIN' ? '管理员' : '学生'
  try {
    await ElMessageBox.confirm(
      `确认将 "${user.username}" 的角色改为 ${roleText}？`,
      '提示',
      { type: 'warning' }
    )
    await userApi.updateUserRole(user.id, newRole)
    ElMessage.success('角色已更新')
    loadUsers()
  } catch { /* cancelled */ }
}

function onPageChange(p: number) {
  page.value = p
  loadUsers()
}

onMounted(loadUsers)
</script>

<template>
  <div>
    <h2>用户管理</h2>

    <el-table v-loading="loading" :data="users" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="100">
        <template #default="{ row }">{{ row.nickname || '-' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
            {{ row.role === 'ADMIN' ? '管理员' : '学生' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'danger' : 'success'" size="small">
            {{ row.status === 0 ? '已禁用' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="creditScore" label="信用分" width="80" />
      <el-table-column prop="deposit" label="保证金" width="90">
        <template #default="{ row }">&yen;{{ row.deposit || 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" type="warning" @click="handleChangeRole(row)">
            {{ row.role === 'ADMIN' ? '降为学生' : '升为管理员' }}
          </el-button>
          <el-button
            size="small"
            :type="row.status === 0 ? 'success' : 'danger'"
            @click="handleToggleStatus(row)"
          >
            {{ row.status === 0 ? '启用' : '禁用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 10"
      layout="prev, pager, next"
      :total="total"
      :page-size="10"
      :current-page="page"
      @current-change="onPageChange"
      style="justify-content:center;margin-top:20px"
    />
  </div>
</template>

<style scoped>
h2 { margin-bottom: 20px; }
</style>
