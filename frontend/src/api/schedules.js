import api from './axios'
export const listSchedules = () => api.get('/schedules')
export const createSchedule = (data) => api.post('/schedules', data)
export const deleteSchedule = (id) => api.delete(`/schedules/${id}`)
