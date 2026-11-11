<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi } from '../api/product'
import { categoryApi } from '../api/category'

const router = useRouter()

const form = ref({
  name: '',
  description: '',
  price: 0,
  stock: 1,
  categoryId: undefined as number | undefined,
  courseCode: '',
  dormitory: '',
  images: ''
})
const loading = ref(false)
const uploading = ref(false)
const categories = ref<any[]>([])

const imageList = computed(() => (form.value.images ? form.value.images.split(',').filter(Boolean) : []))

onMounted(async () => {
  try {
    const res = await categoryApi.list()
    categories.value = res.data.data || []
  } catch {
    /* 分类加载失败不阻塞发布表单，选择时为空即可 */
  }
})

async function handleUpload(file: any) {
  uploading.value = true
  try {
    const res = await productApi.uploadImage(file.raw)
    const url = res.data.data
    form.value.images = form.value.images ? form.value.images + ',' + url : url
    ElMessage.success('上传成功')
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function removeImage(index: number) {
  const arr = form.value.images.split(',')
  arr.splice(index, 1)
  form.value.images = arr.join(',')
}

async function handleCreate() {
  if (!form.value.name || !form.value.price) {
    ElMessage.warning('请填写商品名称和价格')
    return
  }
  if (!form.value.categoryId) {
    ElMessage.warning('请选择商品分类')
    return
  }
  loading.value = true
  try {
    const res = await productApi.create(form.value)
    if (res.data.code === 200) {
      ElMessage.success('发布成功，等待审核')
      router.push('/products/mine')
    } else {
      ElMessage.error(res.data.message || '发布失败')
    }
  } catch {
    ElMessage.error('发布失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="create-page fade-up">
    <div class="page-head">
      <div>
        <h1>发布闲置</h1>
        <p class="sub">填写越完整，越容易被同学找到。提交后需经管理员审核上架。</p>
      </div>
    </div>

    <div class="publish">
      <div class="panel">
        <div class="field">
          <label for="pName">商品标题<span class="req" aria-hidden="true">*</span><span class="hint">一句话说清是什么、成色如何</span></label>
          <input id="pName" v-model="form.name" class="input" placeholder="例如：九成新《高等数学》第七版 上下册" />
        </div>

        <div class="field">
          <label>商品分类<span class="req" aria-hidden="true">*</span></label>
          <div class="chip-group">
            <button
              v-for="cat in categories"
              :key="cat.id"
              type="button"
              class="chip"
              :class="{ active: form.categoryId === cat.id }"
              :aria-pressed="form.categoryId === cat.id"
              @click="form.categoryId = cat.id"
            >
              {{ cat.name }}
            </button>
            <span v-if="categories.length === 0" class="t-xs t-subtle">分类加载中…</span>
          </div>
        </div>

        <div class="field-row">
          <div class="field">
            <label for="pPrice">价格（元）<span class="req" aria-hidden="true">*</span></label>
            <input id="pPrice" v-model.number="form.price" class="input" type="number" min="0" step="0.01" placeholder="28" />
          </div>
          <div class="field">
            <label for="pStock">可售数量</label>
            <input id="pStock" v-model.number="form.stock" class="input" type="number" min="1" placeholder="1" />
          </div>
        </div>

        <div class="field-row">
          <div class="field">
            <label for="pCourse">课程代码<span class="hint">选填</span></label>
            <input id="pCourse" v-model="form.courseCode" class="input" placeholder="例如：MATH101" />
          </div>
          <div class="field">
            <label for="pDorm">宿舍楼栋<span class="hint">选填</span></label>
            <input id="pDorm" v-model="form.dormitory" class="input" placeholder="例如：东区 3 号楼" />
          </div>
        </div>

        <div class="field">
          <label>商品图片<span class="hint">支持多张，首图将作为封面</span></label>

          <div v-if="imageList.length" class="previews">
            <div v-for="(img, i) in imageList" :key="img + i" class="preview">
              <img :src="img" alt="" />
              <button class="preview-del" type="button" aria-label="删除该图片" @click="removeImage(i)">
                <svg class="icon-sm" aria-hidden="true"><use href="#i-x" /></svg>
              </button>
              <span v-if="i === 0" class="preview-cover">封面</span>
            </div>
          </div>

          <el-upload
            class="uploader"
            drag
            action=""
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            @change="handleUpload"
          >
            <div class="dz">
              <svg class="ic" aria-hidden="true"><use href="#i-cam" /></svg>
              <div class="dz-title">{{ uploading ? '上传中…' : '点击或拖拽图片到此处' }}</div>
              <div class="dz-sub">支持 JPG / PNG，单张建议不超过 10MB</div>
            </div>
          </el-upload>
        </div>

        <div class="field">
          <label for="pDesc">商品描述<span class="hint">转手原因、瑕疵、交接方式</span></label>
          <textarea id="pDesc" v-model="form.description" class="input" placeholder="例如：考研结束转出，几乎全新无笔记，校内可面交。" />
        </div>

        <div class="actions">
          <button class="btn btn-primary lg" :disabled="loading || uploading" @click="handleCreate">
            {{ loading ? '提交中…' : '发布商品' }}
          </button>
          <button class="btn btn-subtle lg" @click="router.back()">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.publish {
  max-width: 720px;
}

.field-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5);
}

/* ---------- 图片预览 ---------- */
.previews {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
}
.preview {
  position: relative;
  width: 104px;
  height: 104px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--border);
}
.preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.preview-del {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-pill);
  background: rgba(42, 47, 44, 0.66);
  color: #fff;
  transition: background var(--dur-fast) var(--ease);
}
.preview-del:hover {
  background: var(--danger);
}
.preview-cover {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 2px 0;
  text-align: center;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: rgba(47, 169, 120, 0.9);
}

/* ---------- 上传区：把 el-upload 的拖拽容器改造成设计系统的 dropzone ---------- */
.uploader :deep(.el-upload) {
  display: block;
  width: 100%;
}
.uploader :deep(.el-upload-dragger) {
  width: 100%;
  padding: var(--space-8);
  border: 2px dashed var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
  transition: all var(--dur-fast) var(--ease);
}
.uploader :deep(.el-upload-dragger:hover),
.uploader :deep(.el-upload-dragger.is-dragover) {
  border-color: var(--green-500);
  background: var(--green-50);
}
.dz {
  color: var(--text-subtle);
}
.dz .ic {
  width: 36px;
  height: 36px;
  margin: 0 auto var(--space-3);
  color: var(--green-500);
  stroke: currentColor;
  stroke-width: 1.8;
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.dz-title {
  font-weight: 600;
  font-size: var(--fs-sm);
  color: var(--text);
}
.dz-sub {
  margin-top: 4px;
  font-size: var(--fs-xs);
}

.actions {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-6);
  padding-top: var(--space-5);
  border-top: 1px solid var(--border);
}

@media (max-width: 767px) {
  .field-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
