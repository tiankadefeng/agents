<script setup lang="ts">
import { shallowRef, computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { StreamStatus } from '@/types/sse'
import type { AgentEvent, ReasoningEvent, ToolCallEvent, ToolResultEvent } from '@/types/agent'
import ToolCallEventCard from './ToolCallEventCard.vue'
import ToolResultEventCard from './ToolResultEventCard.vue'

const props = defineProps<{
  reasoningText: string
  status: StreamStatus
  events?: AgentEvent[]
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

const subtitle = computed(() => {
  if (props.events) {
    return activeNames.value.length > 0 ? '点击收起' : '点击展开 ReAct 推理过程'
  }
  return activeNames.value.length > 0 ? '点击收起' : '点击展开 DeepSeek 的思考链'
})

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
        <!-- Legacy: plain text reasoning (CoT mode) -->
        <template v-if="!events">
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
        </template>

      <!-- Phase 5: vertical timeline (ReAct mode) -->
      <div v-else class="timeline">
        <template v-for="(ev, i) in events" :key="i">
          <!-- ReasoningEvent -> Thought block -->
          <div v-if="'content' in ev && !('toolName' in ev) && !('message' in ev)" class="timeline-item thought">
            <span class="dot thought"></span>
            <div class="label">思考 (Thought)</div>
            <div class="content">{{ (ev as ReasoningEvent).content }}</div>
          </div>
          <!-- ToolCallEvent -> Action block -->
          <div v-else-if="'toolName' in ev && 'arguments' in ev" class="timeline-item action">
            <span class="dot action"></span>
            <div class="label">行动 (Action)</div>
            <ToolCallEventCard
              :tool-name="(ev as ToolCallEvent).toolName"
              :arguments="(ev as ToolCallEvent).arguments"
              :ts="(ev as ToolCallEvent).ts"
            />
          </div>
          <!-- ToolResultEvent -> Observation block -->
          <div v-else-if="'toolName' in ev && 'result' in ev" class="timeline-item observation">
            <span class="dot observation"></span>
            <div class="label">观察 (Observation)</div>
            <ToolResultEventCard
              :tool-name="(ev as ToolResultEvent).toolName"
              :result="(ev as ToolResultEvent).result"
              :is-error="(ev as ToolResultEvent).isError"
              :ts="(ev as ToolResultEvent).ts"
            />
          </div>
          <!-- FinalAnswerEvent and ErrorEvent are skipped (handled by FinalAnswer component and el-alert) -->
        </template>
        <div v-if="events.length === 0" class="empty-state">
          暂无推理过程。提交问题后，这里会流式显示 ReAct 模式的 Thought/Action/Observation 循环。
        </div>
      </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.reasoning-panel {
  background: #FFF6E6;
  border-left: 3px solid var(--design-color-warning);
  border-radius: var(--design-radius-base);
  margin-bottom: 0;
  padding: 0 16px;
}

.collapse-header {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.title {
  font-size: var(--design-font-size-lg);
  font-weight: var(--design-font-weight-bold);
  color: var(--design-text-primary);
}

.subtitle {
  font-size: var(--design-font-size-xs);
  color: var(--design-text-secondary);
  flex: 1;
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

.streaming-dot.thinking {
  background-color: var(--design-color-warning);
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
  font-size: var(--design-font-size-base);
  font-weight: var(--design-font-weight-regular);
  line-height: var(--design-line-height-base);
  color: var(--design-text-regular);
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
  padding: 12px 0;
}

.empty-state {
  font-size: var(--design-font-size-base);
  color: var(--design-text-secondary);
  padding: 12px 0;
}

/* Phase 5: vertical timeline (ReAct mode) */
.timeline {
  padding: 12px 0;
  position: relative;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #E4E7ED;
}
.timeline-item {
  position: relative;
  padding-left: 24px;
  margin-bottom: 16px;
}
.timeline-item:last-child {
  margin-bottom: 0;
}
.dot {
  position: absolute;
  left: 0;
  top: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 3px solid;
  background: #FFFFFF;
  z-index: 1;
}
.dot.thought { border-color: #E6A23C; }
.dot.action { border-color: #1D70F5; }
.dot.observation { border-color: #15AC0C; }
.label {
  font-size: 12px;
  font-weight: 600;
  color: #555555;
  margin-bottom: 4px;
}
.content {
  font-size: 14px;
  line-height: 1.5;
  color: #555555;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>