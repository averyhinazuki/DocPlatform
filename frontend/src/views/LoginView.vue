<template>
  <div class="auth-page">
    <div class="card auth-card">
      <h1>Sign in</h1>
      <form @submit.prevent="submit">
        <div class="form-group">
          <label>Username</label>
          <input v-model="form.username" type="text" placeholder="your username" required />
        </div>
        <div class="form-group">
          <label>Password</label>
          <input v-model="form.password" type="password" placeholder="password" required />
        </div>
        <button class="btn" type="submit" :disabled="loading" style="width:100%">
          {{ loading ? 'Signing in…' : 'Sign in' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
      </form>
      <p class="switch-link">No account? <RouterLink to="/register">Register</RouterLink></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await authStore.login(form)
    router.push('/dashboard')
  } catch (e) {
    error.value = e.response?.data?.message ?? 'Invalid username or password'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; }
.auth-card { width: 100%; max-width: 380px; }
h1 { font-size: 24px; text-align: center; margin-bottom: 28px; }
.btn { margin-top: 8px; }
.switch-link { text-align: center; margin-top: 20px; font-size: 14px; color: var(--text-2); }
.switch-link a { color: var(--accent); text-decoration: none; font-weight: 500; }
</style>
