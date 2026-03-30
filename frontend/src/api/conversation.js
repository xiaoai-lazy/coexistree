import http from './http'

export const createConversation = (systemId, title) =>
  http.post('/v1/conversations', { systemId, title })

export const listConversations = (systemId) =>
  http.get('/v1/conversations', { params: { systemId } })

export const deleteConversation = (conversationId) =>
  http.delete(`/v1/conversations/${conversationId}`)

export const getMessages = (conversationId) =>
  http.get(`/v1/conversations/${conversationId}/messages`)

export const generateTitle = (conversationId) =>
  http.post(`/v1/conversations/${conversationId}/title`)
