<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
  <div class="admin-orders fade-up">
    <div class="page-head">
      <div>
        <h1>订单管理</h1>
        <p class="sub">共 {{ total }} 笔订单 · 已支付订单可在此发货</p>
      </div>
      <div class="head-actions">
        <button class="btn btn-ghost" @click="loadOrders">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-refresh" /></svg>
          刷新列表
        </button>
        <button class="btn btn-primary" @click="handleExport">
          <svg class="icon-sm" aria-hidden="true"><use href="#i-receipt" /></svg>
          导出 CSV
        </button>
      </div>
    </div>

    <div v-loading="loading" class="table-wrap">
      <el-table v-if="orders.length > 0" :data="orders" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <span class="t-price">¥{{ row.totalAmount || row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="shippingAddress" label="收货地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="buyerPhone" label="电话" width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
              {{ statusMap[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" size="small" type="primary" @click="showShipDialog(row)">发货</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else-if="!loading" class="empty-state">
        <div class="art"><svg class="ic"><use href="#i-receipt" /></svg></div>
        <h3>暂无订单数据</h3>
        <p>平台产生交易后，订单会汇总到这里</p>
      </div>
      <div v-else class="table-skeleton" />
    </div>

    <div v-if="total > 10" class="pagination-wrapper">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="10"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <el-dialog v-model="shipDialog" title="订单发货" width="420px">
      <el-form label-width="80px">
        <el-form-item label="快递公司">
          <el-select v-model="shipForm.carrier" style="width: 100%">
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
.head-actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}
.table-wrap {
  min-height: 220px;
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.table-skeleton {
  height: 180px;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}
@media (max-width: 767px) {
  .table-wrap {
    padding: var(--space-3);
  }
}
</style>
