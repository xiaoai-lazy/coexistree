import http from './http'

export function getDocumentContent(id) {
  return http.get(`/v1/documents/${id}/content`)
}
