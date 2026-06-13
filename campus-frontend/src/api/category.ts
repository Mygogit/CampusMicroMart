import api from './index'

export const categoryApi = {
  list() {
    return api.get('/category/list')
  },
  create(data: { name: string; parentId?: number; sort?: number; status?: number }) {
    return api.post('/category', data)
  },
  update(data: { id: number; name: string; parentId?: number; sort?: number; status?: number }) {
    return api.put('/category', data)
  },
  delete(id: number) {
    return api.delete(`/category/${id}`)
  }
}
