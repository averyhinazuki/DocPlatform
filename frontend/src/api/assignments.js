import api from './axios'

export const getMyAssignments = () => api.get('/assignments/mine')
export const listAssignments = () => api.get('/assignments')
export const createAssignment = (data) => api.post('/assignments', data)
