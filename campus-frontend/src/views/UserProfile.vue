<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const profile = ref<any>({})
const creditLogs = ref<any[]>([])
const loading = ref(false)
const editing = ref(false)
const editForm = ref({ nickname: '', phone: '', email: '' })

async function loadProfile() {
  loading.value = true
  try {
    const [profileRes, logRes] = await Promise.all([
      userApi.getProfile(),
      userApi.getCreditLog()
    ])
    profile.value = profileRes.data.data || {}
    creditLogs.value = logRes.data.data || []
  } catch {
    ElMessage.error('加载个人信息失败')
  } finally {
    loading.value = false
  }
}

function startEdit() {
  editForm.value = {
    nickname: profile.value.nickname || '',
    phone: profile.value.phone || '',
    email: profile.value.email || ''
  }
  editing.value = true
}

async function handleSave() {
  const res = await userApi.updateProfile(editForm.value)
  if (res.data.code === 200) {
    ElMessage.success('更新成功')
    editing.value = false
    loadProfile()
  } else {
    ElMessage.error(res.data.message)
  }
}

const changeTypeMap: Record<string, string> = {
  REGISTER: '注册奖励',
  SELL: '成功售出',
  BUY: '成功购买',
  CANCEL_ORDER: '取消订单',
  VIOLATION: '违规处罚'
}

onMounted(loadProfile)
</script>

<template>
  <div class="profile-page" v-loading="loading">
    <el-card style="margin-bottom:20px">
      <div class="profile-header">
        <h2>个人信息</h2>
        <el-button v-if="!editing" type="primary" @click="startEdit">编辑</el-button>
      </div>

      <el-descriptions v-if="!editing" :column="2" border>
        <el-descriptions-item label="用户名">{{ profile.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ profile.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ profile.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ profile.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag :type="profile.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
            {{ profile.role === 'ADMIN' ? '管理员' : '学生' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="信用分">{{ profile.creditScore ?? 100 }}</el-descriptions-item>
        <el-descriptions-item label="保证金">&yen;{{ profile.deposit || 0 }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ profile.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-form v-else label-width="80px" style="max-width:400px">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" placeholder="昵称" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSave">保存</el-button>
          <el-button @click="editing = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <h3 style="margin-bottom:16px">信用记录</h3>
      <el-table :data="creditLogs" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ changeTypeMap[row.changeType] || row.changeType }}
          </template>
        </el-table-column>
        <el-table-column label="分值变动" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.scoreChange >= 0 ? '#67c23a' : '#f56c6c' }">
              {{ row.scoreChange >= 0 ? '+' : '' }}{{ row.scoreChange }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="scoreAfter" label="变动后" width="80" />
        <el-table-column prop="reason" label="原因" min-width="150" />
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
      <div v-if="creditLogs.length === 0" class="empty">暂无信用记录</div>
    </el-card>
  </div>
</template>

<style scoped>
.profile-page { max-width: 900px; margin: 0 auto; }
.profile-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
h2, h3 { margin: 0; }
.empty { text-align: center; color: #909399; padding: 30px 0; }
</style>
