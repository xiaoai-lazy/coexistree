<template>
  <el-drawer
    v-model="visible"
    size="50%"
    direction="rtl"
    :title="docName"
    destroy-on-close
    class="document-preview-drawer"
  >
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="20" animated />
    </div>

    <div v-else-if="error" class="error-container">
      <el-empty description="加载失败" />
      <el-button @click="loadContent">重试</el-button>
    </div>

    <div v-else class="preview-container">
      <!-- 文档内容 -->
      <div ref="contentRef" class="markdown-body" v-html="renderedContent" />

      <!-- 引用点导航（多个引用点时显示） -->
      <div v-if="docCitations.length > 1" class="citation-nav">
        <el-button
          :disabled="currentIndex === 0"
          @click="goToPrev"
          size="small"
        >
          <el-icon><ArrowLeft /></el-icon>
          上一个引用
        </el-button>

        <span class="nav-indicator">
          引用 {{ currentIndex + 1 }} / {{ docCitations.length }}
        </span>

        <el-button
          :disabled="currentIndex === docCitations.length - 1"
          @click="goToNext"
          size="small"
        >
          下一个引用
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { marked } from 'marked'
import { getDocumentContent } from '@/api/document'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: Boolean,
  docId: Number,
  initialNodeId: String,
  citations: Array  // 当前消息的所有引用
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const error = ref(false)
const docName = ref('')
const rawContent = ref('')
const contentRef = ref(null)
const currentIndex = ref(0)

// 当前文档的所有引用点（从citations中过滤）
const docCitations = computed(() => {
  if (!props.citations || !props.docId) return []

  const filtered = props.citations.filter(c =>
    c.docId === props.docId && c.nodeId != null
  )

  // 去重：相同nodeId只保留一个
  const seen = new Set()
  const unique = []

  for (const c of filtered) {
    if (!seen.has(c.nodeId)) {
      seen.add(c.nodeId)
      unique.push({
        nodeId: c.nodeId,
        title: c.path?.split(' > ').pop() || c.title || '',
        lineNum: c.lineNum,
        level: c.level || 1
      })
    }
  }

  // 按lineNum排序（如果有的話）
  return unique.sort((a, b) => {
    if (a.lineNum == null && b.lineNum == null) return 0
    if (a.lineNum == null) return 1
    if (b.lineNum == null) return -1
    return a.lineNum - b.lineNum
  })
})

const renderedContent = computed(() => {
  if (!rawContent.value) return ''

  // 创建自定义渲染器，为标题添加锚点标记
  const renderer = new marked.Renderer()
  const originalHeading = renderer.heading.bind(renderer)

  renderer.heading = (text, level, raw, slugger) => {
    // 尝试根据文本内容匹配nodeId
    const nodeId = findNodeIdByText(text)

    if (nodeId) {
      return `<h${level} data-node-id="${nodeId}" class="citation-anchor">${text}</h${level}>`
    }

    return originalHeading(text, level, raw, slugger)
  }

  return marked.parse(rawContent.value, { renderer })
})

// 根据标题文本查找对应的nodeId
function findNodeIdByText(text) {
  if (!docCitations.value.length) return null

  const cleanText = text.trim().toLowerCase()

  const match = docCitations.value.find(c =>
    c.title && c.title.trim().toLowerCase() === cleanText
  )

  return match?.nodeId || null
}

watch(() => props.modelValue, (val) => {
  if (val && props.docId) {
    loadContent()
  }
})

watch(() => props.docId, () => {
  if (visible.value && props.docId) {
    loadContent()
  }
})

async function loadContent() {
  loading.value = true
  error.value = false

  try {
    const res = await getDocumentContent(props.docId)
    const data = res.data

    docName.value = data.docName
    rawContent.value = data.content || ''

    // 找到初始nodeId对应的索引
    if (props.initialNodeId) {
      const index = docCitations.value.findIndex(c => c.nodeId === props.initialNodeId)
      currentIndex.value = index >= 0 ? index : 0
    } else {
      currentIndex.value = 0
    }

    // 等待渲染完成后滚动到当前引用
    nextTick(() => {
      scrollToCurrentCitation()
    })

  } catch (e) {
    error.value = true
    ElMessage.error('加载文档内容失败')
    console.error('Failed to load document content:', e)
  } finally {
    loading.value = false
  }
}

function scrollToCurrentCitation() {
  if (!contentRef.value || !docCitations.value[currentIndex.value]) return

  const citation = docCitations.value[currentIndex.value]
  if (!citation.nodeId) {
    console.warn('Citation missing nodeId:', citation)
    return
  }

  // 首先尝试通过data-node-id找到对应元素
  const targetElement = contentRef.value.querySelector(`[data-node-id="${citation.nodeId}"]`)

  if (targetElement) {
    targetElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
    highlightCitation(targetElement)
    return
  }

  // 兜底：如果有lineNum，使用lineNum估算滚动位置
  if (citation.lineNum != null && citation.lineNum >= 0) {
    const lineHeight = 24
    const offset = 100
    const targetY = citation.lineNum * lineHeight - offset

    contentRef.value.scrollTo({
      top: Math.max(0, targetY),
      behavior: 'smooth'
    })
    return
  }

  console.warn('Cannot scroll to citation:', citation)
}

function highlightCitation(targetElement) {
  // 移除之前的高亮
  contentRef.value?.querySelectorAll('.citation-highlight').forEach(el => {
    el.classList.remove('citation-highlight')
  })

  // 添加高亮类到当前元素
  if (targetElement) {
    targetElement.classList.add('citation-highlight')
  }
}

function goToPrev() {
  if (currentIndex.value > 0) {
    currentIndex.value--
    scrollToCurrentCitation()
  }
}

function goToNext() {
  if (currentIndex.value < docCitations.value.length - 1) {
    currentIndex.value++
    scrollToCurrentCitation()
  }
}
</script>

<style scoped>
.loading-container {
  padding: 20px;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 16px;
}

.preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.markdown-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  line-height: 24px;
}

.citation-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 12px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.nav-indicator {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

/* Citation highlight styles */
:deep(.citation-highlight) {
  background-color: rgba(59, 130, 246, 0.15);
  border-left: 4px solid var(--color-primary, #2563eb);
  padding-left: 12px;
  transition: all 0.3s ease;
}

:deep(.citation-anchor) {
  scroll-margin-top: 20px;
}
</style>

<style>
/* Markdown styles */
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
  line-height: 1.25;
}

.markdown-body p {
  margin-bottom: 16px;
}

.markdown-body pre {
  background: #f6f8fa;
  padding: 16px;
  overflow-x: auto;
  border-radius: 6px;
}

.markdown-body code {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 85%;
}
</style>
