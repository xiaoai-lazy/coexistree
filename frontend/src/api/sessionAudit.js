import http from './http'

/**
 * 获取所有会话的分页列表
 * @param {number} page
 * @param {number} size
 */
export function listSessions(page = 0, size = 20) {
  return http.get(`/v1/admin/sessions?page=${page}&size=${size}`)
}
