import http from './http'

export function getDocumentContent(id) {
  return http.get(`/v1/documents/${id}/content`)
}

export function listDocuments(systemId) {
  return http.get('/v1/documents', { params: { systemId } })
}

export function uploadDocument(data) {
  return http.post('/v1/documents/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function deleteDocument(id) {
  return http.delete(`/v1/documents/${id}`)
}

export function updateDocumentSecurityLevel(id, securityLevel) {
  return http.put(`/v1/documents/${id}/security-level`, null, {
    params: { securityLevel }
  })
}
