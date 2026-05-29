import api from './axios'
export const listTemplates = () => api.get('/templates')
export const createTemplate = (data) => api.post('/templates', data)
export const deleteTemplate = (id) => api.delete(`/templates/${id}`)
export const getTemplate    = (id) => api.get(`/templates/${id}`)
