<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { userApi } from '../api/user'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const nickname = ref('')

interface NavItem {
  path: string
  label: string
  icon: string
  /** 移动端底部 Tab 仅展示核心项 */
  primary?: boolean
}

const mainNav: NavItem[] = [
  { path: '/', label: '首页', icon: 'i-home', primary: true },
  { path: '/products', label: '浏览市场', icon: 'i-grid', primary: true },
  { path: '/products/create', label: '发布闲置', icon: 'i-plus', primary: true },
  { path: '/products/mine', label: '我的发布', icon: 'i-box' },
  { path: '/orders', label: '我的订单', icon: 'i-receipt', primary: true },
  { path: '/profile', label: '个人中心', icon: 'i-user', primary: true }
]

const adminNav: NavItem[] = [
  { path: '/admin/dashboard', label: '数据概览', icon: 'i-chart' },
  { path: '/admin/audit', label: '商品审核', icon: 'i-list' },
  { path: '/admin/users', label: '用户管理', icon: 'i-users' },
  { path: '/admin/orders', label: '订单管理', icon: 'i-receipt' }
]

/** 取最长匹配前缀，避免 /products 在 /products/create 下也高亮 */
const activePath = computed(() => {
  const all = [...mainNav, ...adminNav]
  const matched = all
    .filter(it => (it.path === '/' ? route.path === '/' : route.path.startsWith(it.path)))
    .sort((a, b) => b.path.length - a.path.length)
  return matched[0]?.path ?? ''
})

const avatarText = computed(() => {
  const name = nickname.value || '同学'
  return name.slice(0, 1)
})

const displayName = computed(() => nickname.value || (auth.isLoggedIn ? '校内同学' : '未登录'))

onMounted(async () => {
  if (!auth.isLoggedIn) return
  try {
    const res = await userApi.getProfile()
    nickname.value = res.data?.data?.nickname || res.data?.data?.username || ''
  } catch {
    /* 侧栏资料为增强信息，失败不影响导航 */
  }
})

function go(path: string) {
  if (route.path !== path) router.push(path)
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出当前账号？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <aside class="sidebar" aria-label="主导航">
    <div class="brand" role="link" tabindex="0" @click="go('/')" @keydown.enter="go('/')">
      <div class="brand-mark">
        <svg class="icon" aria-hidden="true"><use href="#i-leaf" /></svg>
      </div>
      <div class="brand-text">
        <div class="brand-name">校园集市</div>
        <div class="brand-sub">CAMPUS MICROMART</div>
      </div>
    </div>

    <nav class="nav">
      <button
        v-for="item in mainNav"
        :key="item.path"
        class="nav-item"
        :class="{ active: activePath === item.path, 'is-primary': item.primary }"
        :aria-current="activePath === item.path ? 'page' : undefined"
        :title="item.label"
        @click="go(item.path)"
      >
        <svg class="icon" aria-hidden="true"><use :href="`#${item.icon}`" /></svg>
        <span>{{ item.label }}</span>
      </button>

      <template v-if="auth.isAdmin">
        <div class="nav-divider" />
        <div class="nav-caption">管理后台</div>
        <button
          v-for="item in adminNav"
          :key="item.path"
          class="nav-item"
          :class="{ active: activePath === item.path }"
          :aria-current="activePath === item.path ? 'page' : undefined"
          :title="item.label"
          @click="go(item.path)"
        >
          <svg class="icon" aria-hidden="true"><use :href="`#${item.icon}`" /></svg>
          <span>{{ item.label }}</span>
        </button>
      </template>
    </nav>

    <div class="sidebar-foot">
      <template v-if="auth.isLoggedIn">
        <div class="avatar">{{ avatarText }}</div>
        <div class="foot-info">
          <div class="foot-name">{{ displayName }}</div>
          <div class="foot-role">{{ auth.isAdmin ? '管理员' : '校内用户' }}</div>
        </div>
        <button class="foot-action" title="退出登录" aria-label="退出登录" @click="handleLogout">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-logout" /></svg>
        </button>
      </template>
      <template v-else>
        <button class="btn btn-primary sm block" @click="go('/login')">登录 / 注册</button>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-w);
  flex: none;
  height: 100vh;
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-6) var(--space-4);
  background: var(--surface);
  border-right: 1px solid var(--border);
  z-index: 40;
}

/* ---------- 品牌 ---------- */
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-2) var(--space-6);
  cursor: pointer;
}
.brand-mark {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(140deg, var(--green-500), var(--green-300));
  box-shadow: var(--shadow-sm);
}
.brand-name {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 17px;
  line-height: 1.2;
}
.brand-sub {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
  letter-spacing: 0.04em;
}

/* ---------- 导航 ---------- */
.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: var(--space-2);
  overflow-y: auto;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3);
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  text-align: left;
  color: var(--text-muted);
  font-family: inherit;
  font-size: var(--fs-sm);
  font-weight: 500;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}
.nav-item:hover {
  background: var(--surface-2);
  color: var(--text);
}
.nav-item.active {
  background: var(--green-50);
  color: var(--green-700);
  font-weight: 600;
}
.nav-item.active .icon {
  color: var(--green-600);
}
.nav-divider {
  height: 1px;
  background: var(--border);
  margin: var(--space-3) var(--space-2);
}
.nav-caption {
  padding: 0 var(--space-3) var(--space-2);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--text-subtle);
}

/* ---------- 用户区 ---------- */
.sidebar-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
}
.avatar {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-pill);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  background: linear-gradient(140deg, var(--green-300), var(--green-500));
}
.foot-info {
  flex: 1;
  min-width: 0;
}
.foot-name {
  font-size: var(--fs-sm);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.foot-role {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}
.foot-action {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--text-subtle);
  transition: all var(--dur-fast) var(--ease);
}
.foot-action:hover {
  background: var(--surface);
  color: var(--danger);
}

/* ---------- 平板：收为图标栏 ---------- */
@media (max-width: 1023px) {
  .sidebar {
    width: 72px;
    padding: var(--space-6) var(--space-2);
  }
  .brand {
    justify-content: center;
    padding: 0 0 var(--space-6);
  }
  .brand-text,
  .nav-item span,
  .nav-caption,
  .foot-info {
    display: none;
  }
  .nav-item {
    justify-content: center;
  }
  .sidebar-foot {
    justify-content: center;
    padding: var(--space-2);
  }
  .foot-action {
    display: none;
  }
}

/* ---------- 手机：底部 Tab 栏 ---------- */
@media (max-width: 767px) {
  .sidebar {
    position: fixed;
    top: auto;
    bottom: 0;
    left: 0;
    right: 0;
    width: 100%;
    height: auto;
    flex-direction: row;
    justify-content: space-around;
    padding: 8px;
    border-right: none;
    border-top: 1px solid var(--border);
    border-radius: 0;
    z-index: 50;
  }
  .brand,
  .sidebar-foot,
  .nav-divider,
  .nav-item:not(.is-primary) {
    display: none;
  }
  .nav {
    flex-direction: row;
    gap: 0;
    margin: 0;
    overflow: visible;
  }
  .nav-item {
    flex-direction: column;
    gap: 2px;
    font-size: 10px;
    padding: 6px 10px;
    min-width: 56px;
    min-height: 48px;
    justify-content: center;
  }
  .nav-item span {
    display: block;
  }
}
</style>
