import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const username = ref('')

  async function login(credentials) {
    await authApi.login(credentials)
    username.value = credentials.username
  }

  async function logout() {
    await authApi.logout()
    username.value = ''
  }

  return { username, login, logout }
})
