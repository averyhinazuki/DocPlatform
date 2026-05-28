import api from './axios'
export const listTenants = () => api.get('/tenants')
export const updateRole = (id, role) => api.patch(`/users/${id}/role`, { role })
