<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps<{
  product: any
}>()

const router = useRouter()

const cover = computed(() => {
  const images = props.product?.images
  return images ? String(images).split(',')[0] : ''
})

const stock = computed(() => Number(props.product?.stock ?? 0))

/** 仅剩少量库存才提示，避免全站徽章噪音 */
const stockBadge = computed(() => {
  if (stock.value <= 0) return { cls: 'gray', text: '已售罄' }
  if (stock.value <= 5) return { cls: 'coral', text: `仅剩 ${stock.value} 件` }
  return null
})

function goDetail() {
  router.push(`/products/${props.product.id}`)
}
</script>

<template>
  <article
    class="card product"
    role="button"
    tabindex="0"
    :aria-label="product.name"
    @click="goDetail"
    @keydown.enter="goDetail"
  >
    <div class="thumb">
      <img v-if="cover" :src="cover" :alt="product.name" loading="lazy" />
      <svg v-else class="ic" aria-hidden="true"><use href="#i-image" /></svg>
      <span v-if="stockBadge" class="badge" :class="stockBadge.cls">{{ stockBadge.text }}</span>
    </div>

    <div class="body">
      <h3 class="title" :title="product.name">{{ product.name }}</h3>

      <div class="meta">
        <span class="price">
          <small>¥</small>{{ product.price }}
        </span>
        <span v-if="product.courseCode" class="chip-mini">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-book" /></svg>
          {{ product.courseCode }}
        </span>
      </div>

      <div class="foot">
        <span v-if="product.dormitory" class="seller">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-pin" /></svg>
          {{ product.dormitory }}
        </span>
        <span v-else class="seller">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-shield" /></svg>
          校内面交
        </span>
        <span class="stock">库存 {{ stock }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product {
  padding: 0;
  cursor: pointer;
  container-type: inline-size;
}
.product:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

/* ---------- 图片区 16:10 ---------- */
.thumb {
  position: relative;
  aspect-ratio: 16 / 10;
  display: grid;
  place-items: center;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(140deg, var(--green-300), var(--green-500));
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--dur) var(--ease);
}
.product:hover .thumb img {
  transform: scale(1.03);
}
.thumb .ic {
  width: 44px;
  height: 44px;
  opacity: 0.9;
}
.thumb .badge {
  position: absolute;
  top: 10px;
  left: 10px;
}

/* ---------- 信息区 ---------- */
.body {
  padding: var(--space-4);
}
.title {
  font-size: var(--fs-sm);
  font-weight: 600;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: calc(2 * 1.45em);
}
.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
.price {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--green-600);
  line-height: 1.2;
}
.price small {
  font-size: 12px;
  font-weight: 600;
  margin-right: 1px;
}
.chip-mini {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 52%;
  padding: 3px 8px;
  border-radius: var(--radius-pill);
  background: var(--surface-2);
  color: var(--text-muted);
  font-size: var(--fs-xs);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
  color: var(--text-subtle);
  font-size: var(--fs-xs);
}
.seller {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stock {
  flex: none;
}

/* 容器查询：卡片在窄栏内自动收敛字号 */
@container (max-width: 240px) {
  .price {
    font-size: 18px;
  }
  .body {
    padding: var(--space-3);
  }
}
</style>
