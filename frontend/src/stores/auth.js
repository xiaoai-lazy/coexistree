import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token'))
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'SUPER_ADMIN')
  const currentUser = computed(() => user.value)

  function setAuth(authData) {
    token.value = authData.token
    user.value = authData
    localStorage.setItem('token', authData.token)
    localStorage.setItem('user', JSON.stringify(authData))
  }

  function clearAuth() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  async function fetchCurrentUser() {
    try {
      const res = await getCurrentUser()
      if (res.success) {
        user.value = { ...user.value, ...res.data }
        localStorage.setItem('user', JSON.stringify(user.value))
      }
    } catch (err) {
      clearAuth()
      throw err
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    currentUser,
    setAuth,
    clearAuth,
    fetchCurrentUser
  }
})
