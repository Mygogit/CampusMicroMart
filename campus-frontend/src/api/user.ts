import api from './index'

export const userApi = {
  login(data: { username: string; password: string }) {
    return api.post('/user/login', data)
  },
  register(data: { username: string; password: string; nickname?: string; phone?: string }) {
    return api.post('/user/register', data)
  },
  getInfo(userId: number) {
    return api.get(`/user/info/${userId}`)
  },
  getProfile() {
    return api.get('/user/profile')
  },
  updateProfile(data: any) {
    return api.put('/user/profile', data)
  },
  getCreditLog() {
    return api.get('/user/credit/log')
  },
  listUsers(page = 1, size = 10) {
    return api.get('/user/admin/users', { params: { page, size } })
  },
  updateUserStatus(id: number, status: number) {
    return api.put(`/user/admin/users/${id}/status`, null, { params: { status } })
  },
  updateUserRole(id: number, role: string) {
    return api.put(`/user/admin/users/${id}/role`, null, { params: { role } })
  }
}
