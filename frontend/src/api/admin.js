import http from './http'

// ========== 用户管理 ==========

/**
 * 获取所有用户列表（管理员）
 */
export function listUsers() {
  return http.get('/v1/users')
}

/**
 * 创建新用户（管理员）
 * @param {Object} data - { username, password, displayName }
 */
export function createUser(data) {
  return http.post('/v1/users', data)
}

/**
 * 更新用户信息（管理员）
 * @param {number} id - 用户ID
 * @param {Object} data - { displayName, role, enabled }
 */
export function updateUser(id, data) {
  return http.put(`/v1/users/${id}`, data)
}

/**
 * 删除用户（管理员）
 * @param {number} id - 用户ID
 */
export function deleteUser(id) {
  return http.delete(`/v1/users/${id}`)
}

/**
 * 重置用户密码（管理员）
 * @param {number} id - 用户ID
 * @param {string} newPassword - 新密码
 */
export function resetPassword(id, newPassword) {
  return http.put(`/v1/users/${id}/password`, newPassword)
}

// ========== 系统管理 ==========

/**
 * 获取所有系统列表（管理员）
 */
export function listAllSystems() {
  return http.get('/v1/systems/admin/all')
}

/**
 * 转移系统所有权
 * @param {number} systemId - 系统ID
 * @param {number} newOwnerId - 新所有者用户ID
 */
export function transferSystemOwnership(systemId, newOwnerId) {
  return http.put(`/v1/systems/${systemId}/transfer`, { newOwnerId })
}

// ========== 成员概览 ==========

/**
 * 获取跨系统成员统计
 */
export function getMemberOverview() {
  return http.get('/v1/systems/admin/member-overview')
}
