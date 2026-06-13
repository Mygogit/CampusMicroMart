import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code === 401) {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('role')
      sessionStorage.removeItem('userId')
      sessionStorage.removeItem('sessionId')
      router.push('/login')
      ElMessage.error(data.message || '登录已过期')
      return Promise.reject(new Error(data.message))
    }
    return response
  },
  error => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('role')
      sessionStorage.removeItem('userId')
      sessionStorage.removeItem('sessionId')
      router.push('/login')
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default api
