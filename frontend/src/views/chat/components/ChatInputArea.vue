<template>
  <div class="chat-input-area">
    <!-- Attached Document Indicator -->
    <transition name="slide-down">
      <div v-if="attachedDocument" class="attached-document">
        <div class="document-info">
          <el-icon class="document-icon" :size="16"><Document /></el-icon>
          <span class="document-name">{{ attachedDocument.name }}</span>
          <span class="document-badge">待评估</span>
        </div>
        <el-button
          link
          type="danger"
          size="small"
          class="remove-btn"
          @click="removeDocument"
        >
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </transition>

    <!-- Input Area -->
    <div class="input-container" :class="{ 'has-document': attachedDocument }">
      <el-tooltip content="上传需求文档" placement="top">
        <el-button
          class="upload-btn"
          circle
          :type="attachedDocument ? 'success' : 'default'"
          @click="openUpload"
        >
          <el-icon :size="18"><Paperclip /></el-icon>
        </el-button>
      </el-tooltip>

      <div class="input-wrapper">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="请输入问题，或上传文档进行需求评估..."
          class="chat-input"
          @keydown.enter.prevent="handleEnter"
        />
      </div>

      <el-button
        type="primary"
        class="send-btn"
        :disabled="!canSend"
        @click="send"
      >
        <el-icon><Promotion /></el-icon>
      </el-button>
    </div>

    <!-- Upload Dialog -->
    <el-dialog
      v-model="uploadVisible"
      title="上传需求文档"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-upload
        drag
        action="/api/v1/documents/upload"
        :headers="uploadHeaders"
        :data="uploadData"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        accept=".md,.markdown,.txt"
        :timeout="120000"
        class="upload-area"
      >
        <el-icon class="upload-icon"><Upload /></el-icon>
        <div class="upload-text">
          <p>拖拽文件到此处，或 <em>点击上传</em></p>
          <p class="upload-hint">支持 Markdown、TXT 格式</p>
        </div>
      </el-upload>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Document, Close, Paperclip, Promotion, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  attachedDocument: {
    type: Object,
    default: null
  },
  systemId: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'send', 'update:attachedDocument'])

const inputMessage = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const uploadVisible = ref(false)
const uploadHeaders = ref({
  Authorization: `Bearer ${localStorage.getItem('token')}`
})
const uploadData = computed(() => ({
  systemId: props.systemId
}))

const canSend = computed(() => {
  return inputMessage.value.trim().length > 0
})

const openUpload = () => {
  uploadVisible.value = true
}

const handleUploadSuccess = (response) => {
  if (response.success) {
    emit('update:attachedDocument', {
      id: response.data.id,
      name: response.data.originalFileName
    })
    uploadVisible.value = false
    ElMessage.success('文档上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleUploadError = () => {
  ElMessage.error('上传失败，请重试')
}

const removeDocument = () => {
  emit('update:attachedDocument', null)
}

const handleEnter = (e) => {
  if (!e.shiftKey) {
    send()
  }
}

const send = () => {
  if (!canSend.value) return
  emit('send', {
    message: inputMessage.value.trim(),
    documentId: props.attachedDocument?.id
  })
  inputMessage.value = ''
}
</script>

<style scoped>
.chat-input-area {
  padding: 16px 20px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.8) 0%, rgba(255, 255, 255, 1) 100%);
  border-top: 1px solid rgba(226, 232, 240, 0.6);
}

/* Attached Document Indicator */
.attached-document {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
  border: 1px solid #6ee7b7;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.1);
}

.document-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.document-icon {
  color: #059669;
}

.document-name {
  font-size: 14px;
  font-weight: 500;
  color: #065f46;
}

.document-badge {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  color: #059669;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 12px;
}

.remove-btn {
  padding: 4px;
}

/* Input Container */
.input-container {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.input-container:hover {
  border-color: #94a3b8;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

.input-container.has-document {
  border-color: #10b981;
  box-shadow: 0 4px 20px rgba(16, 185, 129, 0.15);
}

/* Upload Button */
.upload-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  transition: all 0.3s ease;
}

.upload-btn:hover {
  transform: scale(1.05);
  background: #f1f5f9;
}

/* Input Wrapper */
.input-wrapper {
  flex: 1;
  min-width: 0;
}

.chat-input :deep(.el-textarea__inner) {
  padding: 10px 0;
  font-size: 15px;
  line-height: 1.6;
  color: #1e293b;
  background: transparent;
  border: none;
  box-shadow: none;
  resize: none;
}

.chat-input :deep(.el-textarea__inner::placeholder) {
  color: #94a3b8;
}

.chat-input :deep(.el-textarea__inner:focus) {
  outline: none;
}

/* Send Button */
.send-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  padding: 0;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border: none;
  border-radius: 12px;
  transition: all 0.3s ease;
}

.send-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}

.send-btn:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}

/* Upload Dialog */
.upload-area :deep(.el-upload-dragger) {
  padding: 60px 20px;
  background: #f8fafc;
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  transition: all 0.3s ease;
}

.upload-area :deep(.el-upload-dragger:hover) {
  background: #f0f9ff;
  border-color: #3b82f6;
}

.upload-icon {
  margin-bottom: 16px;
  font-size: 48px;
  color: #94a3b8;
}

.upload-text {
  color: #64748b;
}

.upload-text em {
  color: #3b82f6;
  font-style: normal;
}

.upload-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}

/* Transitions */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
