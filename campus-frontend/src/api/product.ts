import api from './index'

export const productApi = {
  list(page = 1, size = 10) {
    return api.get('/product/list', { params: { page, size } })
  },
  search(params: { page?: number; size?: number; categoryId?: number; courseCode?: string; dormitory?: string }) {
    return api.get('/product/search', { params })
  },
  myProducts(page = 1, size = 10) {
    return api.get('/product/my', { params: { page, size } })
  },
  getById(id: number) {
    return api.get(`/product/${id}`)
  },
  create(data: any) {
    return api.post('/product', data)
  },
  update(data: any) {
    return api.put('/product', data)
  },
  delete(id: number) {
    return api.delete(`/product/${id}`)
  },
  audit(data: { productId: number; approved: boolean; reason?: string }) {
    return api.post('/product/audit', data)
  },
  getPending(page = 1, size = 10) {
    return api.get('/product/pending', { params: { page, size } })
  },
  offShelf(id: number) {
    return api.put(`/product/${id}/off-shelf`)
  },
  cancel(id: number) {
    return api.post(`/product/${id}/cancel`)
  },
  uploadImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/product/upload/image', formData)
  }
}
