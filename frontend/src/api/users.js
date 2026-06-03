import api from './axios'
export const listUsers = () => api.get('/users')
