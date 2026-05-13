
/**
 * 统一的对话框工具
 * 确保所有弹窗样式与系统 design system 一致
 */

/**
 * 确认对话框
 * @param {string} message - 提示消息
 * @param {string} title - 标题
 * @param {string} type - 类型: 'warning' | 'error' | 'success' | 'info'
 * @param {string} confirmButtonText - 确认按钮文本
 * @param {string} cancelButtonText - 取消按钮文本
 * @returns {Promise}
 */
export function confirm(message, title = '确认', type = 'warning', options = {}) {
  const {
    confirmButtonText = '确定',
    cancelButtonText = '取消',
    confirmButtonClass = '',
    ...rest
  } = options

  return ElMessageBox.confirm(message, title, {
    type,
    confirmButtonText,
    cancelButtonText,
    confirmButtonClass: type === 'error' ? 'el-button--danger' : confirmButtonClass,
    closeOnClickModal: false,
    closeOnPressEscape: true,
    showClose: true,
    ...rest
  })
}

/**
 * 删除确认对话框
 * @param {string} itemName - 要删除的项名称
 * @param {string} extraWarning - 额外警告信息
 * @returns {Promise}
 */
export function confirmDelete(itemName = '', extraWarning = '') {
  const message = itemName
    ? `确定要删除「${itemName}」吗？${extraWarning ? '\n' + extraWarning : ''}`
    : '确定要删除吗？'

  return confirm(message, '确认删除', 'warning', {
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger'
  })
}

export default {
  confirm,
  confirmDelete
}
