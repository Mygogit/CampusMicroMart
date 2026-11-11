<script setup lang="ts">
import IconSprite from './components/IconSprite.vue'
</script>

<template>
  <IconSprite />
  <router-view />
</template>

<style>
/* 全局仅保留最小必要样式，主题细节见 src/styles/ */
html,
body,
#app {
  min-height: 100vh;
}

#app {
  isolation: isolate;
}
</style>
