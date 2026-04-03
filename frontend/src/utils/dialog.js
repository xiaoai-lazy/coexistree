import { ElMessageBox, ElDialog } from 'element-plus'

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

/**
 * 危险操作确认（如永久删除、禁用等）
 * @param {string} message - 提示消息
 * @param {string} title - 标题
 * @returns {Promise}
 */
export function confirmDanger(message, title = '危险操作') {
  return confirm(message, title, 'error', {
    confirmButtonText: '确认',
    confirmButtonClass: 'el-button--danger'
  })
}

/**
 * 信息提示对话框
 * @param {string} message - 提示消息
 * @param {string} title - 标题
 * @returns {Promise}
 */
export function alert(message, title = '提示', type = 'info') {
  return ElMessageBox.alert(message, title, {
    type,
    confirmButtonText: '知道了',
    closeOnClickModal: false,
    closeOnPressEscape: true,
    showClose: true
  })
}

/**
 * Dialog 默认配置
 * 用于统一 el-dialog 的默认行为
 */
export const dialogDefaultProps = {
  closeOnClickModal: false,
  closeOnPressEscape: true,
  destroyOnClose: true,
  alignCenter: false,
  center: false,
  appendToBody: false,
  lockScroll: true,
  modal: true,
  showClose: true,
  draggable: false
}

/**
 * Dialog 尺寸配置
 */
export const dialogSizes = {
  xs: '360px',
  sm: '480px',
  md: '600px',
  lg: '800px',
  xl: '960px'
}

export default {
  confirm,
  confirmDelete,
  confirmDanger,
  alert,
  dialogDefaultProps,
  dialogSizes
}
