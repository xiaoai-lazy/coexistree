<template>
  <div class="span-waterfall" v-loading="loading">
    <el-empty v-if="flattened.length === 0" description="暂无数据" />
    <template v-else>
      <!-- Time axis -->
      <div class="time-axis">
        <div class="time-labels">
          <span
            v-for="tick in timeTicks"
            :key="tick.value"
            class="time-tick"
            :style="{ left: tick.percent + '%' }"
          >{{ tick.label }}</span>
        </div>
      </div>

      <!-- Span rows -->
      <div
        v-for="span in flattened"
        :key="span.spanId"
        class="span-row"
        :class="[`depth-${span.depth}`, `type-${span.spanType}`, { 'row-active': activeSpan === span.spanId }]"
      >
        <!-- Gantt track only -->
        <div class="span-track">
          <div
            class="span-bar"
            :class="`bar-${span.spanType}`"
            :style="{ left: span.leftPercent + '%', width: Math.max(span.widthPercent, 0.5) + '%' }"
            @mouseenter="handleBarMouseEnter(span, $event)"
            @mouseleave="hoveredSpan = null"
            @mousemove="handleBarMouseMove($event)"
            @click="toggleSpan(span.spanId)"
          >
            <span class="bar-icon-wrap" :class="`icon-${span.spanType}`">
              <component :is="typeSvg(span.spanType)" />
            </span>
            <span class="bar-name">{{ span.spanName }}</span>
            <span v-if="span.modelName" class="bar-badge">{{ span.modelName }}</span>
            <span v-if="span.toolName" class="bar-badge tool">{{ span.toolName }}</span>
            <span class="bar-status" :class="span.status">{{ span.status === 'success' ? '✓' : '✗' }}</span>
            <span class="bar-duration">{{ formatDuration(span.durationMs) }}</span>
          </div>

          <!-- Tooltip (not teleported, positioned relative to parent) -->
          <div v-if="hoveredSpan && hoveredSpan.spanId === span.spanId" class="span-tooltip" :style="tooltipStyle">
            <div class="tooltip-header">
              <span class="tooltip-type">{{ span.spanType }}</span>
              <span class="tooltip-name">{{ span.spanName }}</span>
              <span class="tooltip-status" :class="span.status">{{ span.status }}</span>
            </div>
            <div class="tooltip-body">
              <div class="tooltip-row"><span class="tooltip-label">Span ID</span><span class="tooltip-val mono">{{ span.spanId }}</span></div>
              <div class="tooltip-row" v-if="span.parentSpanId"><span class="tooltip-label">Parent</span><span class="tooltip-val mono">{{ span.parentSpanId }}</span></div>
              <div class="tooltip-row" v-if="span.modelName"><span class="tooltip-label">Model</span><span class="tooltip-val">{{ span.modelName }}</span></div>
              <div class="tooltip-row"><span class="tooltip-label">Duration</span><span class="tooltip-val">{{ formatDuration(span.durationMs) }}</span></div>
              <div class="tooltip-row"><span class="tooltip-label">Start</span><span class="tooltip-val">{{ formatDateTime(span.startedAt) }}</span></div>
              <div class="tooltip-row"><span class="tooltip-label">End</span><span class="tooltip-val">{{ formatDateTime(span.finishedAt) }}</span></div>
              <div class="tooltip-row" v-if="span.inputTokens != null"><span class="tooltip-label">Tokens</span><span class="tooltip-val">{{ span.inputTokens }} in / {{ span.outputTokens ?? '?' }} out</span></div>
              <div class="tooltip-row" v-if="span.inputPayload?.promptPreview"><span class="tooltip-label">Prompt</span><span class="tooltip-val">{{ truncate(span.inputPayload.promptPreview, 100) }}</span></div>
              <div class="tooltip-row" v-if="span.outputPayload?.content"><span class="tooltip-label">Response</span><span class="tooltip-val">{{ truncate(span.outputPayload.content, 100) }}</span></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Expanded detail panel -->
      <div v-if="activeSpan" class="detail-panel">
        <div class="detail-header">
          <span class="detail-title">
            <span class="type-icon-wrap" :class="`icon-${activeSpanData?.spanType}`">
              <component :is="typeSvg(activeSpanData?.spanType)" />
            </span>
            {{ activeSpanData?.spanName }}
            <span class="badge" :class="activeSpanData?.spanType">{{ activeSpanData?.spanType }}</span>
          </span>
          <span class="detail-close" @click="activeSpan = null">✕</span>
        </div>
        <div class="detail-content">
          <div v-if="activeSpanData?.inputPayload?.promptPreview" class="detail-section">
            <div class="section-title">Prompt</div>
            <pre class="section-body">{{ activeSpanData.inputPayload.promptPreview }}</pre>
          </div>
          <div v-if="activeSpanData?.outputPayload?.content" class="detail-section">
            <div class="section-title">Response</div>
            <pre class="section-body">{{ activeSpanData.outputPayload.content }}</pre>
          </div>
          <div v-if="activeSpanData?.attributes && Object.keys(activeSpanData.attributes).length" class="detail-section">
            <div class="section-title">Attributes</div>
            <pre class="section-body">{{ JSON.stringify(activeSpanData.attributes, null, 2) }}</pre>
          </div>
          <div v-if="activeSpanData?.inputPayload && !activeSpanData.inputPayload.promptPreview && Object.keys(activeSpanData.inputPayload).length" class="detail-section">
            <div class="section-title">Input Payload</div>
            <pre class="section-body">{{ JSON.stringify(activeSpanData.inputPayload, null, 2) }}</pre>
          </div>
          <div v-if="activeSpanData?.outputPayload && !activeSpanData.outputPayload.content && Object.keys(activeSpanData.outputPayload).length" class="detail-section">
            <div class="section-title">Output Payload</div>
            <pre class="section-body">{{ JSON.stringify(activeSpanData.outputPayload, null, 2) }}</pre>
          </div>
          <div class="detail-section">
            <div class="section-title">Raw JSON</div>
            <pre class="section-body">{{ JSON.stringify(activeSpanData, null, 2) }}</pre>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  tree: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const hoveredSpan = ref(null)
const activeSpan = ref(null)
const tooltipPos = ref({ top: 0, left: 0 })

// Flatten tree to list with time offsets
const flattened = computed(() => {
  if (!props.tree) return []
  const result = []
  const totalDuration = props.tree.durationMs || 1
  const rootStart = props.tree.startedAt

  function walk(node, depth) {
    const offsetMs = calcOffset(node.startedAt, rootStart)
    const leftPercent = (offsetMs / totalDuration) * 100
    const widthPercent = (node.durationMs / totalDuration) * 100

    result.push({
      ...node,
      depth,
      offsetMs,
      leftPercent,
      widthPercent,
      inputTokens: node.attributes?.tokenInput ?? null,
      outputTokens: node.attributes?.tokenOutput ?? null
    })

    if (node.children) {
      for (const child of node.children) {
        walk(child, depth + 1)
      }
    }
  }

  walk(props.tree, 0)
  return result
})

const activeSpanData = computed(() => {
  if (!activeSpan.value) return null
  return flattened.value.find(s => s.spanId === activeSpan.value) || null
})

const timeTicks = computed(() => {
  if (!props.tree) return []
  const total = props.tree.durationMs || 1
  return [
    { label: '0ms', percent: 0 },
    { label: formatMs(total * 0.25), percent: 25 },
    { label: formatMs(total * 0.5), percent: 50 },
    { label: formatMs(total * 0.75), percent: 75 },
    { label: formatMs(total), percent: 100 }
  ]
})

const tooltipStyle = computed(() => {
  if (!hoveredSpan.value) return {}
  return {
    position: 'fixed',
    top: tooltipPos.value.top + 'px',
    left: tooltipPos.value.left + 'px',
    zIndex: 9999
  }
})

function calcOffset(dateStr, rootStart) {
  if (!dateStr || !rootStart) return 0
  const d1 = new Date(dateStr).getTime()
  const d0 = new Date(rootStart).getTime()
  return Math.max(0, d1 - d0)
}

function toggleSpan(id) {
  activeSpan.value = activeSpan.value === id ? null : id
}

function formatDuration(ms) {
  if (!ms && ms !== 0) return ''
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatMs(ms) {
  if (ms < 1000) return `${Math.round(ms)}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatDateTime(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function truncate(s, maxLen) {
  if (!s) return ''
  return s.length > maxLen ? s.substring(0, maxLen) + '...' : s
}

function typeSvg(type) {
  const icons = {
    run: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12,6 12,12 16,14"/></svg>',
    agent: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9h6M9 13h4"/></svg>',
    model: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>',
    tool: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14,2 14,8 20,8"/></svg>',
    system: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="3"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg>'
  }
  return { template: icons[type] || icons.system }
}

function handleBarMouseEnter(span, event) {
  hoveredSpan.value = span
  tooltipPos.value = { top: event.clientY + 12, left: event.clientX + 12 }
}

function handleBarMouseMove(event) {
  if (hoveredSpan.value) {
    tooltipPos.value = { top: event.clientY + 12, left: event.clientX + 12 }
  }
}

onMounted(() => {
  document.addEventListener('mousemove', handleGlobalMouseMove)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', handleGlobalMouseMove)
})

function handleGlobalMouseMove(event) {
  if (hoveredSpan.value) {
    tooltipPos.value = { top: event.clientY + 12, left: event.clientX + 12 }
  }
}
</script>

<style scoped>
.span-waterfall {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
  font-family: 'SF Mono', 'Cascadia Code', 'Fira Code', monospace;
}

/* Time axis */
.time-axis {
  position: relative;
  height: 24px;
  margin-bottom: 8px;
  border-bottom: 1px solid #334155;
}

.time-labels {
  position: relative;
  height: 100%;
}

.time-tick {
  position: absolute;
  font-size: 9px;
  color: #475569;
  transform: translateX(-50%);
}

.time-tick::before {
  content: '';
  display: block;
  width: 1px;
  height: 6px;
  background: #334155;
  margin: 0 auto 2px;
}

/* Span row */
.span-row {
  display: flex;
  align-items: center;
  height: 32px;
  cursor: pointer;
  transition: background 0.1s;
  border-radius: 4px;
  margin-bottom: 1px;
}

.span-row:hover {
  background: rgba(255, 255, 255, 0.02);
}

.span-row.row-active {
  background: rgba(255, 255, 255, 0.04);
}

/* Depth indentation via left margin on the track */
.span-row.depth-1 { padding-left: 16px; }
.span-row.depth-2 { padding-left: 32px; }
.span-row.depth-3 { padding-left: 48px; }
.span-row.depth-4 { padding-left: 64px; }
.span-row.depth-5 { padding-left: 80px; }

/* Gantt track */
.span-track {
  flex: 1;
  position: relative;
  height: 100%;
}

/* Span bar on the Gantt track */
.span-bar {
  position: absolute;
  top: 4px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 6px;
  cursor: pointer;
  transition: filter 0.15s, box-shadow 0.15s;
  overflow: hidden;
}

.span-bar:hover {
  filter: brightness(1.3);
  box-shadow: 0 0 8px rgba(255, 255, 255, 0.1);
  z-index: 10;
}

.bar-icon-wrap {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bar-icon-wrap svg {
  width: 12px;
  height: 12px;
}

.icon-run svg { color: rgba(255, 255, 255, 0.8); }
.icon-agent svg { color: rgba(255, 255, 255, 0.8); }
.icon-model svg { color: rgba(255, 255, 255, 0.8); }
.icon-tool svg { color: rgba(255, 255, 255, 0.8); }
.icon-system svg { color: rgba(255, 255, 255, 0.7); }

.bar-name {
  font-size: 10px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.95);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 1;
  min-width: 0;
}

.bar-badge {
  padding: 1px 4px;
  border-radius: 2px;
  font-size: 8px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.15);
  flex-shrink: 0;
  white-space: nowrap;
}

.bar-badge.tool { background: rgba(255, 255, 255, 0.2); }

.bar-status {
  padding: 0 3px;
  border-radius: 2px;
  font-size: 8px;
  font-weight: 600;
  flex-shrink: 0;
}

.bar-status.success { color: rgba(167, 243, 208, 0.9); background: rgba(52, 211, 153, 0.2); }
.bar-status.failed { color: rgba(252, 165, 165, 0.9); background: rgba(248, 113, 113, 0.2); }

.bar-duration {
  font-size: 9px;
  color: rgba(255, 255, 255, 0.7);
  flex-shrink: 0;
  margin-left: auto;
  padding-left: 4px;
}

/* Bar type colors */
.bar-run { background: rgba(96, 165, 250, 0.55); }
.bar-agent { background: rgba(167, 139, 250, 0.55); }
.bar-model { background: rgba(56, 189, 248, 0.55); }
.bar-tool { background: rgba(245, 158, 11, 0.55); }
.bar-system { background: rgba(148, 163, 184, 0.4); }

.bar-run:hover { background: rgba(96, 165, 250, 0.8); }
.bar-agent:hover { background: rgba(167, 139, 250, 0.8); }
.bar-model:hover { background: rgba(56, 189, 248, 0.8); }
.bar-tool:hover { background: rgba(245, 158, 11, 0.8); }

/* Tooltip */
.span-tooltip {
  background: #0f172a;
  border: 1px solid #475569;
  border-radius: 8px;
  padding: 10px 14px;
  min-width: 280px;
  max-width: 400px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  pointer-events: none;
}

.tooltip-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #334155;
}

.tooltip-type {
  font-size: 9px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.tooltip-name {
  font-size: 12px;
  font-weight: 600;
  color: #f1f5f9;
}

.tooltip-status {
  margin-left: auto;
  font-size: 9px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 3px;
}

.tooltip-status.success { color: #34d399; background: rgba(52, 211, 153, 0.15); }
.tooltip-status.failed { color: #f87171; background: rgba(248, 113, 113, 0.15); }

.tooltip-body {
  font-size: 10px;
}

.tooltip-row {
  display: flex;
  gap: 8px;
  padding: 3px 0;
  color: #94a3b8;
}

.tooltip-label {
  color: #64748b;
  min-width: 56px;
  flex-shrink: 0;
}

.tooltip-val {
  color: #cbd5e1;
  word-break: break-all;
}

.tooltip-val.mono {
  font-family: 'SF Mono', 'Cascadia Code', monospace;
  font-size: 9px;
}

/* Detail panel */
.detail-panel {
  margin-top: 12px;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #1e293b;
  background: #1a2744;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #f1f5f9;
}

.detail-close {
  cursor: pointer;
  color: #64748b;
  font-size: 14px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}

.detail-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #f1f5f9;
}

.detail-content {
  padding: 12px 16px;
  max-height: 500px;
  overflow-y: auto;
}

.detail-section {
  margin-bottom: 12px;
}

.section-title {
  font-size: 10px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
  padding-bottom: 4px;
  border-bottom: 1px solid #1e293b;
}

.section-body {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 4px;
  padding: 10px;
  font-size: 11px;
  line-height: 1.5;
  color: #a5d6ff;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
  margin: 0;
}

/* Shared icon styles */
.type-icon-wrap {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.type-icon-wrap svg {
  width: 14px;
  height: 14px;
}

.badge {
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 9px;
  font-weight: 600;
  flex-shrink: 0;
}

.badge.model { background: rgba(56, 189, 248, 0.15); color: #38bdf8; }
.badge.tool { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
</style>
