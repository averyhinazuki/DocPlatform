import api from './axios'
export const listSchedules = () => api.get('/schedules')
export const createSchedule = (data) => api.post('/schedules', data)
