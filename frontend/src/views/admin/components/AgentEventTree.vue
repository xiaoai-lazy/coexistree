<template>
  <div class="agent-event-tree" v-loading="loading">
    <div v-if="treeNodes.length === 0" class="empty-state">
      <el-empty description="暂无事件数据" />
    </div>
    <AgentEventNode
      v-for="node in treeNodes"
      :key="node.id"
      :node="node"
    />
  </div>
</template>

<script setup>
import AgentEventNode from './AgentEventNode.vue'

defineProps({
  treeNodes: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
</script>

<style scoped>
.agent-event-tree {
  padding: 8px 0;
  overflow-y: auto;
  flex: 1;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}
</style>
