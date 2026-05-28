import api from './axios'
export const generateReport = (data) => api.post('/reports/generate', data)
