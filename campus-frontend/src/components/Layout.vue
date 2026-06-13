<script setup lang="ts">
import Navbar from './Navbar.vue'
</script>

<template>
  <div class="layout">
    <Navbar />
    <main class="main-content">
      <div class="content-wrapper">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.main-content {
  padding: 24px;
  min-height: calc(100vh - 64px);
}

.content-wrapper {
  max-width: 1400px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  min-height: calc(100vh - 112px);
}
</style>
