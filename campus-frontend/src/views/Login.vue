<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'
import { useAuthStore } from '../stores/auth'
import AuthShell from '../components/AuthShell.vue'

const router = useRouter()
const auth = useAuthStore()

const REMEMBER_KEY = 'campus_remember_username'

const form = ref({ username: '', password: '' })
const loading = ref(false)
const showPwd = ref(false)
const remember = ref(false)

onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    form.value.username = saved
    remember.value = true
  }
})

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
      const raw = token.split('.')[1]
      const base64 = raw.replace(/-/g, '+').replace(/_/g, '/')
      const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=')
      const payload = JSON.parse(atob(padded))
      auth.setAuth(token, payload.role || 'STUDENT', String(payload.userId))

      if (remember.value) {
        localStorage.setItem(REMEMBER_KEY, form.value.username)
      } else {
        localStorage.removeItem(REMEMBER_KEY)
      }

      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error(res.data.message)
    }
  } finally {
    loading.value = false
  }
}

function handleForgot() {
  ElMessage.info('请携带校园证件到信息中心重置密码')
}
</script>

<template>
  <AuthShell headline="同学，欢迎回到集市" blurb="校内实名、信用可见，买卖更安心。用你的校园账号登录即可开张。">
    <div class="login-card">
      <h1>欢迎回来</h1>
      <p class="login-sub">登录到校园集市，继续你的闲置流转</p>

      <div class="auth-tabs" role="tablist" aria-label="登录或注册">
        <router-link class="auth-tab" to="/login" role="tab" aria-selected="true">登录</router-link>
        <router-link class="auth-tab" to="/register" role="tab" aria-selected="false">注册</router-link>
      </div>

      <form novalidate @submit.prevent="handleLogin">
        <div class="input-wrap">
          <svg class="icon input-pre" aria-hidden="true"><use href="#i-user" /></svg>
          <input
            v-model="form.username"
            class="input has-icon"
            type="text"
            placeholder="学号 / 用户名"
            autocomplete="username"
            aria-label="学号或用户名"
            required
          />
        </div>

        <div class="input-wrap">
          <svg class="icon input-pre" aria-hidden="true"><use href="#i-lock" /></svg>
          <input
            v-model="form.password"
            class="input has-icon has-suf"
            :type="showPwd ? 'text' : 'password'"
            placeholder="密码"
            autocomplete="current-password"
            aria-label="密码"
            required
          />
          <button
            type="button"
            class="input-suf"
            :aria-label="showPwd ? '隐藏密码' : '显示密码'"
            @click="showPwd = !showPwd"
          >
            <svg class="icon" aria-hidden="true"><use :href="showPwd ? '#i-eye-off' : '#i-eye'" /></svg>
          </button>
        </div>

        <div class="login-row">
          <label class="remember">
            <input v-model="remember" type="checkbox" />
            记住用户名
          </label>
          <a class="forgot" href="#" @click.prevent="handleForgot">忘记密码？</a>
        </div>

        <button class="btn btn-primary lg block" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>

        <button class="btn btn-ghost block" type="button" style="margin-top: var(--space-3)" @click="handleForgot">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-shield" /></svg>
          校园统一认证登录
        </button>
      </form>

      <p class="auth-foot">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </p>
    </div>
  </AuthShell>
</template>
