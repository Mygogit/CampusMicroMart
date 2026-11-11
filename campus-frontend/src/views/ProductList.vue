<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '../api/product'
import { categoryApi } from '../api/category'
import ProductCard from '../components/ProductCard.vue'

const PAGE_SIZE = 12

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const page = ref(1)
const total = ref(0)

/** 服务端分页结果 */
const serverProducts = ref<any[]>([])
/** 关键字命中的本地结果（后端 /product/search 未提供 name 模糊查询，故在前端对结果集过滤） */
const keywordHits = ref<any[]>([])

const categories = ref<any[]>([])
const categoryId = ref<number | undefined>()
const courseCode = ref('')
const dormitory = ref('')
const keyword = ref(String(route.query.q || ''))

const hasKeyword = computed(() => keyword.value.trim().length > 0)
const shown = computed(() =>
  hasKeyword.value
    ? keywordHits.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE)
    : serverProducts.value
)

async function loadProducts() {
  loading.value = true
  try {
    const base = {
      categoryId: categoryId.value,
      courseCode: courseCode.value || undefined,
      dormitory: dormitory.value || undefined
    }
    if (hasKeyword.value) {
      // 关键字检索：拉取较大结果集后在本地做模糊匹配 + 分页
      const res = await productApi.search({ page: 1, size: 100, ...base })
      const all: any[] = res.data.data?.records || []
      const k = keyword.value.trim().toLowerCase()
      keywordHits.value = all.filter(
        p =>
          String(p.name || '').toLowerCase().includes(k) ||
          String(p.courseCode || '').toLowerCase().includes(k) ||
          String(p.description || '').toLowerCase().includes(k)
      )
      total.value = keywordHits.value.length
    } else {
      const res = await productApi.search({ page: page.value, size: PAGE_SIZE, ...base })
      serverProducts.value = res.data.data?.records || []
      total.value = res.data.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  loadProducts()
}

function onKeywordInput(e: Event) {
  keyword.value = (e.target as HTMLInputElement).value
}

function selectCategory(id: number | undefined) {
  categoryId.value = id
  applyFilters()
}

function clearKeyword() {
  if (route.query.q) {
    router.push({ path: '/products', query: {} })
  } else {
    keyword.value = ''
    applyFilters()
  }
}

/** 一键重置全部筛选条件 */
function resetFilters() {
  categoryId.value = undefined
  courseCode.value = ''
  dormitory.value = ''
  keyword.value = ''
  if (route.query.q) {
    router.push({ path: '/products', query: {} })
  } else {
    applyFilters()
  }
}

function onPageChange(p: number) {
  page.value = p
  loadProducts()
}

watch(
  () => route.query.q,
  v => {
    keyword.value = String(v || '')
    applyFilters()
  }
)

onMounted(() => {
  loadProducts()
  categoryApi
    .list()
    .then(res => {
      categories.value = res.data.data || []
    })
    .catch(() => {})
})
</script>

<template>
  <div class="list-page fade-up">
    <div class="page-head">
      <div>
        <h1>商品集市</h1>
        <p class="sub">
          <template v-if="hasKeyword">
            「{{ keyword }}」的搜索结果，共 {{ total }} 件
          </template>
          <template v-else>发现你想要的，开始校园购物之旅</template>
        </p>
      </div>
      <button class="btn btn-primary" @click="router.push('/products/create')">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-plus" /></svg>
        发布闲置
      </button>
    </div>

    <!-- 分类快捷筛选 -->
    <div class="chip-group" role="group" aria-label="商品分类">
      <button class="chip" :class="{ active: categoryId === undefined }" @click="selectCategory(undefined)">全部</button>
      <button
        v-for="cat in categories"
        :key="cat.id"
        class="chip"
        :class="{ active: categoryId === cat.id }"
        @click="selectCategory(cat.id)"
      >
        {{ cat.name }}
      </button>
    </div>

    <!-- 精细筛选 -->
    <div class="filter-bar">
      <div class="filter-field">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-search" /></svg>
        <input
          :value="keyword"
          class="input"
          type="search"
          placeholder="搜索商品名称"
          aria-label="搜索商品名称"
          @input="onKeywordInput"
          @keydown.enter="applyFilters"
        />
        <button v-if="hasKeyword" class="filter-clear" type="button" aria-label="清除关键字" @click="clearKeyword">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-x" /></svg>
        </button>
      </div>
      <div class="filter-field">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-book" /></svg>
        <input v-model="courseCode" class="input" placeholder="课程代码" aria-label="课程代码" @keydown.enter="applyFilters" />
      </div>
      <div class="filter-field">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-pin" /></svg>
        <input v-model="dormitory" class="input" placeholder="宿舍楼栋" aria-label="宿舍楼栋" @keydown.enter="applyFilters" />
      </div>
      <button class="btn btn-primary" @click="applyFilters">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-search" /></svg>
        搜索
      </button>
    </div>

    <div class="result-bar">
      <span class="result-count">
        <svg class="icon-sm" aria-hidden="true"><use href="#i-grid" /></svg>
        共 {{ total }} 件商品
      </span>
    </div>

    <div v-loading="loading" class="grid-area">
      <div v-if="shown.length === 0 && !loading" class="empty-state">
        <div class="art"><svg class="ic"><use href="#i-inbox" /></svg></div>
        <h3>没有找到相关商品</h3>
        <p>换个关键字或放宽筛选条件试试</p>
        <button class="btn btn-ghost" @click="resetFilters">重置筛选</button>
      </div>

      <div v-else class="cm-grid">
        <ProductCard v-for="p in shown" :key="p.id" :product="p" />
      </div>
    </div>

    <div v-if="total > PAGE_SIZE" class="pagination-wrapper">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
/* ---------- 分类与筛选 ---------- */
.chip-group {
  margin-bottom: var(--space-5);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-4);
  margin-bottom: var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.filter-field {
  position: relative;
  flex: 1;
  min-width: 180px;
  display: flex;
  align-items: center;
}
.filter-field > .icon-sm {
  position: absolute;
  left: 13px;
  color: var(--text-subtle);
  pointer-events: none;
}
.filter-field .input {
  padding-left: 38px;
}
.filter-clear {
  position: absolute;
  right: 6px;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-pill);
  background: none;
  color: var(--text-subtle);
}
.filter-clear:hover {
  background: var(--border);
  color: var(--text);
}

/* ---------- 结果区 ---------- */
.result-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}
.result-count {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: var(--fs-sm);
  font-weight: 600;
}
.grid-area {
  min-height: 200px;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-10);
  padding-top: var(--space-6);
  border-top: 1px solid var(--border);
}

@media (max-width: 767px) {
  .filter-field {
    min-width: 100%;
  }
}
</style>
