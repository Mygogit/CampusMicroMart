<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'
import AuthShell from '../components/AuthShell.vue'

const router = useRouter()

const form = ref({ username: '', password: '', nickname: '', phone: '' })
const confirmPwd = ref('')
const agreed = ref(false)
const loading = ref(false)
const showPwd = ref(false)

/** 密码强度：长度 / 大小写混合 / 数字+符号 三段计分 */
const pwdLevel = computed(() => {
  const v = form.value.password
  if (!v) return 0
  let level = 0
  if (v.length >= 8) level++
  if (/[A-Z]/.test(v) && /[a-z]/.test(v)) level++
  if (/[0-9]/.test(v) && /[^A-Za-z0-9]/.test(v)) level++
  return level
})
const pwdLevelText = computed(() => ['弱', '中', '强'][pwdLevel.value - 1] || '弱')

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
  if (confirmPwd.value !== form.value.password) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!agreed.value) {
    ElMessage.warning('请先阅读并同意用户协议与隐私政策')
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
  <AuthShell headline="让闲置，遇见下一个主人" blurb="完成校园实名注册，即可发布闲置、收发消息，与同校同学放心交易。">
    <div class="login-card">
      <h1>创建账号</h1>
      <p class="login-sub">填写基础信息，加入校园集市</p>

      <div class="auth-tabs" role="tablist" aria-label="登录或注册">
        <router-link class="auth-tab" to="/login" role="tab" aria-selected="false">登录</router-link>
        <router-link class="auth-tab" to="/register" role="tab" aria-selected="true">注册</router-link>
      </div>

      <form novalidate @submit.prevent="handleRegister">
        <div class="field-compact">
          <label for="regUsername">用户名<span class="req" aria-hidden="true">*</span></label>
          <input
            id="regUsername"
            v-model="form.username"
            class="input"
            type="text"
            placeholder="至少 3 位，用于登录"
            autocomplete="username"
            required
          />
        </div>

        <div class="field-compact">
          <label for="regNickname">昵称</label>
          <input id="regNickname" v-model="form.nickname" class="input" type="text" placeholder="集市里想被怎么称呼" />
        </div>

        <div class="field-compact">
          <label for="regPhone">手机号</label>
          <input id="regPhone" v-model="form.phone" class="input" type="text" placeholder="13800000000（选填）" autocomplete="tel" />
        </div>

        <div class="field-compact">
          <label for="regPwd">设置密码<span class="req" aria-hidden="true">*</span></label>
          <div class="input-wrap" style="margin-bottom: 0">
            <input
              id="regPwd"
              v-model="form.password"
              class="input has-suf"
              :type="showPwd ? 'text' : 'password'"
              placeholder="至少 6 位"
              autocomplete="new-password"
              aria-describedby="regPwdHint"
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
          <div id="regPwdHint" class="pwd-strength" :data-level="pwdLevel" aria-live="polite">
            <i /><i /><i /><em>{{ pwdLevelText }}</em>
          </div>
        </div>

        <div class="field-compact">
          <label for="regConfirm">确认密码<span class="req" aria-hidden="true">*</span></label>
          <input id="regConfirm" v-model="confirmPwd" class="input" :type="showPwd ? 'text' : 'password'" placeholder="再次输入密码" autocomplete="new-password" required />
        </div>

        <label class="agree">
          <input v-model="agreed" type="checkbox" />
          <span>我已阅读并同意<a href="#" @click.prevent>《用户协议》</a>与<a href="#" @click.prevent>《隐私政策》</a></span>
        </label>

        <button class="btn btn-primary lg block" type="submit" :disabled="loading">
          {{ loading ? '注册中…' : '注册' }}
        </button>
      </form>

      <p class="auth-foot">
        已有账号？
        <router-link to="/login">返回登录</router-link>
      </p>
    </div>
  </AuthShell>
</template>
