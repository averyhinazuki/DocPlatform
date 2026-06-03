import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const username = ref('')
  const role = ref('')
  let initPromise = null

  function ensureInitialized() {
    if (!initPromise) {
      initPromise = authApi.me()
        .then(res => {
          username.value = res.data.username
          role.value = res.data.role
        })
        .catch(() => {})
    }
    return initPromise
  }

  async function login(credentials) {
    await authApi.login(credentials)
    const res = await authApi.me()
    username.value = res.data.username
    role.value = res.data.role
    initPromise = Promise.resolve()
  }

  async function logout() {
    await authApi.logout()
    username.value = ''
    role.value = ''
    initPromise = Promise.resolve()
  }

  return { username, role, login, logout, ensureInitialized }
})
