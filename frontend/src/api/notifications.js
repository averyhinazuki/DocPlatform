import api from './axios'
export const getUnread = () => api.get('/notifications')
export const markAllRead = () => api.post('/notifications/read-all')
