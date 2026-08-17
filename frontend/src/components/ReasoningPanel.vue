<script setup lang="ts">
import { shallowRef, computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { StreamStatus } from '@/types/sse'
import type { AgentEvent, ReasoningEvent, ToolCallEvent, ToolResultEvent, SubQuestionEvent, SubAnswerEvent, PlanEvent, StepStartEvent, StepCompleteEvent } from '@/types/agent'
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
    return activeNames.value.length > 0 ? '点击收起' : '点击展开推理过程'
  }
  return activeNames.value.length > 0 ? '点击收起' : '点击展开 DeepSeek 的思考链'
})

function handleChange(val: string[]) {
  activeNames.value = val
}

/**
 * Check if the event at the given index is a replan (i.e., the previous event is also a PlanEvent).
 * Used to render the Replan divider before the second PlanEvent.
 */
function isReplan(index: number): boolean {
  if (index <= 0) return false
  const prevEv = props.events?.[index - 1]
  return !!(prevEv && 'steps' in prevEv)
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
          <div v-if="'content' in ev && !('toolName' in ev) && !('message' in ev) && !('steps' in ev)" class="timeline-item thought">
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
          <!-- SubQuestion 块 (Phase 6 Self-Ask) -->
          <div v-else-if="'question' in ev && !('answer' in ev)" class="timeline-item sub-question">
            <span class="dot sub-question"></span>
            <div class="label" style="color: #8B5CF6">子问题 (Sub-Question)</div>
            <div class="content">{{ (ev as SubQuestionEvent).question }}</div>
          </div>
          <!-- SubAnswer 块 (Phase 6 Self-Ask) -->
          <div v-else-if="'question' in ev && 'answer' in ev" class="timeline-item sub-answer">
            <span class="dot sub-answer"></span>
            <div class="label" style="color: #0D9488">子答案 (Sub-Answer)</div>
            <div class="content">{{ (ev as SubAnswerEvent).answer }}</div>
          </div>
          <!-- Phase 7: Plan block (with optional Replan divider) -->
          <template v-else-if="'steps' in ev">
            <div v-if="isReplan(i)" class="replan-divider">
              <span class="replan-divider-text">重新规划 (Replan)</span>
            </div>
            <div class="timeline-item">
              <span class="dot plan"></span>
              <div class="label" style="color: #F59E0B">计划 (Plan)</div>
              <div class="plan-steps-container">
                <div v-for="step in (ev as PlanEvent).steps" :key="step.stepNumber" class="plan-step-item">
                  <div class="plan-step-number">步骤 {{ step.stepNumber }}</div>
                  <div class="plan-step-title">{{ step.description }}</div>
                  <div class="plan-step-expected">
                    <span class="plan-step-expected-label">预期输出: </span>
                    {{ step.expectedOutput }}
                  </div>
                </div>
              </div>
            </div>
          </template>
          <!-- Phase 7: StepStartEvent -> Step running 块 -->
          <div v-else-if="'stepNumber' in ev && !('status' in ev)" class="timeline-item">
            <span class="dot step-running"></span>
            <div class="label" style="color: #1D70F5">步骤 {{ (ev as StepStartEvent).stepNumber }} 执行中</div>
            <div class="content">{{ (ev as StepStartEvent).description }}</div>
          </div>
          <!-- Phase 7: StepCompleteEvent -> Step done/failed 块 -->
          <div v-else-if="'stepNumber' in ev && 'status' in ev && 'result' in ev" class="timeline-item">
            <template v-if="(ev as StepCompleteEvent).status === 'done'">
              <span class="dot step-done"></span>
              <div class="label" style="color: #15AC0C">步骤 {{ (ev as StepCompleteEvent).stepNumber }} 已完成</div>
              <div class="content">{{ (ev as StepCompleteEvent).result }}</div>
            </template>
            <template v-else>
              <span class="dot step-failed"></span>
              <div class="label" style="color: #D70016">步骤 {{ (ev as StepCompleteEvent).stepNumber }} 执行失败</div>
              <div class="content" style="color: #D70016">{{ (ev as StepCompleteEvent).result }}</div>
            </template>
          </div>
          <!-- FinalAnswerEvent and ErrorEvent are skipped (handled by FinalAnswer component and el-alert) -->
        </template>
        <div v-if="events.length === 0" class="empty-state">
          暂无推理过程。提交问题后，这里会流式显示推理过程。
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
.dot.sub-question { border-color: #8B5CF6; }
.dot.sub-answer { border-color: #0D9488; }
/* Phase 7: Plan-and-Execute timeline dots */
.dot.plan { border-color: #F59E0B; }
.dot.step-running { border-color: #1D70F5; }
.dot.step-done { border-color: #15AC0C; }
.dot.step-failed { border-color: #D70016; }
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
/* Phase 7: Plan steps container */
.plan-steps-container {
  border: 1px solid #E4E7ED;
  border-radius: 4px;
  background: #FFFDF5;
  padding: 12px;
  margin-top: 8px;
}
.plan-step-item {
  margin-bottom: 12px;
}
.plan-step-item:last-child {
  margin-bottom: 0;
}
.plan-step-number {
  font-size: 12px;
  font-weight: 600;
  color: #F59E0B;
  margin-bottom: 2px;
}
.plan-step-title {
  font-size: 14px;
  font-weight: 600;
  color: #222222;
  margin-bottom: 2px;
}
.plan-step-expected {
  font-size: 14px;
  color: #666666;
}
.plan-step-expected-label {
  color: #666666;
}
/* Phase 7: Replan divider */
.replan-divider {
  display: flex;
  align-items: center;
  margin: 16px 0;
  padding-left: 24px;
}
.replan-divider::before,
.replan-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  border-top: 1px dashed #E4E7ED;
}
.replan-divider-text {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  padding: 0 12px;
  white-space: nowrap;
}
</style>