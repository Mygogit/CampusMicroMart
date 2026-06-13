<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '../../api/order'

const orders = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const shipDialog = ref(false)
const shipForm = ref({ orderId: 0, trackingNo: '', carrier: '' })

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待支付', type: 'warning' },
  1: { text: '已支付', type: 'primary' },
  2: { text: '已发货', type: 'success' },
  3: { text: '已完成', type: '' },
  4: { text: '已取消', type: 'danger' }
}

async function loadOrders() {
  loading.value = true
  try {
    const res = await orderApi.list(page.value, 10)
    orders.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

function showShipDialog(order: any) {
  shipForm.value = { orderId: order.id, trackingNo: '', carrier: '顺丰快递' }
  shipDialog.value = true
}

async function handleShip() {
  if (!shipForm.value.trackingNo) {
    ElMessage.warning('请输入快递单号')
    return
  }
  try {
    await orderApi.ship(shipForm.value.orderId, shipForm.value.trackingNo, shipForm.value.carrier)
    ElMessage.success('发货成功')
    shipDialog.value = false
    loadOrders()
  } catch {
    ElMessage.error('发货失败')
  }
}

async function handleExport() {
  try {
    const res = await orderApi.exportOrders()
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = `orders_${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

function onPageChange(p: number) {
  page.value = p
  loadOrders()
}

onMounted(loadOrders)
</script>

<template>
  <div>
    <div class="header">
      <h2>订单管理</h2>
      <el-button type="primary" @click="handleExport">导出 CSV</el-button>
    </div>

    <el-table v-loading="loading" :data="orders" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column prop="productName" label="商品" min-width="140" />
      <el-table-column label="金额" width="100">
        <template #default="{ row }">&yen;{{ row.totalAmount || row.price }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="60" />
      <el-table-column prop="shippingAddress" label="收货地址" min-width="140" show-overflow-tooltip />
      <el-table-column prop="buyerPhone" label="电话" width="130" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
            {{ statusMap[row.status]?.text || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 1"
            size="small"
            type="primary"
            @click="showShipDialog(row)"
          >发货</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 10"
      layout="prev, pager, next"
      :total="total"
      :page-size="10"
      :current-page="page"
      @current-change="onPageChange"
      style="justify-content:center;margin-top:20px"
    />

    <el-dialog v-model="shipDialog" title="发货" width="400px">
      <el-form>
        <el-form-item label="快递公司">
          <el-select v-model="shipForm.carrier" style="width:100%">
            <el-option label="顺丰快递" value="顺丰快递" />
            <el-option label="中通快递" value="中通快递" />
            <el-option label="圆通快递" value="圆通快递" />
            <el-option label="韵达快递" value="韵达快递" />
            <el-option label="EMS" value="EMS" />
          </el-select>
        </el-form-item>
        <el-form-item label="快递单号" required>
          <el-input v-model="shipForm.trackingNo" placeholder="请输入快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog = false">取消</el-button>
        <el-button type="primary" @click="handleShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
h2 { margin: 0; }
</style>
