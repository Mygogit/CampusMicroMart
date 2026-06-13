<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await userApi.login(form.value)
    if (res.data.code === 200) {
      const token = res.data.data
      const payload = JSON.parse(atob(token.split('.')[1]))
      auth.setAuth(token, payload.role || 'STUDENT', String(payload.userId))
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error(res.data.message)
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-card">
        <div class="login-header">
          <div class="logo-wrapper">
            <el-icon class="logo-icon"><Shop /></el-icon>
          </div>
          <h1 class="login-title">校园微集市</h1>
          <p class="login-subtitle">欢迎回来！请登录你的账号</p>
        </div>

        <el-form @submit.prevent="handleLogin" class="login-form">
          <el-form-item>
            <div class="input-wrapper">
              <el-icon class="input-icon"><User /></el-icon>
              <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
            </div>
          </el-form-item>

          <el-form-item>
            <div class="input-wrapper">
              <el-icon class="input-icon"><Lock /></el-icon>
              <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password size="large" />
            </div>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleLogin" class="login-btn" size="large">
              <el-icon><ArrowRight /></el-icon>
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          <p class="tip">
            还没有账号？
            <router-link to="/register" class="link-btn">立即注册</router-link>
          </p>
        </div>
      </div>

      <div class="decoration decoration-1"></div>
      <div class="decoration decoration-2"></div>
      <div class="decoration decoration-3"></div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px;
}

.login-container {
  position: relative;
  width: 100%;
  max-width: 480px;
}

.login-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  position: relative;
  z-index: 10;
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-wrapper {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
}

.logo-icon {
  font-size: 44px;
  color: white;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.login-subtitle {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.login-form {
  margin-bottom: 24px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 16px;
  z-index: 10;
  font-size: 18px;
  color: #909399;
}

.input-wrapper :deep(.el-input__wrapper) {
  padding-left: 44px !important;
  border-radius: 12px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06) !important;
  transition: all 0.3s;
}

.input-wrapper :deep(.el-input__wrapper:hover) {
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15) !important;
}

.input-wrapper :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.2), 0 4px 12px rgba(102, 126, 234, 0.15) !important;
}

.login-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.35);
  transition: all 0.3s;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px rgba(102, 126, 234, 0.45);
}

.login-footer {
  text-align: center;
}

.tip {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.link-btn {
  color: #667eea;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.3s;
}

.link-btn:hover {
  color: #764ba2;
}

.decoration {
  position: absolute;
  border-radius: 50%;
  opacity: 0.4;
  z-index: 1;
}

.decoration-1 {
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  top: -60px;
  left: -80px;
  filter: blur(20px);
}

.decoration-2 {
  width: 150px;
  height: 150px;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  bottom: -40px;
  right: -50px;
  filter: blur(16px);
}

.decoration-3 {
  width: 100px;
  height: 100px;
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  bottom: 100px;
  left: -40px;
  filter: blur(12px);
}
</style>
