import http from './http'

export function getSystem(id) {
  return http.get(`/v1/systems/${id}`)
}

export function listSystems() {
  return http.get('/v1/systems')
}

export function createSystem(data) {
  return http.post('/v1/systems', data)
}

export function updateSystem(id, data) {
  return http.put(`/v1/systems/${id}`, data)
}

export function deleteSystem(id) {
  return http.delete(`/v1/systems/${id}`)
}
