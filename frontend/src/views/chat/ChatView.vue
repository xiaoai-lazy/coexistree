<template>
  <div class="chat-page">
    <!-- History Sidebar -->
    <transition name="slide">
      <aside v-if="historyOpen" class="history-sidebar">
        <div class="sidebar-header">
          <el-select
            v-model="selectedSystemId"
            placeholder="选择系统"
            size="default"
            @change="onSystemChange"
          >
            <el-option
              v-for="sys in systems"
              :key="sys.id"
              :label="sys.systemName"
              :value="sys.id"
            />
          </el-select>
        </div>

        <div class="sidebar-actions">
          <el-button
            type="primary"
            :disabled="!selectedSystemId"
            @click="newConversation"
            class="new-chat-btn"
          >
            <el-icon><Plus /></el-icon>
            <span>新建对话</span>
          </el-button>
        </div>

        <div class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.conversationId"
            class="conversation-item"
            :class="{ active: currentConversationId === conv.conversationId }"
            @click="switchConversation(conv)"
          >
            <el-icon :size="14" class="conv-icon"><ChatDotRound /></el-icon>
            <span class="conv-title">{{ conv.title || '新对话' }}</span>
            <el-button
              link
              type="danger"
              size="small"
              class="delete-btn"
              @click.stop="deleteConv(conv.conversationId)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>

          <el-empty
            v-if="conversations.length === 0"
            description="暂无会话"
            :image-size="48"
          />
        </div>
      </aside>
    </transition>

    <!-- Toggle Button -->
    <button class="sidebar-toggle" :class="{ open: historyOpen }" @click="historyOpen = !historyOpen">
      <el-icon><ArrowLeft v-if="historyOpen" /><ArrowRight v-else /></el-icon>
    </button>

    <!-- Main Chat Area -->
    <main class="chat-main">
      <!-- Empty State -->
      <div v-if="messages.length === 0" class="welcome-section">
        <div class="welcome-content">
          <div class="welcome-icon">
            <el-icon :size="32"><Promotion /></el-icon>
          </div>
          <h1 class="welcome-title">智能问答</h1>
          <p class="welcome-desc">基于系统知识库的智能对话，支持问答和需求评估</p>
          <div class="welcome-features">
            <div class="feature-item">
              <el-icon><ChatDotRound /></el-icon>
              <span>系统问答 - 了解现有功能和实现</span>
            </div>
            <div class="feature-item">
              <el-icon><DocumentChecked /></el-icon>
              <span>需求评估 - 上传需求文档，评估冲突和影响</span>
            </div>
          </div>
          <p class="welcome-hint" v-if="!currentConversationId">请先在左侧选择系统并新建会话</p>
        </div>
      </div>

      <!-- Chat Messages -->
      <div v-else ref="contentRef" class="messages-container">
        <template v-for="(msg, idx) in messages" :key="msg.id">
          <!-- User Message -->
          <div v-if="msg.role === 'user'" class="message user-message">
            <div class="message-content">
              <div v-if="msg.attachedDocument" class="user-document-tag">
                <el-icon :size="12"><Document /></el-icon>
                <span>{{ msg.attachedDocument.name }}</span>
              </div>
              <div class="message-bubble">{{ msg.content }}</div>
            </div>
          </div>

          <!-- AI Message -->
          <div v-else class="message ai-message">
            <div class="message-avatar">
              <el-icon :size="18"><Cpu /></el-icon>
            </div>
            <div class="message-content">
              <!-- Status -->
              <div v-if="msg.statusText" class="status-badge">
                <span class="status-dot"></span>
                {{ msg.statusText }}
              </div>

              <!-- Thinking -->
              <div v-if="msg.thinking" class="thinking-box">
                <div class="thinking-header" @click="msg.thinkingOpen = !msg.thinkingOpen">
                  <el-icon><Cpu /></el-icon>
                  <span>思考过程</span>
                  <el-icon class="toggle-icon"><ArrowUp v-if="msg.thinkingOpen" /><ArrowDown v-else /></el-icon>
                </div>
                <div v-if="msg.thinkingOpen" class="thinking-body">{{ msg.thinking }}</div>
              </div>

              <!-- Clarification Options -->
              <ClarificationOptions
                v-if="msg.type === 'CLARIFICATION' && msg.options"
                :question="msg.content"
                :options="msg.options"
                @select="handleClarificationSelect"
              />

              <!-- Evaluation Report -->
              <EvaluationReportCard
                v-else-if="msg.type === 'EVALUATION_REPORT'"
                :category="msg.evaluationCategory"
                :risk-level="msg.riskLevel"
                :subtitle="msg.subtitle"
                :summary="msg.summary"
                :details="msg.details"
              />

              <!-- Normal Answer -->
              <div v-else-if="msg.content" class="message-bubble" v-html="renderMarkdown(msg.content)"></div>

              <!-- Citations -->
              <div v-if="msg.citations?.length" class="citations-box">
                <div class="citations-header" @click="msg.citationsOpen = !msg.citationsOpen">
                  <el-icon><Document /></el-icon>
                  <span>引用来源 ({{ msg.citations.length }})</span>
                  <el-icon class="toggle-icon"><ArrowUp v-if="msg.citationsOpen" /><ArrowDown v-else /></el-icon>
                </div>
                <div v-if="msg.citationsOpen" class="citations-body">
                  <div
                    v-for="(cite, ci) in msg.citations"
                    :key="ci"
                    class="citation-item"
                  >
                    <div class="citation-path">{{ cite.path }}</div>
                    <div class="citation-text">{{ cite.text }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- Input Area -->
      <div class="input-section">
        <ChatInputArea
          v-model="inputValue"
          :attached-document="attachedDocument"
          :system-id="selectedSystemId"
          :disabled="!currentConversationId || sending"
          @send="onSubmit"
          @update:attached-document="attachedDocument = $event"
        />
        <p class="input-hint">AI 生成内容仅供参考，请核实重要信息</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { listSystems } from '@/api/system'
import { createConversation, listConversations, deleteConversation, getMessages, generateTitle } from '@/api/conversation'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/dialog'
import { marked } from 'marked'
import ChatInputArea from './components/ChatInputArea.vue'
import EvaluationReportCard from './components/EvaluationReportCard.vue'
import ClarificationOptions from './components/ClarificationOptions.vue'
import { SSE_EVENT_TYPES, STAGE_LABELS } from '@/constants/sse-events'

const historyOpen = ref(true)
const systems = ref([])
const selectedSystemId = ref(null)
const conversations = ref([])
const currentConversationId = ref(null)
const messages = ref([])
const inputValue = ref('')
const sending = ref(false)
const contentRef = ref(null)
const attachedDocument = ref(null)
let abortController = null

// Configure marked for safe rendering
marked.setOptions({
  breaks: true,
  gfm: true,
})

const renderMarkdown = (content) => {
  if (!content) return ''
  return marked.parse(content)
}

onMounted(async () => {
  try {
    const res = await listSystems()
    systems.value = res.data || []
    if (systems.value.length > 0) {
      selectedSystemId.value = systems.value[0].id
      await loadConversations()
    }
  } catch {
    ElMessage.error('加载系统列表失败')
  }
})

const loadConversations = async () => {
  if (!selectedSystemId.value) return
  try {
    const res = await listConversations(selectedSystemId.value)
    conversations.value = res.data || []
  } catch {
    ElMessage.error('加载会话列表失败')
  }
}

const leaveConversation = async () => {
  const currentConv = conversations.value.find(c => c.conversationId === currentConversationId.value)
  if (currentConv && !currentConv.title && messages.value.length > 0) {
    try {
      const res = await generateTitle(currentConv.conversationId)
      currentConv.title = res.data
    } catch {
      const firstUserMsg = messages.value.find(m => m.role === 'user')
      if (firstUserMsg) {
        currentConv.title = firstUserMsg.content.replace(/\s+/g, ' ').trim().slice(0, 10)
      } else {
        currentConv.title = '新对话'
      }
    }
  }
}

const onSystemChange = () => {
  currentConversationId.value = null
  messages.value = []
  loadConversations()
}

const newConversation = async () => {
  await leaveConversation()
  try {
    const res = await createConversation(selectedSystemId.value, null)
    const conv = res.data
    conversations.value.unshift(conv)
    currentConversationId.value = conv.conversationId
    messages.value = []
  } catch {
    ElMessage.error('创建会话失败')
  }
}

const switchConversation = async (conv) => {
  if (currentConversationId.value === conv.conversationId) return
  await leaveConversation()
  currentConversationId.value = conv.conversationId
  attachedDocument.value = null
  abortController?.abort()
  sending.value = false
  try {
    const res = await getMessages(conv.conversationId)
    messages.value = (res.data || []).map(m => ({
      id: m.id,
      role: m.role.toLowerCase(),
      content: m.content,
      type: m.messageType,
      evaluationCategory: m.evaluationCategory,
      riskLevel: m.riskLevel,
      subtitle: m.subtitle,
      summary: m.summary,
      details: m.details ? JSON.parse(m.details) : null,
      options: m.options,
      attachedDocument: m.attachedDocument,
      loading: false,
      typing: false,
      thinking: m.thinking || '',
      thinkingOpen: false,
      citations: m.citations || [],
      citationsOpen: false,
      statusText: '',
    }))
    scrollToBottom()
  } catch {
    ElMessage.error('加载消息记录失败')
  }
}

const deleteConv = async (conversationId) => {
  try {
    await confirmDelete('该会话及其所有消息')
    await deleteConversation(conversationId)
    conversations.value = conversations.value.filter(c => c.conversationId !== conversationId)
    if (currentConversationId.value === conversationId) {
      currentConversationId.value = null
      messages.value = []
      attachedDocument.value = null
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除会话失败')
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (contentRef.value) contentRef.value.scrollTop = contentRef.value.scrollHeight
  })
}

const onSubmit = async ({ message, documentId }) => {
  const text = message.trim()
  if (!text || sending.value || !currentConversationId.value) return

  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: text,
    attachedDocument: attachedDocument.value
  })
  inputValue.value = ''
  sending.value = true
  scrollToBottom()

  const aiMsgId = Date.now() + 1
  messages.value.push({
    id: aiMsgId,
    role: 'assistant',
    content: '',
    type: null,
    loading: true,
    thinking: '',
    thinkingOpen: true,
    citations: [],
    citationsOpen: false,
    statusText: '',
    grounded: null,
  })

  const getAiMsg = () => messages.value.find(m => m.id === aiMsgId)
  abortController = new AbortController()

  try {
    const token = localStorage.getItem('token')
    await fetchEventSource(`/api/v1/conversations/${currentConversationId.value}/smart-chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token && { 'Authorization': `Bearer ${token}` })
      },
      body: JSON.stringify({
        question: text,
        documentId: documentId
      }),
      signal: abortController.signal,
      onopen: async () => {
        const msg = getAiMsg()
        if (msg) msg.loading = false
      },
      onmessage: (event) => {
        try {
          const data = JSON.parse(event.data)
          const msg = getAiMsg()
          if (!msg) return

          // 处理不同类型的 SSE 事件 - 使用契约定义的常量
          switch (data.type) {
            // 标准阶段事件
            case SSE_EVENT_TYPES.STAGE:
              if (STAGE_LABELS[data.content]) {
                msg.statusText = STAGE_LABELS[data.content]
              }
              break

            // 思考过程
            case SSE_EVENT_TYPES.THINKING:
              msg.thinking += data.content
              break

            // 普通回答
            case SSE_EVENT_TYPES.ANSWER:
              msg.statusText = ''
              msg.thinkingOpen = false
              msg.content += data.content
              break

            // 引用来源
            case SSE_EVENT_TYPES.CITATIONS:
              msg.citations = data.citations || []
              break

            // 意图检测
            case SSE_EVENT_TYPES.INTENT_DETECTED:
              msg.statusText = `意图识别: ${data.data?.intent === 'REQUIREMENT_EVAL' ? '需求评估' : '问答'} (${data.data?.confidence})`
              break

            // 需要澄清
            case SSE_EVENT_TYPES.CLARIFICATION_NEEDED:
              msg.type = 'CLARIFICATION'
              msg.content = '请选择您想要进行的操作：'
              msg.options = (data.data || []).map(opt => ({
                label: opt.label,
                description: opt.description,
                value: opt.value
              }))
              msg.statusText = ''
              break

            // 评估阶段
            case SSE_EVENT_TYPES.EVALUATION_STAGE:
              if (STAGE_LABELS[data.data]) {
                msg.statusText = STAGE_LABELS[data.data]
              } else {
                msg.statusText = '正在评估...'
              }
              if (!msg.type) {
                msg.type = 'EVALUATION_REPORT'
              }
              break

            // 评估结果
            case SSE_EVENT_TYPES.EVALUATION_RESULT:
              msg.type = 'EVALUATION_REPORT'
              const report = data.data
              if (report) {
                msg.evaluationCategory = report.category
                msg.riskLevel = report.riskLevel
                msg.subtitle = report.subtitle
                msg.summary = report.summary
                msg.details = report.details
              }
              msg.statusText = ''
              // 创建新的消息卡片显示下一个评估结果
              if (msg.evaluationCategory) {
                // 如果已经有类别，创建新消息
                messages.value.push({
                  id: Date.now() + Math.random(),
                  role: 'assistant',
                  type: 'EVALUATION_REPORT',
                  evaluationCategory: report?.category,
                  riskLevel: report?.riskLevel,
                  subtitle: report?.subtitle,
                  summary: report?.summary,
                  details: report?.details,
                  content: '',
                  loading: false,
                  thinking: '',
                  thinkingOpen: false,
                  citations: [],
                  citationsOpen: false,
                  statusText: '',
                })
              }
              break

            // 评估完成
            case SSE_EVENT_TYPES.EVALUATION_DONE:
              msg.statusText = ''
              msg.loading = false
              break

            // 完成
            case SSE_EVENT_TYPES.DONE:
              msg.grounded = data.status === 'grounded'
              msg.statusText = ''
              msg.loading = false
              break

            // 错误
            case SSE_EVENT_TYPES.ERROR:
              msg.statusText = ''
              msg.loading = false
              msg.content = data.content || '请求失败，请稍后重试。'
              break
          }
          scrollToBottom()
        } catch (e) {
          console.error('处理 SSE 消息失败:', e)
        }
      },
      onerror: (err) => {
        const msg = getAiMsg()
        if (msg) {
          msg.loading = false
          msg.content = '请求失败，请稍后重试。'
        }
        throw err
      },
    })
  } finally {
    const msg = getAiMsg()
    if (msg) { msg.loading = false }
    sending.value = false
    abortController = null
  }
}

const handleClarificationSelect = (option) => {
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: `我选择：${option.label}`,
  })

  onSubmit({
    message: option.value,
    documentId: attachedDocument.value?.id
  })
}
</script>

<style scoped>
.chat-page {
  display: flex;
  height: 100%;
  width: 100%;
  overflow: hidden;
  background: var(--color-bg-page);
  position: relative;
}

/* Sidebar */
.history-sidebar {
  width: 260px;
  min-width: 260px;
  height: 100%;
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.sidebar-actions {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.new-chat-btn {
  width: 100%;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 2px;
}

.conversation-item:hover {
  background: var(--color-bg-hover);
}

.conversation-item.active {
  background: var(--color-sidebar-active);
}

.conv-icon {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.conversation-item.active .conv-icon {
  color: var(--color-primary);
}

.conv-title {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-item.active .conv-title {
  color: var(--color-primary);
  font-weight: 500;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

/* Toggle Button */
.sidebar-toggle {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-left: none;
  border-radius: 0 6px 6px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 20;
  color: var(--color-text-tertiary);
  transition: all 0.2s;
}

.sidebar-toggle.open {
  left: 260px;
}

.sidebar-toggle:hover {
  color: var(--color-primary);
  background: var(--color-sidebar-active);
}

/* Main Chat Area */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

/* Welcome Section */
.welcome-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.welcome-content {
  text-align: center;
  max-width: 480px;
}

.welcome-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin: 0 auto 24px;
}

.welcome-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 12px 0;
}

.welcome-desc {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin: 0 0 24px 0;
  line-height: 1.6;
}

.welcome-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
  padding: 16px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.feature-item .el-icon {
  color: var(--color-primary);
}

.welcome-hint {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0;
}

/* Messages Container */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px 16%;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Message Styles */
.message {
  display: flex;
  gap: 12px;
}

.user-message {
  justify-content: flex-end;
}

.message-avatar {
  width: 32px;
  height: 32px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  flex-shrink: 0;
}

.message-content {
  max-width: 80%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.user-document-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-end;
  padding: 4px 10px;
  font-size: 12px;
  color: #059669;
  background: #d1fae5;
  border-radius: 12px;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  font-size: 14px;
  line-height: 1.6;
}

.user-message .message-bubble {
  background: var(--color-primary);
  color: white;
  border-bottom-right-radius: 4px;
}

.ai-message .message-bubble {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-bottom-left-radius: 4px;
  color: var(--color-text-secondary);
}

/* Status Badge */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.status-dot {
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Thinking Box */
.thinking-box {
  background: var(--color-bg-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-tertiary);
  user-select: none;
}

.thinking-header:hover {
  background: var(--color-bg-hover);
}

.toggle-icon {
  margin-left: auto;
  font-size: 12px;
}

.thinking-body {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--color-text-muted);
  white-space: pre-wrap;
  line-height: 1.6;
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-card);
}

/* Citations Box */
.citations-box {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-top: 4px;
}

.citations-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-tertiary);
  background: var(--color-bg-subtle);
  user-select: none;
}

.citations-header:hover {
  background: var(--color-bg-hover);
}

.citations-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.citation-item {
  padding-left: 12px;
  border-left: 3px solid var(--color-primary-light);
}

.citation-path {
  font-size: 11px;
  color: var(--color-primary);
  font-weight: 500;
  margin-bottom: 4px;
}

.citation-text {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Input Section */
.input-section {
  padding: 16px 16% 24px;
  background: var(--color-bg-page);
}

.input-hint {
  text-align: center;
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin: 8px 0 0 0;
}

/* Transitions */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.25s ease;
}

.slide-enter-from,
.slide-leave-to {
  width: 0;
  min-width: 0;
  opacity: 0;
}

/* Markdown Styles */
:deep(.message-bubble pre) {
  background: var(--color-bg-subtle);
  padding: 12px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: 8px 0;
}

:deep(.message-bubble code) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

:deep(.message-bubble p) {
  margin: 0 0 8px 0;
}

:deep(.message-bubble p:last-child) {
  margin-bottom: 0;
}

:deep(.message-bubble ul, .message-bubble ol) {
  margin: 8px 0;
  padding-left: 20px;
}

:deep(.message-bubble li) {
  margin: 4px 0;
}

:deep(.message-bubble h1, .message-bubble h2, .message-bubble h3, .message-bubble h4) {
  margin: 16px 0 8px 0;
  color: var(--color-text-primary);
}

:deep(.message-bubble a) {
  color: var(--color-primary);
  text-decoration: none;
}

:deep(.message-bubble a:hover) {
  text-decoration: underline;
}

:deep(.message-bubble blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid var(--color-border);
  background: var(--color-bg-subtle);
  color: var(--color-text-muted);
}
</style>
