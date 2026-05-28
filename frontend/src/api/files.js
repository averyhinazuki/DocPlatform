import api from './axios'
export const getDownloadUrl = (documentId) => api.get(`/files/${documentId}/url`)
