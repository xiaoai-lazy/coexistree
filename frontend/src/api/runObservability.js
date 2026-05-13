import http from './http'

/**
 * Run-based observability API.
 * Replaces sessionAudit.js for the new run perspective.
 */

/** List runs for a conversation */
export function listConversationRuns(conversationId, page = 0, size = 20) {
  return http.get(`/v1/admin/observability/conversations/${conversationId}/runs?page=${page}&size=${size}`)
}

/** Get run summary */
export function getRunSummary(runId) {
  return http.get(`/v1/admin/observability/runs/${runId}/summary`)
}

/** Get run span tree */
export function getRunTree(runId) {
  return http.get(`/v1/admin/observability/runs/${runId}/tree`)
}
