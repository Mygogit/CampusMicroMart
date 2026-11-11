<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const keyword = ref('')

function handleSearch() {
  const q = keyword.value.trim()
  router.push({ path: '/products', query: q ? { q } : {} })
}

function goOrders() {
  router.push('/orders')
}

function handleNotify() {
  ElMessage.info('暂无新的通知')
}
</script>

<template>
  <header class="topbar">
    <form class="search" role="search" @submit.prevent="handleSearch">
      <svg class="icon" aria-hidden="true"><use href="#i-search" /></svg>
      <input
        v-model="keyword"
        type="search"
        placeholder="搜索教材、自行车、电子产品…"
        aria-label="搜索商品"
      />
      <button v-if="keyword" type="button" class="search-clear" aria-label="清空搜索" @click="keyword = ''">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-x" /></svg>
      </button>
    </form>

    <div class="topbar-actions">
      <button class="icon-btn" type="button" aria-label="通知" title="通知" @click="handleNotify">
        <svg class="icon" aria-hidden="true"><use href="#i-bell" /></svg>
        <span class="dot" aria-hidden="true" />
      </button>

      <template v-if="auth.isLoggedIn">
        <button class="icon-btn hide-sm" type="button" aria-label="我的订单" title="我的订单" @click="goOrders">
          <svg class="icon" aria-hidden="true"><use href="#i-receipt" /></svg>
        </button>
      </template>
      <template v-else>
        <button class="btn btn-primary sm" type="button" @click="router.push('/login')">登录 / 注册</button>
      </template>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: var(--space-4);
  min-height: var(--topbar-h);
  padding: var(--space-3) var(--space-8);
  background: rgba(251, 251, 248, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}

/* ---------- 搜索 ---------- */
.search {
  position: relative;
  flex: 1;
  max-width: 520px;
}
.search .icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-subtle);
  pointer-events: none;
}
.search input {
  width: 100%;
  height: 42px;
  padding: 0 42px 0 44px;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  background: var(--surface-2);
  color: var(--text);
  font-family: inherit;
  font-size: var(--fs-sm);
  transition: border-color var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}
.search input::-webkit-search-cancel-button {
  display: none;
}
.search input::placeholder {
  color: var(--text-subtle);
}
.search input:focus {
  outline: none;
  border-color: var(--green-500);
  box-shadow: 0 0 0 3px var(--green-50);
  background: var(--surface);
}
.search-clear {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-pill);
  background: none;
  color: var(--text-subtle);
  transition: all var(--dur-fast) var(--ease);
}
.search-clear:hover {
  background: var(--border);
  color: var(--text);
}

/* ---------- 右侧动作 ---------- */
.topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-left: auto;
}
.icon-btn {
  position: relative;
  width: 42px;
  height: 42px;
  flex: none;
  display: grid;
  place-items: center;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text-muted);
  transition: all var(--dur-fast) var(--ease);
}
.icon-btn:hover {
  color: var(--green-600);
  border-color: var(--green-300);
}
.icon-btn .dot {
  position: absolute;
  top: 9px;
  right: 9px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--coral-500);
  border: 2px solid var(--surface);
}

@media (max-width: 1023px) {
  .topbar {
    padding: var(--space-3) var(--space-6);
  }
}
@media (max-width: 767px) {
  .topbar {
    padding: var(--space-3) var(--space-4);
  }
  .hide-sm {
    display: none;
  }
}
</style>
