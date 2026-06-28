<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'

const router = useRouter()
const form = ref({ username: '', password: '', nickname: '', phone: '' })
const loading = ref(false)

async function handleRegister() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (form.value.username.length < 3) {
    ElMessage.warning('用户名长度至少3位')
    return
  }
  if (form.value.password.length < 6) {
    ElMessage.warning('密码长度至少6位')
    return
  }
  loading.value = true
  try {
    const res = await userApi.register(form.value)
    if (res.data.code === 200) {
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } else {
      ElMessage.error(res.data.message)
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-container">
      <div class="register-card">
        <div class="register-header">
          <div class="logo-wrapper">
            <el-icon class="logo-icon"><UserFilled /></el-icon>
          </div>
          <h1 class="register-title">加入校园微集市</h1>
          <p class="register-subtitle">创建一个账号，开始你的购物之旅</p>
        </div>

        <el-form @submit.prevent="handleRegister" class="register-form">
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
            <div class="input-wrapper">
              <el-icon class="input-icon"><Avatar /></el-icon>
              <el-input v-model="form.nickname" placeholder="昵称（选填）" size="large" />
            </div>
          </el-form-item>

          <el-form-item>
            <div class="input-wrapper">
              <el-icon class="input-icon"><Phone /></el-icon>
              <el-input v-model="form.phone" placeholder="手机号（选填）" size="large" />
            </div>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleRegister" class="register-btn" size="large">
              <el-icon><Check /></el-icon>
              注册
            </el-button>
          </el-form-item>
        </el-form>

        <div class="register-footer">
          <p class="tip">
            已有账号？
            <router-link to="/login" class="link-btn">立即登录</router-link>
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
.register-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px;
}

.register-container {
  position: relative;
  width: 100%;
  max-width: 480px;
}

.register-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  position: relative;
  z-index: 10;
}

.register-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-wrapper {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  box-shadow: 0 8px 24px rgba(17, 153, 142, 0.3);
}

.logo-icon {
  font-size: 44px;
  color: white;
}

.register-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px;
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.register-subtitle {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.register-form {
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
  box-shadow: 0 4px 12px rgba(17, 153, 142, 0.15) !important;
}

.input-wrapper :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px rgba(17, 153, 142, 0.2), 0 4px 12px rgba(17, 153, 142, 0.15) !important;
}

.register-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  border: none;
  box-shadow: 0 6px 20px rgba(17, 153, 142, 0.35);
  transition: all 0.3s;
}

.register-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px rgba(17, 153, 142, 0.45);
}

.register-footer {
  text-align: center;
}

.tip {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.link-btn {
  color: #11998e;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.3s;
}

.link-btn:hover {
  color: #38ef7d;
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
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  top: -60px;
  right: -80px;
  filter: blur(20px);
}

.decoration-2 {
  width: 150px;
  height: 150px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  bottom: -40px;
  left: -50px;
  filter: blur(16px);
}

.decoration-3 {
  width: 100px;
  height: 100px;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  bottom: 100px;
  right: -40px;
  filter: blur(12px);
}
</style>
