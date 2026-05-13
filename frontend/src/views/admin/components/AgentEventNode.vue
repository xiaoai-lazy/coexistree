<template>
  <div class="event-node">
    <div
      class="event-node-header"
      :class="node.eventType"
      @click="toggleExpand"
    >
      <span class="expand-icon">{{ isExpanded ? '▼' : '▶' }}</span>
      <span class="event-icon">{{ getIcon(node) }}</span>
      <span class="event-type-label">{{ getTypeLabel(node) }}</span>
      <span v-if="node.agentName" class="agent-badge">{{ node.agentName }}</span>
      <span class="node-summary">{{ getNodeSummary(node) }}</span>
      <span v-if="node.durationMs" class="badge-time">{{ formatDuration(node.durationMs) }}</span>
      <span v-if="node.status === 'failed' || node.status === 'orphaned' || node.status === 'aborted'" class="badge-failed">{{ node.status }}</span>
      <span v-else-if="node.status === 'success'" class="badge-success">success</span>
    </div>

    <!-- Collapsed detail preview -->
    <div class="event-detail-preview" :class="{ open: isExpanded && node.hasDetail }" v-if="isExpanded">
      <div class="key-info-mini">
        <span v-if="getAgent(node)">Agent: {{ getAgent(node) }}</span>
        <span v-if="getModel(node)">Model: {{ getModel(node) }}</span>
        <span v-if="getToolName(node)">Tool: {{ getToolName(node) }}</span>
        <span v-if="node.correlationId" class="correlation-id">ID: {{ node.correlationId }}</span>
      </div>

      <!-- User message content -->
      <div class="detail-block" v-if="node.eventType === 'user_message' && node.content">
        <div class="detail-toggle" @click.stop="toggleSection('message')">
          <span>Message</span>
          <span class="arrow" :class="{ open: sections.message }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.message }">
          <pre>{{ node.content }}</pre>
        </div>
      </div>

      <!-- LLM prompt -->
      <div class="detail-block" v-if="node.eventType === 'llm_call' && node.metadata?.prompt">
        <div class="detail-toggle" @click.stop="toggleSection('prompt')">
          <span>Prompt</span>
          <span class="arrow" :class="{ open: sections.prompt }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.prompt }">
          <pre>{{ node.metadata.prompt }}</pre>
        </div>
      </div>

      <!-- LLM response content -->
      <div class="detail-block" v-if="node.eventType === 'llm_call' && node.metadata?.content">
        <div class="detail-toggle" @click.stop="toggleSection('response')">
          <span>Response <span v-if="node.metadata?.tokens" class="badge-token">{{ node.metadata.tokens.total || node.metadata.tokens }} tokens</span></span>
          <span class="arrow" :class="{ open: sections.response }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.response }">
          <pre>{{ node.metadata.content }}</pre>
        </div>
      </div>

      <!-- Tool input/output -->
      <div class="detail-block" v-if="node.eventType === 'tool_call' && node.metadata">
        <div class="detail-toggle" @click.stop="toggleSection('toolInput')">
          <span>Input</span>
          <span class="arrow" :class="{ open: sections.toolInput }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.toolInput }">
          <pre>{{ formatJsonSafe(node.content) }}</pre>
        </div>
        <div class="detail-toggle" @click.stop="toggleSection('toolOutput')" v-if="node.metadata?.output">
          <span>Output</span>
          <span class="arrow" :class="{ open: sections.toolOutput }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.toolOutput }">
          <pre>{{ formatJsonSafe(node.metadata.output) }}</pre>
        </div>
      </div>

      <!-- Agent metadata -->
      <div class="detail-block" v-if="node.eventType === 'agent_turn'">
        <div class="detail-toggle" @click.stop="toggleSection('agentMeta')">
          <span>Metadata</span>
          <span class="arrow" :class="{ open: sections.agentMeta }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.agentMeta }">
          <pre>{{ formatJsonSafe(node.metadata) }}</pre>
        </div>
      </div>
    </div>

    <!-- Recursive children -->
    <div class="event-children" v-if="isExpanded && node.children && node.children.length > 0">
      <AgentEventNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

const props = defineProps({
  node: { type: Object, required: true }
})

const isExpanded = ref(false)
const sections = reactive({})

function toggleExpand() {
  isExpanded.value = !isExpanded.value
}

function toggleSection(name) {
  sections[name] = !sections[name]
}

function getIcon(node) {
  // New span types
  if (node.spanType) {
    const spanIcons = { run: '🏃', agent: '🤖', model: '🧠', tool: '🔧', system: '⚙️' }
    return spanIcons[node.spanType] || '📌'
  }
  // Legacy event types
  const icons = {
    agent_turn: '🤖',
    agent_start: '🤖',
    agent_complete: '🤖',
    llm_call: '🧠',
    llm_request: '🧠',
    llm_response: '🧠',
    tool_call: '🔧',
    tool_request: '🔧',
    tool_response: '🔧',
    user_message: '👤',
    agent_error: '❌'
  }
  return icons[node.eventType] || '📄'
}

function getTypeLabel(node) {
  // New span types
  if (node.spanType) {
    const labels = { run: 'RUN', agent: 'AGENT', model: 'MODEL', tool: 'TOOL', system: 'SYSTEM' }
    return labels[node.spanType] || node.spanType
  }
  // Legacy event types
  const labels = {
    agent_turn: 'Agent',
    agent_start: 'Agent',
    agent_complete: 'Agent',
    llm_call: 'LLM',
    llm_request: 'LLM',
    llm_response: 'LLM',
    tool_call: 'Tool',
    tool_request: 'Tool',
    tool_response: 'Tool',
    user_message: 'User',
    agent_error: 'Error'
  }
  return labels[node.eventType] || node.eventType
}

function getNodeSummary(node) {
  // New span types
  if (node.spanType) {
    if (node.modelName) return node.modelName
    if (node.toolName) return node.toolName
    if (node.agentName) return node.agentName
    return ''
  }
  // Legacy event types
  switch (node.eventType) {
    case 'user_message':
      return node.content ? `"${node.content.substring(0, 60)}${node.content.length > 60 ? '...' : ''}"` : ''
    case 'llm_call':
    case 'llm_request':
    case 'llm_response': {
      const model = node.metadata?.model || node.modelName || ''
      const tokens = node.metadata?.tokens?.total
      return `${model}${tokens ? ` · ${tokens} tokens` : ''}`
    }
    case 'tool_call':
    case 'tool_request':
    case 'tool_response': {
      const tool = node.metadata?.tool_name || node.toolName || ''
      return tool
    }
    default:
      return ''
  }
}

function getAgent(node) {
  return node.agentName || node.metadata?.agent
}

function getModel(node) {
  return node.modelName || node.metadata?.model
}

function getToolName(node) {
  return node.toolName || node.metadata?.tool_name
}

function formatDuration(ms) {
  if (!ms) return '0ms'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatJsonSafe(val) {
  if (!val) return '(empty)'
  if (typeof val === 'object') return JSON.stringify(val, null, 2)
  try { return JSON.stringify(JSON.parse(val), null, 2) } catch { return val }
}
</script>

<style scoped>
.event-node {
  border-left: 2px solid var(--color-border);
}

.event-node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  border-radius: 4px;
  transition: background 0.15s;
  min-height: 32px;
}

.event-node-header:hover {
  background: var(--color-bg-hover);
}

.expand-icon {
  font-size: 10px;
  color: var(--color-text-secondary);
  width: 12px;
  flex-shrink: 0;
}

.event-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.event-type-label {
  font-weight: 600;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.event-type-label.agent_turn { color: #a78bfa; background: rgba(167, 139, 250, 0.1); }
.event-type-label.llm_call { color: #60a5fa; background: rgba(96, 165, 250, 0.1); }
.event-type-label.tool_call { color: #f59e0b; background: rgba(245, 158, 11, 0.1); }
.event-type-label.user_message { color: #34d399; background: rgba(52, 211, 153, 0.1); }
.event-type-label.agent_error { color: #f87171; background: rgba(248, 113, 113, 0.1); }

/* New span type labels */
.event-type-label.RUN { color: #60a5fa; background: rgba(96, 165, 250, 0.1); }
.event-type-label.AGENT { color: #a78bfa; background: rgba(167, 139, 250, 0.1); }
.event-type-label.MODEL { color: #38bdf8; background: rgba(56, 189, 248, 0.1); }
.event-type-label.TOOL { color: #f59e0b; background: rgba(245, 158, 11, 0.1); }
.event-type-label.SYSTEM { color: #94a3b8; background: rgba(148, 163, 184, 0.1); }

.agent-badge {
  font-size: 10px;
  color: #94a3b8;
  background: var(--color-bg-page);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: monospace;
}

.node-summary {
  color: #cbd5e1;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.badge-time {
  font-size: 10px;
  color: #a78bfa;
  background: #1a1a2e;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.badge-failed {
  font-size: 10px;
  color: #f87171;
  background: #450a0a;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.badge-success {
  font-size: 10px;
  color: #34d399;
  background: #052e16;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.badge-token {
  color: #60a5fa;
  font-weight: 500;
}

/* Detail preview */
.event-detail-preview {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
  padding: 0 10px;
}

.event-detail-preview.open {
  max-height: 800px;
  padding: 4px 10px 8px;
}

.key-info-mini {
  display: flex;
  gap: 12px;
  font-size: 10px;
  color: #64748b;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.key-info-mini .correlation-id {
  font-family: monospace;
  color: #475569;
  background: #1e293b;
  padding: 0 4px;
  border-radius: 2px;
}

.detail-block {
  margin-bottom: 4px;
}

.detail-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  padding: 3px 8px;
  border-radius: 4px;
  background: #1e293b;
  font-size: 11px;
  color: #94a3b8;
}

.detail-toggle:hover {
  background: #253349;
}

.detail-toggle .arrow {
  transition: transform 0.2s;
  font-size: 10px;
}

.detail-toggle .arrow.open {
  transform: rotate(90deg);
}

.detail-body {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.25s ease;
}

.detail-body.open {
  max-height: 400px;
  overflow-y: auto;
}

.detail-body pre {
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 4px;
  padding: 8px;
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: #a5d6ff;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* Children indentation */
.event-children {
  margin-left: 20px;
  border-left: 1px dashed #334155;
  padding-left: 8px;
}
</style>
