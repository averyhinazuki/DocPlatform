import api from './axios'
export const getDownloadUrl = (documentId) => api.get(`/files/${documentId}/url`)
export const listDocuments = (page = 0, size = 20) => api.get('/files', { params: { page, size } })
export const deleteDocument = (documentId) => api.delete(`/files/${documentId}`)
