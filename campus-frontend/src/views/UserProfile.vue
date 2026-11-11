<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'
import { productApi } from '../api/product'
import { orderApi } from '../api/order'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const profile = ref<any>({})
const creditLogs = ref<any[]>([])
const stats = ref({ products: 0, orders: 0 })
const loading = ref(false)
const editing = ref(false)
const editForm = ref({ nickname: '', phone: '', email: '' })

const avatarText = computed(() => String(profile.value.nickname || profile.value.username || '同').slice(0, 1))
const displayName = computed(() => profile.value.nickname || profile.value.username || '校内同学')

async function loadProfile() {
  loading.value = true
  try {
    const profileRes = await userApi.getProfile()
    profile.value = profileRes.data.data || {}

    // 辅助数据失败不应阻断主资料展示
    const [logRes, prodRes, orderRes] = await Promise.all([
      userApi.getCreditLog().catch(() => null),
      productApi.myProducts(1, 1).catch(() => null),
      orderApi.myOrders(1, 1).catch(() => null)
    ])
    creditLogs.value = logRes?.data?.data || []
    stats.value = {
      products: prodRes?.data?.data?.total || 0,
      orders: orderRes?.data?.data?.total || 0
    }
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
  <div v-loading="loading" class="profile-page fade-up">
    <!-- 个人信息横幅 -->
    <section class="profile-head">
      <div class="avatar-lg">{{ avatarText }}</div>
      <div class="info">
        <h1>{{ displayName }}</h1>
        <p>
          <span class="badge" :class="profile.role === 'ADMIN' ? 'coral' : 'green'">
            <svg class="ic" aria-hidden="true"><use href="#i-shield" /></svg>
            {{ profile.role === 'ADMIN' ? '管理员' : '校内实名用户' }}
          </span>
          <span class="t-xs t-subtle">
            用户名 {{ profile.username || '-' }} · 注册于 {{ profile.createTime || '-' }}
          </span>
        </p>
      </div>
      <div class="stats">
        <div class="stat">
          <b>{{ profile.creditScore ?? 100 }}</b>
          <span>信用分</span>
        </div>
        <div class="stat">
          <b>{{ stats.products }}</b>
          <span>发布商品</span>
        </div>
        <div class="stat">
          <b>{{ stats.orders }}</b>
          <span>我的订单</span>
        </div>
      </div>
    </section>

    <div class="panels">
      <!-- 账号资料 -->
      <section class="panel">
        <div class="panel-head">
          <h2>账号资料</h2>
          <button v-if="!editing" class="btn btn-ghost sm" @click="startEdit">编辑资料</button>
        </div>

        <el-descriptions v-if="!editing" :column="1" border>
          <el-descriptions-item label="用户名">{{ profile.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ profile.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ profile.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag :type="profile.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
              {{ profile.role === 'ADMIN' ? '管理员' : '学生' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="保证金">¥{{ profile.deposit || 0 }}</el-descriptions-item>
        </el-descriptions>

        <form v-else @submit.prevent="handleSave">
          <div class="field-compact">
            <label for="eNick">昵称</label>
            <input id="eNick" v-model="editForm.nickname" class="input" placeholder="昵称" />
          </div>
          <div class="field-compact">
            <label for="ePhone">手机号</label>
            <input id="ePhone" v-model="editForm.phone" class="input" placeholder="手机号" />
          </div>
          <div class="field-compact">
            <label for="eMail">邮箱</label>
            <input id="eMail" v-model="editForm.email" class="input" placeholder="邮箱" />
          </div>
          <div class="form-actions">
            <button class="btn btn-primary" type="submit">保存</button>
            <button class="btn btn-subtle" type="button" @click="editing = false">取消</button>
          </div>
        </form>
      </section>

      <!-- 信用记录 -->
      <section class="panel">
        <div class="panel-head">
          <h2>信用记录</h2>
          <span class="t-xs t-subtle">共 {{ creditLogs.length }} 条</span>
        </div>

        <el-table v-if="creditLogs.length > 0" :data="creditLogs" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              {{ changeTypeMap[row.changeType] || row.changeType }}
            </template>
          </el-table-column>
          <el-table-column label="分值变动" width="100">
            <template #default="{ row }">
              <span :class="row.scoreChange >= 0 ? 'delta-up' : 'delta-down'">
                {{ row.scoreChange >= 0 ? '+' : '' }}{{ row.scoreChange }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="scoreAfter" label="变动后" width="90" />
          <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>

        <div v-else class="empty-state" style="padding: var(--space-10) var(--space-4)">
          <div class="art" style="width: 64px; height: 64px">
            <svg class="ic" style="width: 28px; height: 28px"><use href="#i-star" /></svg>
          </div>
          <h3>暂无信用记录</h3>
          <p>完成第一笔交易后，这里会记录你的信用分变化</p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 顶部横幅 ---------- */
.profile-head {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  flex-wrap: wrap;
  padding: var(--space-8);
  margin-bottom: var(--space-6);
  border: 1px solid var(--green-100);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--green-50), #fff 60%);
}
.avatar-lg {
  width: 72px;
  height: 72px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-pill);
  color: #fff;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 600;
  background: linear-gradient(140deg, var(--green-300), var(--green-500));
  box-shadow: var(--shadow-sm);
}
.info {
  flex: 1;
  min-width: 200px;
}
.info h1 {
  font-size: var(--fs-h1);
}
.info p {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-top: var(--space-3);
}
.stats {
  display: flex;
  gap: var(--space-8);
}
.stat {
  text-align: center;
}
.stat b {
  display: block;
  font-family: var(--font-display);
  font-size: 24px;
  color: var(--green-700);
}
.stat span {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

/* ---------- 面板 ---------- */
.panels {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}
.panel-head h2 {
  font-size: var(--fs-h2);
}
.form-actions {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.delta-up {
  color: var(--green-600);
  font-weight: 600;
}
.delta-down {
  color: var(--danger);
  font-weight: 600;
}

@media (max-width: 767px) {
  .profile-head {
    padding: var(--space-6);
  }
  .stats {
    gap: var(--space-6);
  }
}
</style>
