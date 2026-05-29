import api from './axios'
export const getDownloadUrl = (documentId) => api.get(`/files/${documentId}/url`)
export const listDocuments = () => api.get('/files')
