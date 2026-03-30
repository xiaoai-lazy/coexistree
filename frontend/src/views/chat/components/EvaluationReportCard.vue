<template>
  <div class="evaluation-report" :class="`risk-${riskLevel.toLowerCase()}`">
    <!-- Header -->
    <div class="report-header">
      <div class="header-main">
        <div class="category-icon" :class="categoryClass">
          <el-icon :size="20">
            <component :is="categoryIcon" />
          </el-icon>
        </div>
        <div class="header-content">
          <h4 class="category-title">{{ categoryTitle }}</h4>
          <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <div class="risk-badge" :class="`risk-${riskLevel.toLowerCase()}`">
        <el-icon v-if="riskIcon" :size="14">
          <component :is="riskIcon" />
        </el-icon>
        <span>{{ riskLabel }}</span>
      </div>
    </div>

    <!-- Divider -->
    <div class="divider" />

    <!-- Content -->
    <div class="report-content">
      <!-- Summary -->
      <p v-if="summary" class="summary">{{ summary }}</p>

      <!-- Detail Items -->
      <div v-if="details && details.length > 0" class="detail-list">
        <div
          v-for="(item, index) in details"
          :key="index"
          class="detail-item"
          :class="{ expandable: item.suggestion }"
          @click="toggleExpand(index)"
        >
          <div class="item-header">
            <div class="item-marker" :class="`severity-${item.severity || 'medium'}`">
              <el-icon :size="12">
                <component :is="severityIcon(item.severity)" />
              </el-icon>
            </div>
            <h5 class="item-title">{{ item.name }}</h5>
            <el-icon v-if="item.suggestion" class="expand-icon" :class="{ expanded: expandedItems.includes(index) }">
              <ArrowDown />
            </el-icon>
          </div>
          <p class="item-description">{{ item.description }}</p>

          <!-- Expandable Suggestion -->
          <transition name="expand">
            <div v-if="item.suggestion && expandedItems.includes(index)" class="item-suggestion">
              <div class="suggestion-label">建议</div>
              <p>{{ item.suggestion }}</p>
            </div>
          </transition>
        </div>
      </div>

      <!-- Empty State -->
      <div v-else-if="showEmpty" class="empty-state">
        <el-icon :size="32" class="empty-icon">
          <CircleCheck />
        </el-icon>
        <p>未发现明显问题</p>
      </div>
    </div>

    <!-- Footer Actions -->
    <div v-if="actions && actions.length > 0" class="report-footer">
      <el-button
        v-for="(action, index) in actions"
        :key="index"
        :type="action.type || 'default'"
        :link="action.link"
        size="small"
        @click="action.handler"
      >
        {{ action.label }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  WarningFilled,
  CircleCheck,
  InfoFilled,
  Document,
  ScaleToOriginal,
  Histogram,
  Clock,
  ArrowDown,
  Close
} from '@element-plus/icons-vue'

const props = defineProps({
  category: {
    type: String,
    required: true,
    validator: (val) => ['CONFLICT', 'CONSISTENCY', 'IMPACT', 'HISTORY'].includes(val)
  },
  riskLevel: {
    type: String,
    required: true,
    validator: (val) => ['HIGH', 'MEDIUM', 'LOW', 'NONE'].includes(val)
  },
  subtitle: {
    type: String,
    default: ''
  },
  summary: {
    type: String,
    default: ''
  },
  details: {
    type: Array,
    default: () => []
  },
  actions: {
    type: Array,
    default: () => []
  },
  showEmpty: {
    type: Boolean,
    default: true
  }
})

const expandedItems = ref([])

const categoryConfig = {
  CONFLICT: {
    title: '功能冲突检测',
    icon: 'ScaleToOriginal',
    class: 'conflict'
  },
  CONSISTENCY: {
    title: '业务规则一致性',
    icon: 'Document',
    class: 'consistency'
  },
  IMPACT: {
    title: '依赖模块识别',
    icon: 'Histogram',
    class: 'impact'
  },
  HISTORY: {
    title: '历史背景/现状一致性',
    icon: 'Clock',
    class: 'history'
  }
}

const categoryTitle = computed(() => categoryConfig[props.category]?.title)
const categoryIcon = computed(() => categoryConfig[props.category]?.icon)
const categoryClass = computed(() => categoryConfig[props.category]?.class)

const riskConfig = {
  HIGH: { label: '高风险', icon: 'WarningFilled' },
  MEDIUM: { label: '中风险', icon: 'InfoFilled' },
  LOW: { label: '低风险', icon: 'InfoFilled' },
  NONE: { label: '无风险', icon: 'CircleCheck' }
}

const riskLabel = computed(() => riskConfig[props.riskLevel]?.label)
const riskIcon = computed(() => riskConfig[props.riskLevel]?.icon)

const severityIcon = (severity) => {
  const icons = {
    high: 'Close',
    medium: 'WarningFilled',
    low: 'InfoFilled',
    none: 'CircleCheck'
  }
  return icons[severity] || 'InfoFilled'
}

const toggleExpand = (index) => {
  const pos = expandedItems.value.indexOf(index)
  if (pos > -1) {
    expandedItems.value.splice(pos, 1)
  } else {
    expandedItems.value.push(index)
  }
}
</script>

<style scoped>
.evaluation-report {
  max-width: 720px;
  margin: 12px 0;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  transition: all 0.3s ease;
}

.evaluation-report:hover {
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.1);
}

/* Risk Level Styles */
.evaluation-report.risk-high {
  border-color: #fecaca;
  background: linear-gradient(180deg, #ffffff 0%, #fef2f2 100%);
}

.evaluation-report.risk-medium {
  border-color: #fed7aa;
  background: linear-gradient(180deg, #ffffff 0%, #fff7ed 100%);
}

.evaluation-report.risk-low {
  border-color: #fde68a;
  background: linear-gradient(180deg, #ffffff 0%, #fefce8 100%);
}

.evaluation-report.risk-none {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #ffffff 0%, #f0fdf4 100%);
}

/* Header */
.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
}

.header-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.category-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  transition: all 0.3s ease;
}

.category-icon.conflict {
  color: #dc2626;
  background: #fee2e2;
}

.category-icon.consistency {
  color: #ea580c;
  background: #ffedd5;
}

.category-icon.impact {
  color: #0891b2;
  background: #cffafe;
}

.category-icon.history {
  color: #7c3aed;
  background: #ede9fe;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.category-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.subtitle {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

/* Risk Badge */
.risk-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 20px;
}

.risk-badge.risk-high {
  color: #dc2626;
  background: #fee2e2;
}

.risk-badge.risk-medium {
  color: #ea580c;
  background: #ffedd5;
}

.risk-badge.risk-low {
  color: #ca8a04;
  background: #fef9c3;
}

.risk-badge.risk-none {
  color: #16a34a;
  background: #dcfce7;
}

/* Divider */
.divider {
  height: 1px;
  margin: 0 20px;
  background: linear-gradient(90deg, transparent 0%, #e2e8f0 50%, transparent 100%);
}

/* Content */
.report-content {
  padding: 16px 20px;
}

.summary {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.7;
  color: #475569;
}

/* Detail List */
.detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-item {
  padding: 14px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  transition: all 0.3s ease;
}

.detail-item:hover {
  background: rgba(255, 255, 255, 0.9);
  border-color: #cbd5e1;
  transform: translateX(4px);
}

.detail-item.expandable {
  cursor: pointer;
}

.item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.item-marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
}

.item-marker.severity-high {
  color: #dc2626;
  background: #fee2e2;
}

.item-marker.severity-medium {
  color: #ea580c;
  background: #ffedd5;
}

.item-marker.severity-low {
  color: #ca8a04;
  background: #fef9c3;
}

.item-marker.severity-none {
  color: #16a34a;
  background: #dcfce7;
}

.item-title {
  flex: 1;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.expand-icon {
  color: #94a3b8;
  transition: transform 0.3s ease;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.item-description {
  margin: 0;
  padding-left: 32px;
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
}

/* Suggestion */
.item-suggestion {
  margin-top: 12px;
  margin-left: 32px;
  padding: 12px;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border-left: 3px solid #3b82f6;
  border-radius: 0 8px 8px 0;
}

.suggestion-label {
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.item-suggestion p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #1e40af;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px;
  text-align: center;
}

.empty-icon {
  margin-bottom: 12px;
  color: #22c55e;
}

.empty-state p {
  margin: 0;
  font-size: 14px;
  color: #16a34a;
}

/* Footer */
.report-footer {
  display: flex;
  gap: 8px;
  padding: 12px 20px;
  background: rgba(248, 250, 252, 0.6);
  border-top: 1px solid #e2e8f0;
}

/* Expand Animation */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  max-height: 200px;
  opacity: 1;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
