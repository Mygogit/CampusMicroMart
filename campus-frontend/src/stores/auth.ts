import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem('token') || '')
  const role = ref(sessionStorage.getItem('role') || '')
  const userId = ref(sessionStorage.getItem('userId') || '')
  const sessionId = ref(sessionStorage.getItem('sessionId') || '')

  // BroadcastChannel 用于跨标签页通信
  let broadcastChannel: BroadcastChannel | null = null
  let conflictCheckTimer: ReturnType<typeof setInterval> | null = null

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  // 生成唯一会话ID
  const generateSessionId = () => {
    return Date.now().toString(36) + Math.random().toString(36).substring(2)
  }

  // 确保 BroadcastChannel 已初始化
  function ensureChannel() {
    if (!broadcastChannel) {
      broadcastChannel = new BroadcastChannel('auth_channel')
      broadcastChannel.onmessage = handleBroadcastMessage
    }
  }

  function setAuth(t: string, r: string, uid: string) {
    ensureChannel()
    const sid = generateSessionId()
    token.value = t
    role.value = r
    userId.value = uid
    sessionId.value = sid
    // 使用 sessionStorage 实现浏览器标签页隔离
    sessionStorage.setItem('token', t)
    sessionStorage.setItem('role', r)
    sessionStorage.setItem('userId', uid)
    sessionStorage.setItem('sessionId', sid)
    // 同时保存到 localStorage 用于跨标签页检测
    localStorage.setItem(`user_${uid}_session`, sid)
    localStorage.setItem(`user_${uid}_lastActive`, Date.now().toString())

    // 通知其他标签页：此账号已在此处登录
    broadcastChannel!.postMessage({
      type: 'login',
      userId: uid,
      sessionId: sid
    })

    // 启动会话冲突检测
    startConflictCheck()
  }

  function logout() {
    const uid = userId.value
    token.value = ''
    role.value = ''
    userId.value = ''
    sessionId.value = ''
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('role')
    sessionStorage.removeItem('userId')
    sessionStorage.removeItem('sessionId')
    if (uid) {
      localStorage.removeItem(`user_${uid}_session`)
      localStorage.removeItem(`user_${uid}_lastActive`)

      // 通知其他标签页：此账号已登出
      if (broadcastChannel) {
        broadcastChannel.postMessage({
          type: 'logout',
          userId: uid
        })
      }
    }
    stopConflictCheck()
  }

  // 启动定时检查会话冲突
  function startConflictCheck() {
    if (conflictCheckTimer) return
    conflictCheckTimer = setInterval(checkSessionConflict, 1000)
  }

  function stopConflictCheck() {
    if (conflictCheckTimer) {
      clearInterval(conflictCheckTimer)
      conflictCheckTimer = null
    }
  }

  // 检查会话是否被其他标签页顶替
  function checkSessionConflict() {
    if (!userId.value || !sessionId.value) return

    const storedSessionId = localStorage.getItem(`user_${userId.value}_session`)
    if (storedSessionId && storedSessionId !== sessionId.value) {
      // 会话被顶替，强制登出
      stopConflictCheck()
      token.value = ''
      role.value = ''
      userId.value = ''
      sessionId.value = ''
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('role')
      sessionStorage.removeItem('userId')
      sessionStorage.removeItem('sessionId')
      alert('您的账号已在其他地方登录，当前页面已自动登出')
      window.location.href = '/login'
    }
  }

  // 处理来自其他标签页的消息
  function handleBroadcastMessage(event: MessageEvent) {
    const { type, userId: msgUserId, sessionId: msgSessionId } = event.data

    if (type === 'login' && msgUserId === userId.value && msgSessionId !== sessionId.value) {
      // 同一账号在其他标签页登录，强制登出
      stopConflictCheck()
      token.value = ''
      role.value = ''
      userId.value = ''
      sessionId.value = ''
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('role')
      sessionStorage.removeItem('userId')
      sessionStorage.removeItem('sessionId')
      alert('您的账号已在其他地方登录，当前页面已自动登出')
      window.location.href = '/login'
    } else if (type === 'logout' && msgUserId === userId.value) {
      // 账号在其他标签页登出，此页面也登出
      stopConflictCheck()
      token.value = ''
      role.value = ''
      userId.value = ''
      sessionId.value = ''
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('role')
      sessionStorage.removeItem('userId')
      sessionStorage.removeItem('sessionId')
      window.location.href = '/login'
    }
  }

  // 初始化：如果已登录则恢复会话检测
  if (token.value) {
    ensureChannel()
    startConflictCheck()
  }

  // 销毁（组件卸载时调用）
  function destroy() {
    stopConflictCheck()
    if (broadcastChannel) {
      broadcastChannel.close()
      broadcastChannel = null
    }
  }

  return { token, role, userId, isLoggedIn, isAdmin, setAuth, logout, destroy }
})
