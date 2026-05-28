<template>
  <div class="auth-page">
    <div class="card auth-card">
      <h1>Create account</h1>
      <form @submit.prevent="submit">
        <div class="form-group">
          <label>Tenant Slug</label>
          <input v-model="form.tenantSlug" type="text" placeholder="e.g. acme" required />
        </div>
        <div class="form-group">
          <label>Username</label>
          <input v-model="form.username" type="text" placeholder="your username" required />
        </div>
        <div class="form-group">
          <label>Password</label>
          <input v-model="form.password" type="password" placeholder="password" required />
        </div>
        <button class="btn" type="submit" :disabled="loading" style="width:100%">
          {{ loading ? 'Creating account…' : 'Create account' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
        <p class="success-msg" v-if="success">Account created — you can now sign in.</p>
      </form>
      <p class="switch-link">Already have an account? <RouterLink to="/login">Sign in</RouterLink></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { register } from '../api/auth'

const form = reactive({ username: '', password: '', tenantSlug: '' })
const loading = ref(false)
const error = ref('')
const success = ref(false)

async function submit() {
  loading.value = true
  error.value = ''
  success.value = false
  try {
    await register(form)
    success.value = true
    form.username = ''
    form.password = ''
    form.tenantSlug = ''
  } catch (e) {
    error.value = e.response?.data?.message ?? 'Registration failed'
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
