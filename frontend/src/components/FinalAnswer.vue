<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { StreamStatus } from '@/types/sse'

const props = defineProps<{
  finalText: string
  status: StreamStatus
}>()

const statusText = computed(() => {
  switch (props.status) {
    case 'answering':
      return '正在回答...'
    case 'completed':
      return '已完成'
    case 'aborted':
      return '已中断'
    case 'error':
      return '错误'
    default:
      return ''
  }
})
</script>

<template>
  <div class="final-answer">
    <div class="answer-header">
      <span class="title">最终答案</span>
      <div class="status-indicator">
        <span
          v-if="status === 'answering'"
          class="streaming-dot answering"
          aria-label="正在回答"
        ></span>
        <el-icon v-if="status === 'completed'" class="status-icon completed">
          <Check />
        </el-icon>
        <span class="status-text">{{ statusText }}</span>
      </div>
    </div>
    <pre
      v-if="finalText"
      class="answer-content"
      role="log"
      aria-live="polite"
      :aria-busy="status === 'answering'"
    >{{ finalText }}</pre>
    <div v-else class="empty-state">
      暂无最终答案。等待推理完成后，这里会显示模型给出的答案。
    </div>
  </div>
</template>

<style scoped>
.final-answer {
  background: var(--design-bg-white);
  border: 1px solid var(--design-border-color);
  border-left: 3px solid var(--design-color-primary);
  border-radius: var(--design-radius-base);
  padding: 20px;
  min-height: 120px;
}

.answer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--design-divider);
}

.title {
  font-size: var(--design-font-size-lg);
  font-weight: var(--design-font-weight-bold);
  color: var(--design-text-primary);
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-text {
  font-size: var(--design-font-size-xs);
  color: var(--design-text-secondary);
}

.streaming-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 1.2s ease-in-out infinite;
}

.streaming-dot.answering {
  background-color: var(--design-color-primary);
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.85);
  }
}

.answer-content {
  font-size: var(--design-font-size-base);
  font-weight: var(--design-font-weight-regular);
  line-height: var(--design-line-height-base);
  color: var(--design-text-primary);
  white-space: pre-wrap;
  word-wrap: break-word;
  margin: 0;
}

.empty-state {
  font-size: var(--design-font-size-base);
  color: var(--design-text-secondary);
}
</style>