<template>
  <div class="page">
    <h1>Admin</h1>

    <div class="card" style="margin-bottom: 24px;">
      <h2>Tenants</h2>
      <div v-if="tenantsLoading" class="empty-state">Loading…</div>
      <div v-else-if="tenants.length === 0 && !tenantsError" class="empty-state">No tenants found.</div>
      <table v-else-if="tenants.length > 0">
        <thead>
          <tr><th>ID</th><th>Name</th><th>Slug</th><th>Plan</th><th>Created</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in tenants" :key="t.id">
            <td>{{ t.id }}</td>
            <td>{{ t.name }}</td>
            <td>{{ t.slug }}</td>
            <td>{{ t.plan ?? '—' }}</td>
            <td>{{ formatDate(t.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="tenantsError">{{ tenantsError }}</p>
    </div>

    <div class="card" style="max-width: 400px; margin-bottom: 24px;">
      <h2>Update User Role</h2>
      <form @submit.prevent="submitRole">
        <div class="form-group">
          <label>User ID</label>
          <input v-model.number="roleForm.userId" type="number" placeholder="2" required />
        </div>
        <div class="form-group">
          <label>Role</label>
          <select v-model="roleForm.role">
            <option value="ADMIN">ADMIN</option>
            <option value="USER">USER</option>
          </select>
        </div>
        <button class="btn" type="submit" :disabled="roleUpdating">
          {{ roleUpdating ? 'Updating…' : 'Update Role' }}
        </button>
        <p class="error-msg" v-if="roleError">{{ roleError }}</p>
        <p class="success-msg" v-if="roleSuccess">Role updated successfully.</p>
      </form>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listTenants, updateRole } from '../api/admin'

const tenants = ref([])
const tenantsLoading = ref(false)
const tenantsError = ref('')

const roleForm = reactive({ userId: null, role: 'USER' })
const roleUpdating = ref(false)
const roleError = ref('')
const roleSuccess = ref(false)

onMounted(loadTenants)

async function loadTenants() {
  tenantsLoading.value = true
  tenantsError.value = ''
  try {
    const res = await listTenants()
    tenants.value = res.data
  } catch (e) {
    tenantsError.value = e.response?.status === 403
      ? 'Access denied — Admin role required'
      : 'Failed to load tenants'
  } finally {
    tenantsLoading.value = false
  }
}

async function submitRole() {
  roleUpdating.value = true
  roleError.value = ''
  roleSuccess.value = false
  try {
    await updateRole(roleForm.userId, roleForm.role)
    roleSuccess.value = true
  } catch (e) {
    roleError.value = e.response?.status === 403
      ? 'Access denied — Admin role required'
      : e.response?.data?.message ?? 'Failed to update role'
  } finally {
    roleUpdating.value = false
  }
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}
</script>

