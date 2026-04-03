import http from './http'

export function listMembers(systemId) {
  return http.get(`/v1/systems/${systemId}/members`)
}

export function addMember(systemId, data) {
  return http.post(`/v1/systems/${systemId}/members`, data)
}

export function removeMember(systemId, userId) {
  return http.delete(`/v1/systems/${systemId}/members/${userId}`)
}

export function updateViewLevel(systemId, userId, viewLevel) {
  return http.put(`/v1/systems/${systemId}/members/${userId}/view-level`, { viewLevel })
}
