<script setup lang="ts">
import { shallowRef, computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { StreamStatus } from '@/types/sse'

const props = defineProps<{
  reasoningText: string
  status: StreamStatus
}>()

// Default collapsed (D-03)
const activeNames = shallowRef<string[]>([])

const statusText = computed(() => {
  switch (props.status) {
    case 'thinking':
      return '正在思考...'
    case 'answering':
      return '正在思考...' // reasoning may still arrive
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

const subtitle = computed(() =>
  activeNames.value.length > 0 ? '点击收起' : '点击展开 DeepSeek 的思考链'
)

function handleChange(val: string[]) {
  activeNames.value = val
}
</script>

<template>
  <div class="reasoning-panel">
    <el-collapse v-model="activeNames" @change="handleChange">
      <el-collapse-item name="reasoning">
        <template #title>
          <div class="collapse-header">
            <span class="title">推理过程</span>
            <span class="subtitle">{{ subtitle }}</span>
            <div class="status-indicator">
              <span
                v-if="status === 'thinking' || status === 'answering'"
                class="streaming-dot thinking"
                aria-label="正在思考"
              ></span>
              <el-icon v-if="status === 'completed'" class="status-icon completed">
                <Check />
              </el-icon>
              <span class="status-text">{{ statusText }}</span>
            </div>
          </div>
        </template>
        <pre
          v-if="reasoningText"
          class="reasoning-content"
          role="log"
          aria-live="polite"
          :aria-busy="status === 'thinking'"
        >{{ reasoningText }}</pre>
        <div v-else class="empty-state">
          暂无推理过程。提交问题后，这里会流式显示 DeepSeek 的思考链（reasoning_content）。
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.reasoning-panel {
  background: #fdf6ec;
  border-left: 3px solid #e6a23c;
  border-radius: 4px;
  margin-bottom: 24px;
  padding: 0 16px;
}

.collapse-header {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.subtitle {
  font-size: 12px;
  color: #909399;
  flex: 1;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-text {
  font-size: 12px;
  color: #909399;
}

.streaming-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 1.2s ease-in-out infinite;
}

.streaming-dot.thinking {
  background-color: #e6a23c;
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

.reasoning-content {
  font-size: 14px;
  font-weight: 400;
  line-height: 1.5;
  color: #303133;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
  padding: 12px 0;
}

.empty-state {
  font-size: 14px;
  color: #909399;
  padding: 12px 0;
}
</style>