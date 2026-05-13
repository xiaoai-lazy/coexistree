import http from './http'

export const createConversation = (systemId, title) =>
  http.post('/v1/conversations', { systemId, title })

export const listConversations = (systemId, page, size) => {
  if (page !== undefined && size !== undefined) {
    // Use paginated endpoint
    return http.get('/v1/conversations/paginated', { 
      params: { systemId, page, size } 
    })
  }
  // Use legacy endpoint for backward compatibility
  return http.get('/v1/conversations', { params: { systemId } })
}

export const deleteConversation = (conversationId) =>
  http.delete(`/v1/conversations/${conversationId}`)

export const getMessages = (conversationId) =>
  http.get(`/v1/conversations/${conversationId}/messages`)

export const generateTitle = (conversationId) =>
  http.post(`/v1/conversations/${conversationId}/title`)
