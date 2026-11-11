<script setup lang="ts">
/**
 * 登录 / 注册共用的全屏外壳：左侧品牌叙事区 + 右侧表单容器
 * 直接对应 ui-design/prototype.html 中 .auth 的版式
 */
defineProps<{
  headline: string
  blurb: string
}>()
</script>

<template>
  <div class="auth-shell">
    <aside class="aside">
      <div>
        <div class="brand">
          <div class="brand-mark">
            <svg class="icon" aria-hidden="true"><use href="#i-leaf" /></svg>
          </div>
          <div>
            <div class="brand-name">校园集市</div>
            <div class="brand-sub">CAMPUS MICROMART</div>
          </div>
        </div>
        <h2>{{ headline }}</h2>
        <p>{{ blurb }}</p>
      </div>
      <div class="big" aria-hidden="true">
        <svg class="ic"><use href="#i-leaf" /></svg>
      </div>
    </aside>

    <div class="panel">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.brand-mark {
  width: 40px;
  height: 40px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
}
.brand-name {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.2;
}
.brand-sub {
  font-size: var(--fs-xs);
  letter-spacing: 0.04em;
}
</style>
