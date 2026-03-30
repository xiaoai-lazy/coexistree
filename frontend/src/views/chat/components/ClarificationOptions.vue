<template>
  <div class="clarification-options">
    <div class="clarification-header">
      <el-icon :size="20" class="clarification-icon">
        <QuestionFilled />
      </el-icon>
      <span class="clarification-text">{{ question }}</span>
    </div>

    <div class="options-list">
      <div
        v-for="(option, index) in options"
        :key="index"
        class="option-item"
        :class="{ selected: selectedIndex === index }"
        @click="selectOption(index)"
      >
        <div class="option-marker">{{ String.fromCharCode(65 + index) }}</div>
        <div class="option-content">
          <span class="option-label">{{ option.label }}</span>
          <span class="option-desc">{{ option.description }}</span>
        </div>
        <el-icon v-if="selectedIndex === index" class="check-icon">
          <Check />
        </el-icon>
      </div>
    </div>

    <div v-if="selectedIndex !== null" class="confirmation-area">
      <el-button type="primary" size="small" @click="confirm">
        确认选择
      </el-button>
      <el-button link size="small" @click="selectedIndex = null">
        重新选择
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { QuestionFilled, Check } from '@element-plus/icons-vue'

const props = defineProps({
  question: {
    type: String,
    default: '您想进行以下哪种操作？'
  },
  options: {
    type: Array,
    required: true,
    // [{ label: '问答', description: '了解系统现有功能', value: 'QUESTION' }, ...]
  }
})

const emit = defineEmits(['select'])

const selectedIndex = ref(null)

const selectOption = (index) => {
  selectedIndex.value = index
}

const confirm = () => {
  if (selectedIndex.value !== null) {
    emit('select', props.options[selectedIndex.value])
  }
}
</script>

<style scoped>
.clarification-options {
  max-width: 600px;
  margin: 12px 0;
  padding: 20px;
  background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%);
  border: 1px solid #d8b4fe;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(147, 51, 234, 0.1);
}

.clarification-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.clarification-icon {
  color: #9333ea;
}

.clarification-text {
  font-size: 15px;
  font-weight: 500;
  color: #581c87;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #ffffff;
  border: 2px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.option-item:hover {
  border-color: #c084fc;
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(147, 51, 234, 0.15);
}

.option-item.selected {
  border-color: #9333ea;
  background: #faf5ff;
  box-shadow: 0 4px 16px rgba(147, 51, 234, 0.2);
}

.option-marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 14px;
  font-weight: 600;
  color: #9333ea;
  background: #f3e8ff;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.option-item:hover .option-marker {
  background: #e9d5ff;
  transform: scale(1.1);
}

.option-item.selected .option-marker {
  color: #ffffff;
  background: #9333ea;
}

.option-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.option-label {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.option-desc {
  font-size: 12px;
  color: #64748b;
}

.check-icon {
  color: #9333ea;
  animation: scaleIn 0.3s ease;
}

@keyframes scaleIn {
  from {
    transform: scale(0);
  }
  to {
    transform: scale(1);
  }
}

.confirmation-area {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e9d5ff;
}
</style>
