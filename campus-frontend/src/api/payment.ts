import api from './index'

export const paymentApi = {
  list(page = 1, size = 10) {
    return api.get('/payment/list', { params: { page, size } })
  },
  getById(id: number) {
    return api.get(`/payment/${id}`)
  },
  create(data: { userId: number; orderId: number; orderNo: string; amount: number; paymentMethod: number }) {
    return api.post('/payment', data)
  },
  simulate(paymentId: number) {
    return api.post('/payment/simulate', null, { params: { paymentId } })
  }
}
