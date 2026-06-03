<template>
  <div class="page">
    <h1>Admin</h1>

    <div class="card">
      <h2>Users</h2>
      <div v-if="usersLoading" class="empty-state">Loading…</div>
      <div v-else-if="users.length === 0 && !usersError" class="empty-state">No users found.</div>
      <table v-else-if="users.length > 0">
        <thead>
          <tr><th>ID</th><th>Username</th><th>Role</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>
              <select v-model="pendingRole[u.id]" class="role-select">
                <option value="ADMIN">ADMIN</option>
                <option value="USER">USER</option>
              </select>
            </td>
            <td class="action-cell">
              <button
                class="btn btn-sm"
                :disabled="saving[u.id] || pendingRole[u.id] === u.role"
                @click="saveRole(u)"
              >{{ saving[u.id] ? 'Saving…' : 'Save' }}</button>
              <span v-if="rowSuccess[u.id]" class="success-msg">Saved</span>
              <span v-if="rowError[u.id]" class="error-msg">{{ rowError[u.id] }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="usersError">{{ usersError }}</p>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { updateRole } from '../api/admin'
import { listUsers } from '../api/users'

const users = ref([])
const usersLoading = ref(false)
const usersError = ref('')

const pendingRole = reactive({})
const saving = reactive({})
const rowSuccess = reactive({})
const rowError = reactive({})

onMounted(loadUsers)

async function loadUsers() {
  usersLoading.value = true
  usersError.value = ''
  try {
    const res = await listUsers()
    users.value = res.data
    res.data.forEach(u => { pendingRole[u.id] = u.role })
  } catch (e) {
    usersError.value = e.response?.status === 403
      ? 'Access denied — Admin role required'
      : 'Failed to load users'
  } finally {
    usersLoading.value = false
  }
}

async function saveRole(u) {
  saving[u.id] = true
  rowError[u.id] = ''
  rowSuccess[u.id] = false
  try {
    await updateRole(u.id, pendingRole[u.id])
    u.role = pendingRole[u.id]
    rowSuccess[u.id] = true
    setTimeout(() => { rowSuccess[u.id] = false }, 2000)
  } catch (e) {
    rowError[u.id] = e.response?.data?.message ?? 'Failed to update role'
  } finally {
    saving[u.id] = false
  }
}
</script>

<style scoped>
.role-select {
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid #ccc;
  font-size: 0.875rem;
}
.action-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.btn-sm {
  padding: 4px 12px;
  font-size: 0.8125rem;
}
</style>
