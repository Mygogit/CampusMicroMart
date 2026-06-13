<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function go(path: string) {
  router.push(path)
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <nav class="navbar">
    <div class="navbar-container">
      <div class="navbar-brand" @click="go('/')">
        <el-icon class="brand-icon"><Shop /></el-icon>
        <span class="brand-text">校园微集市</span>
      </div>

      <div class="navbar-nav">
        <div class="nav-item" @click="go('/')">
          <el-icon><HomeFilled /></el-icon>
          <span>推荐</span>
        </div>
        <div class="nav-item" @click="go('/products')">
          <el-icon><Shop /></el-icon>
          <span>集市</span>
        </div>
        <template v-if="auth.isLoggedIn">
          <div class="nav-item" @click="go('/products/create')">
            <el-icon><Plus /></el-icon>
            <span>发布</span>
          </div>
          <div class="nav-item" @click="go('/products/mine')">
            <el-icon><Box /></el-icon>
            <span>我的</span>
          </div>
          <div class="nav-item" @click="go('/orders')">
            <el-icon><Tickets /></el-icon>
            <span>订单</span>
          </div>
        </template>
      </div>

      <div class="navbar-actions">
        <template v-if="auth.isAdmin">
          <div class="nav-item admin-badge" @click="go('/admin/dashboard')">
            <el-icon><Operation /></el-icon>
            <span>管理后台</span>
          </div>
        </template>

        <template v-if="auth.isLoggedIn">
          <div class="nav-item" @click="go('/profile')">
            <el-icon><User /></el-icon>
            <span>个人中心</span>
          </div>
          <el-button type="danger" size="small" class="logout-btn" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </template>
        <template v-else>
          <el-button type="primary" size="small" class="login-btn" @click="go('/login')">
            登录
          </el-button>
          <el-button type="success" size="small" @click="go('/register')">
            注册
          </el-button>
        </template>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.navbar-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.3s;
}

.navbar-brand:hover {
  transform: scale(1.02);
}

.brand-icon {
  font-size: 32px;
  color: #667eea;
}

.brand-text {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.navbar-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.nav-item:hover {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.nav-item .el-icon {
  font-size: 18px;
}

.admin-badge {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.admin-badge:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 4px 12px rgba(240, 147, 251, 0.4);
}

.navbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-btn, .logout-btn {
  font-weight: 500;
}
</style>
