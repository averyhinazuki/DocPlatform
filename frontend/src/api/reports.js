import api from './axios'

export const generateReport = (payload, file = null) => {
  const fd = new FormData()
  fd.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  if (file) fd.append('file', file)
  return api.post('/reports/generate', fd)
}
