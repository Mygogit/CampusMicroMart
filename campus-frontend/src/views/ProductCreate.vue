<script setup lang="ts">
import { ref, onMounted } from 'vue'
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

onMounted(async () => {
  try {
    const res = await categoryApi.list()
    categories.value = res.data.data || []
  } catch { /* ignore */ }
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
  <div class="create-page">
    <el-card>
      <h2>发布商品</h2>
      <el-form label-width="100px" style="max-width:600px">
        <el-form-item label="商品名称" required>
          <el-input v-model="form.name" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="商品分类" required>
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width:100%">
            <el-option
              v-for="cat in categories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="商品描述" />
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="form.price" :min="0" :precision="2" style="width:200px" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stock" :min="1" style="width:200px" />
        </el-form-item>
        <el-form-item label="课程代码">
          <el-input v-model="form.courseCode" placeholder="关联课程代码（选填）" />
        </el-form-item>
        <el-form-item label="宿舍楼栋">
          <el-input v-model="form.dormitory" placeholder="所在宿舍楼栋（选填）" />
        </el-form-item>
        <el-form-item label="商品图片">
          <div>
            <div v-if="form.images" class="image-previews">
              <div v-for="(img, i) in form.images.split(',')" :key="i" class="image-preview-item">
                <img :src="img" />
                <el-button size="small" type="danger" :icon="'Delete'" circle @click="removeImage(i)" />
              </div>
            </div>
            <el-upload
              action=""
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              @change="handleUpload"
            >
              <el-button :loading="uploading" type="primary" plain>上传图片</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleCreate">发布商品</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.create-page { max-width: 800px; margin: 0 auto; }
h2 { margin-bottom: 24px; }
.image-previews { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.image-preview-item { position: relative; width: 100px; height: 100px; }
.image-preview-item img { width: 100%; height: 100%; object-fit: cover; border-radius: 4px; }
.image-preview-item .el-button { position: absolute; top: -8px; right: -8px; }
</style>
