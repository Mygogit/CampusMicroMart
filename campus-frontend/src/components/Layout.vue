<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'
</script>

<template>
  <div class="app-shell">
    <AppSidebar />
    <div class="app-main">
      <AppTopbar />
      <main class="app-content">
        <div class="content-inner">
          <router-view v-slot="{ Component }">
            <transition name="page" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: var(--bg);
}

.app-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-content {
  flex: 1;
  padding: var(--space-8);
}

.content-inner {
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
}

/* 路由切换：仅做轻微上浮淡入，避免布局跳动 */
.page-enter-active,
.page-leave-active {
  transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}
.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 1023px) {
  .app-content {
    padding: var(--space-6);
  }
}
@media (max-width: 767px) {
  .app-content {
    padding: var(--space-4);
    padding-bottom: 88px; /* 为底部 Tab 栏留出安全区 */
  }
}
</style>
