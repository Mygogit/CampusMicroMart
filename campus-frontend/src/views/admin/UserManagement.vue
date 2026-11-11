<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../../api/user'

const users = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)

const adminCount = computed(() => users.value.filter(u => u.role === 'ADMIN').length)
const disabledCount = computed(() => users.value.filter(u => u.status === 0).length)

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
  } catch {
    /* 用户取消 */
  }
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
  } catch {
    /* 用户取消 */
  }
}

function onPageChange(p: number) {
  page.value = p
  loadUsers()
}

onMounted(loadUsers)
</script>

<template>
  <div class="users-page fade-up">
    <div class="page-head">
      <div>
        <h1>用户管理</h1>
        <p class="sub">共 {{ total }} 位注册用户 · 本页管理员 {{ adminCount }} 人、禁用 {{ disabledCount }} 人</p>
      </div>
      <button class="btn btn-ghost" @click="loadUsers">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-refresh" /></svg>
        刷新列表
      </button>
    </div>

    <div v-loading="loading" class="table-wrap">
      <el-table v-if="users.length > 0" :data="users" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column label="昵称" min-width="110">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="140">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '学生' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'danger' : 'success'" size="small">
              {{ row.status === 0 ? '已禁用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creditScore" label="信用分" width="90" />
        <el-table-column label="保证金" width="100">
          <template #default="{ row }">¥{{ row.deposit || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
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

      <div v-else-if="!loading" class="empty-state">
        <div class="art"><svg class="ic"><use href="#i-users" /></svg></div>
        <h3>暂无用户数据</h3>
        <p>等待同学注册后即可在此管理</p>
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
