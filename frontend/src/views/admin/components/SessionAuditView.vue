<template>
  <div class="session-audit">
    <!-- 左侧面板 -->
    <aside class="side-panel">
      <div class="panel-header">
        <h3>会话与 Run</h3>
      </div>
      <div class="list-area" v-loading="loading">
        <template v-for="conv in sessions" :key="conv.conversationId">
          <!-- 一级：会话 -->
          <div
            class="session-group"
            :class="{ active: selectedConversation?.conversationId === conv.conversationId }"
          >
            <div class="session-item" @click="toggleSession(conv)">
              <span class="expand-icon" :class="{ open: expandedConvs.has(conv.conversationId) }">▶</span>
              <span class="session-title">{{ conv.title || '未命名会话' }}</span>
              <span class="session-date">{{ formatDate(conv.createdAt) }}</span>
            </div>

            <!-- 二级：Run 列表 -->
            <div v-if="expandedConvs.has(conv.conversationId)" class="run-sublist" v-loading="convRuns[conv.conversationId]?.loading">
              <div
                v-for="run in convRuns[conv.conversationId]?.runs || []"
                :key="run.runId"
                class="run-item"
                :class="{ active: selectedRun?.runId === run.runId }"
                @click="selectRun(run)"
              >
                <span class="run-title">{{ truncate(run.requestPreview, 40) || '—' }}</span>
                <span class="run-meta">
                  <span class="status-dot" :class="run.status">{{ run.status === 'success' ? '✓' : '✗' }}</span>
                  <span class="run-duration">{{ formatDuration(run.durationMs) }}</span>
                </span>
              </div>
              <el-empty
                v-if="!convRuns[conv.conversationId]?.loading && (!convRuns[conv.conversationId]?.runs || convRuns[conv.conversationId]?.runs.length === 0)"
                description="暂无 Run"
                :image-size="40"
              />
            </div>
          </div>
        </template>
        <el-empty v-if="!loading && sessions.length === 0" description="暂无会话" />
      </div>
      <div class="pagination-bar" v-if="totalPages > 1">
        <el-pagination
          small
          layout="prev, pager, next"
          :current-page="currentPage + 1"
          :page-size="pageSize"
          :total="totalElements"
          @current-change="handlePageChange"
        />
      </div>
    </aside>

    <!-- 右侧：瀑布图详情 -->
    <main class="detail-panel" v-if="selectedRun">
      <div class="detail-header">
        <div class="run-summary">
          <span class="run-id">{{ selectedRun.runId }}</span>
          <span class="run-status" :class="selectedRun.status">{{ summary?.status || selectedRun.status }}</span>
          <span v-if="summary" class="run-stats">
            {{ summary.totalSpanCount }} spans · {{ summary.modelCallCount }} model · {{ summary.toolCallCount }} tool
          </span>
        </div>
      </div>
      <div class="tab-content" v-loading="loadingTree">
        <SpanWaterfall :tree="runTree" :loading="loadingTree" />
      </div>
    </main>

    <main class="detail-panel" v-else>
      <el-empty description="请选择一个 Run 查看详情" :image-size="120" />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listSessions } from '@/api/sessionAudit'
import { listConversationRuns, getRunSummary, getRunTree } from '@/api/runObservability'
import SpanWaterfall from './SpanWaterfall.vue'
import { ElMessage } from 'element-plus'

// State
const sessions = ref([])
const selectedConversation = ref(null)
const selectedRun = ref(null)
const summary = ref(null)
const runTree = ref(null)
const loading = ref(false)
const loadingTree = ref(false)
const currentPage = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)

const expandedConvs = ref(new Set())
const convRuns = ref({})

// Session list
async function loadSessions(page = 0) {
  loading.value = true
  try {
    const res = await listSessions(page, pageSize.value)
    if (res.success) {
      sessions.value = res.data.content || []
      totalElements.value = res.data.totalElements || 0
      totalPages.value = res.data.totalPages || 0
      currentPage.value = page
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '加载会话失败')
  } finally {
    loading.value = false
  }
}

async function toggleSession(conv) {
  if (expandedConvs.value.has(conv.conversationId)) {
    expandedConvs.value.delete(conv.conversationId)
    return
  }

  expandedConvs.value.add(conv.conversationId)
  selectedConversation.value = conv

  // Load runs if not cached
  if (!convRuns.value[conv.conversationId]) {
    convRuns.value[conv.conversationId] = { runs: [], loading: true }
    try {
      const res = await listConversationRuns(conv.conversationId, 0, 50)
      if (res.success) {
        convRuns.value[conv.conversationId].runs = res.data || []
      }
    } catch (err) {
      ElMessage.error(err.response?.data?.message || '加载 Run 列表失败')
    } finally {
      convRuns.value[conv.conversationId].loading = false
    }
  }
}

// Run detail
async function selectRun(run) {
  selectedRun.value = run
  loadingTree.value = true

  try {
    const [summaryRes, treeRes] = await Promise.allSettled([
      getRunSummary(run.runId),
      getRunTree(run.runId)
    ])

    if (summaryRes.status === 'fulfilled' && summaryRes.value.success) {
      summary.value = summaryRes.value.data
    }

    if (treeRes.status === 'fulfilled' && treeRes.value.success) {
      runTree.value = treeRes.value.data
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '加载 Run 详情失败')
  } finally {
    loadingTree.value = false
  }
}

// Helpers
function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

function formatDuration(ms) {
  if (!ms && ms !== 0) return '0ms'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function truncate(s, maxLen) {
  if (!s) return ''
  return s.length > maxLen ? s.substring(0, maxLen) + '...' : s
}

function handlePageChange(page) {
  loadSessions(page - 1)
}

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.session-audit {
  display: flex;
  height: calc(100vh - 120px);
  gap: 0;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
}

/* Side panel */
.side-panel {
  width: 280px;
  min-width: 280px;
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border-light);
}

.panel-header h3 {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.list-area {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

/* Session group */
.session-group.active {
  background: var(--color-sidebar-active);
  border-radius: var(--radius-md);
}

/* Session item (level 1) */
.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: background 0.15s;
}

.session-item:hover {
  background: var(--color-bg-hover);
}

.expand-icon {
  font-size: 8px;
  color: #475569;
  transition: transform 0.2s;
  flex-shrink: 0;
}

.expand-icon.open {
  transform: rotate(90deg);
}

.session-title {
  flex: 1;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-date {
  font-size: 10px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

/* Run sublist (level 2) */
.run-sublist {
  padding: 2px 0 2px 16px;
}

.run-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.run-item:hover {
  background: var(--color-bg-hover);
}

.run-item.active {
  background: rgba(96, 165, 250, 0.12);
  border-left: 3px solid var(--color-primary);
}

.run-title {
  flex: 1;
  font-size: 11px;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.run-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  font-size: 10px;
  color: var(--color-text-secondary);
}

.status-dot {
  padding: 0 3px;
  border-radius: 3px;
  font-size: 9px;
  font-weight: 600;
}

.status-dot.success { color: #34d399; background: rgba(52, 211, 153, 0.15); }
.status-dot.failed { color: #f87171; background: rgba(248, 113, 113, 0.15); }

.run-duration {
  font-family: monospace;
  font-size: 9px;
}

.pagination-bar {
  padding: 6px 10px;
  border-top: 1px solid var(--color-border-light);
  display: flex;
  justify-content: center;
}

/* Detail panel */
.detail-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-header {
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.run-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.run-id {
  font-family: monospace;
  color: #64748b;
  font-size: 10px;
}

.run-status {
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
}

.run-status.success { color: #34d399; background: rgba(52, 211, 153, 0.15); }
.run-status.failed { color: #f87171; background: rgba(248, 113, 113, 0.15); }

.run-stats {
  color: #94a3b8;
}

.tab-content {
  flex: 1;
  overflow-y: auto;
}
</style>
