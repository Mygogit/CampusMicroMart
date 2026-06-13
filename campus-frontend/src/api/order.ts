import api from './index'

export const orderApi = {
  list(page = 1, size = 10) {
    return api.get('/order/list', { params: { page, size } })
  },
  myOrders(page = 1, size = 10) {
    return api.get('/order/my', { params: { page, size } })
  },
  getById(id: number) {
    return api.get(`/order/${id}`)
  },
  create(data: any) {
    return api.post('/order', data)
  },
  cancel(orderId: number) {
    return api.post('/order/cancel', null, { params: { orderId } })
  },
  ship(id: number, trackingNo: string, carrier: string) {
    return api.post(`/order/${id}/ship`, null, { params: { trackingNo, carrier } })
  },
  confirm(id: number) {
    return api.post(`/order/${id}/confirm`)
  },
  updateStatus(orderId: number, status: number) {
    return api.put('/order/status', null, { params: { orderId, status } })
  },
  update(data: { id: number; remark: string }) {
    return api.put('/order', data)
  },
  delete(id: number) {
    return api.delete(`/order/${id}`)
  },
  batchUpdateStatus(data: { orderIds: number[]; status: number }) {
    return api.post('/order/batch/status', data)
  },
  exportOrders() {
    return api.get('/order/export', { responseType: 'blob' })
  },
  exportOrdersExcel() {
    return api.get('/order/export/excel', { responseType: 'blob' })
  }
}
