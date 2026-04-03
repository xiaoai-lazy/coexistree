/**
 * SSE 事件类型常量
 * 与 OpenAPI 契约中定义的 SseEvent.type 保持一致
 * @see api/openapi.yaml#/components/schemas/SseEvent
 */

export const SSE_EVENT_TYPES = {
  // 处理阶段
  STAGE: 'stage',

  // AI 思考过程
  THINKING: 'thinking',

  // 普通回答
  ANSWER: 'answer',

  // 引用来源
  CITATIONS: 'citations',

  // 意图识别结果
  INTENT_DETECTED: 'intent_detected',

  // 需要澄清
  CLARIFICATION_NEEDED: 'clarification_needed',

  // 评估阶段
  EVALUATION_STAGE: 'evaluation_stage',

  // 评估结果
  EVALUATION_RESULT: 'evaluation_result',

  // 评估完成
  EVALUATION_DONE: 'evaluation_done',

  // 流结束
  DONE: 'done',

  // 错误
  ERROR: 'error'
}

/**
 * 处理阶段标签映射
 */
export const STAGE_LABELS = {
  init: '正在初始化...',
  load_document: '正在加载文档索引...',
  load_system_tree: '正在加载系统知识树...',
  search: '正在检索相关内容...',
  thinking: '正在生成回答...',
  intent_detection: '正在分析意图...',
  CONFLICT: '正在检测功能冲突...',
  CONSISTENCY: '正在检查业务规则...',
  IMPACT: '正在分析模块依赖...',
  HISTORY: '正在验证历史背景...'
}

/**
 * 评估类别映射
 */
export const EVALUATION_CATEGORIES = {
  CONFLICT: '功能冲突检测',
  CONSISTENCY: '业务规则一致性',
  IMPACT: '影响范围分析',
  HISTORY: '历史背景验证'
}

/**
 * 风险等级
 */
export const RISK_LEVELS = {
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
  NONE: 'none'
}
