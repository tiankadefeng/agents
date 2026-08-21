<script setup lang="ts">
import { shallowRef, computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { StreamStatus } from '@/types/sse'
import type { AgentEvent, ReasoningEvent, ToolCallEvent, ToolResultEvent, SubQuestionEvent, SubAnswerEvent, PlanEvent, StepStartEvent, StepCompleteEvent, ReflexionAttemptEvent, ReflexionEvaluateEvent, ReflexionReflectEvent } from '@/types/agent'
import type { TotTree } from '@/composables/useTotTree'
import ToolCallEventCard from './ToolCallEventCard.vue'
import ToolResultEventCard from './ToolResultEventCard.vue'

const props = defineProps<{
  reasoningText: string
  status: StreamStatus
  events?: AgentEvent[]
  // Phase 8 新增
  totTree?: TotTree
  selectedPattern?: string
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

// Phase 8: ToT 树状渲染模式 -- 选中 tot 且树有根节点时启用
const isTotMode = computed(() =>
  props.selectedPattern === 'tot'
  && !!props.totTree
  && props.totTree.rootNodes.value.length > 0
)

// Phase 8: 层级列表 [-1, 0, 1, ..., maxLevel]（-1 = 原始问题根节点）
const totLevels = computed(() => {
  if (!isTotMode.value || !props.totTree) return []
  const levels = [-1]
  for (const n of props.totTree.nodeMap.value.values()) {
    const last = levels[levels.length - 1]
    if (last !== undefined && n.level > last) levels.push(n.level)
  }
  return levels
})

// Phase 8: 判断节点是否在最优路径上
function isOptimalPath(nodeId: number): boolean {
  return props.totTree?.getOptimalPath().some(n => n.nodeId === nodeId) ?? false
}

// Phase 8: 评分徽章三段配色（>=7 金 / 4-6 琥珀 / <=3 红）
function scoreClass(score: number): string {
  if (score >= 7) return 'high'
  if (score >= 4) return 'mid'
  return 'low'
}

// Phase 9: Reflexion round 分组渲染模式 -- 选中 reflexion 且事件列表包含 ReflexionAttemptEvent
const isReflexionMode = computed(() =>
  props.selectedPattern === 'reflexion'
  && !!props.events
  && props.events.some(ev => 'round' in ev && 'answer' in ev && !('score' in ev) && !('reflection' in ev))
)

// Phase 9: 提取所有 round 号，去重排序
const reflexionRounds = computed(() => {
  if (!isReflexionMode.value || !props.events) return []
  const rounds = new Set<number>()
  for (const ev of props.events) {
    if ('round' in ev) {
      rounds.add((ev as any).round as number)
    }
  }
  return Array.from(rounds).sort()
})

// Phase 9: 获取指定 round 的所有事件
function getEventsByRound(round: number): AgentEvent[] {
  return props.events?.filter(ev => 'round' in ev && (ev as any).round === round) ?? []
}

// Phase 9: Reflexion 评分三段配色 (8-10 pass / 4-7 mid / 1-3 low)
// 独立于 ToT 的 scoreClass，因为阈值不同（ToT >=7 金，Reflexion >=8 绿）
function reflexionScoreClass(score: number): string {
  if (score >= 8) return 'high'
  if (score >= 4) return 'mid'
  return 'low'
}

function isPassed(score: number): boolean {
  return score >= 8
}

function isLastRound(round: number): boolean {
  return reflexionRounds.value.length > 0
    && round === reflexionRounds.value[reflexionRounds.value.length - 1]
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
        <!-- Legacy: plain text reasoning (CoT mode) — also used when events array is empty -->
        <template v-if="!events || events.length === 0">
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

      <!-- Phase 8: ToT 树状层-列布局 -->
      <template v-else-if="isTotMode">
        <div class="tot-tree-container">
          <div v-for="level in totLevels" :key="level" class="tot-level">
            <div class="tot-level-label">{{ level === -1 ? '原始问题' : `Level ${level}` }}</div>
            <div
              v-for="node in props.totTree!.getNodesByLevel(level)"
              :key="node.nodeId"
              class="tot-node-card"
              :class="{
                'pruned': node.pruned,
                'optimal-path': isOptimalPath(node.nodeId),
              }"
            >
              <div v-if="level !== -1" class="tot-node-score" :class="scoreClass(node.score)">
                {{ node.score }}/10
              </div>
              <div class="tot-node-thought">{{ node.thought }}</div>
              <div v-if="node.pruned" class="tot-pruned-label">已剪枝</div>
            </div>
          </div>
        </div>
        <!-- 最优路径图例（流式完成后路径确定即显示） -->
        <div v-if="props.totTree!.getOptimalPath().length > 0" class="tot-optimal-legend">
          <span class="legend-dot"></span> 最优路径
        </div>
      </template>

      <!-- Phase 9: Reflexion round 分组布局 -->
      <template v-else-if="isReflexionMode">
        <div class="reflexion-container">
          <template v-for="(round, ri) in reflexionRounds" :key="round">
            <!-- 轮次分隔线 -->
            <div class="round-divider">
              <span class="round-divider-text">Round {{ round }}</span>
            </div>

            <!-- 该轮的所有事件 -->
            <div class="round-group">
              <template v-for="(ev, i) in getEventsByRound(round)" :key="i">
                <!-- ReflexionAttemptEvent -->
                <div v-if="'answer' in ev && !('score' in ev) && !('reflection' in ev)" class="timeline-item reflexion">
                  <span class="dot reflexion-attempt"></span>
                  <div class="label reflexion-attempt">尝试 (Attempt {{ round }})</div>
                  <div class="content">{{ (ev as ReflexionAttemptEvent).answer }}</div>
                </div>
                <!-- ReflexionEvaluateEvent -->
                <div v-else-if="'score' in ev && 'feedback' in ev" class="timeline-item reflexion">
                  <span class="dot reflexion-evaluate"></span>
                  <div class="label reflexion-evaluate">评估 (Evaluate)</div>
                  <div class="evaluate-content">
                    <div class="evaluate-score" :class="reflexionScoreClass((ev as ReflexionEvaluateEvent).score)">
                      评分：{{ (ev as ReflexionEvaluateEvent).score }}/10
                    </div>
                    <div class="evaluate-feedback">{{ (ev as ReflexionEvaluateEvent).feedback }}</div>
                    <div class="evaluate-passed" v-if="isPassed((ev as ReflexionEvaluateEvent).score)">
                      评估通过 (Passed)
                    </div>
                    <div class="evaluate-failed" v-else-if="isLastRound(round)">
                      评估不通过 (Failed)
                    </div>
                    <div class="evaluate-failed" v-else>
                      评估不通过，继续改进
                    </div>
                  </div>
                </div>
                <!-- ReflexionReflectEvent -->
                <div v-else-if="'reflection' in ev" class="timeline-item reflexion">
                  <span class="dot reflexion-reflect"></span>
                  <div class="label reflexion-reflect">反思 (Reflect)</div>
                  <div class="content">{{ (ev as ReflexionReflectEvent).reflection }}</div>
                </div>
              </template>
            </div>
          </template>
        </div>
      </template>

      <!-- Phase 5: vertical timeline (default) -->
      <template v-else>
        <div class="timeline">
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
      </template>
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

/* Phase 8: ToT 树状层-列布局 */
.tot-tree-container {
  display: flex;
  flex-direction: row;
  gap: 24px;
  padding: 16px 0;
  overflow-x: auto;
}
.tot-level {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 200px;
  max-width: 280px;
}
.tot-level-label {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-align: center;
  margin-bottom: 8px;
}
.tot-node-card {
  border: 1px solid #E4E7ED;
  border-radius: 6px;
  background: #FFFFFF;
  padding: 16px;
  position: relative;
  transition: all 0.3s;
}
.tot-node-card.pruned {
  background: #F5F7FA;
  opacity: 0.5;
  text-decoration: line-through;
  color: #C0C4CC;
}
/* optimal-path 规则在 pruned 之后 -- 同节点重叠时金色边框优先 */
.tot-node-card.optimal-path {
  border-color: #F59E0B;
  box-shadow: 0 0 0 1.5px #F59E0B;
}
.tot-node-score {
  position: absolute;
  top: -8px;
  right: -8px;
  color: #FFFFFF;
  font-size: 12px;
  font-weight: 600;
  border-radius: 10px;
  padding: 1px 8px;
  min-width: 24px;
  text-align: center;
}
.tot-node-score.high { background: #F59E0B; }  /* >=7 金 */
.tot-node-score.mid  { background: #E6A23C; }  /* 4-6 琥珀 */
.tot-node-score.low  { background: #F56C6C; }  /* <=3 红 */
.tot-node-thought {
  font-size: 14px;
  line-height: 1.5;
  color: #555555;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 120px;
  overflow-y: auto;
}
.tot-pruned-label {
  font-size: 12px;
  color: #C0C4CC;
  margin-top: 4px;
}
.tot-optimal-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #F59E0B;
  margin-top: 12px;
  padding: 8px 16px;
}
.tot-optimal-legend .legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #F59E0B;
}

/* Phase 9: Reflexion round 分组布局 */
.reflexion-container {
  padding: 12px 0;
}
.round-divider {
  display: flex;
  align-items: center;
  margin: 24px 0 16px;
  padding-left: 0;
}
.round-divider::before,
.round-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  border-top: 1px dashed #E4E7ED;
}
.round-divider-text {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  padding: 0 12px;
  white-space: nowrap;
}
.round-group {
  margin-bottom: 24px;
  padding-left: 24px;
}
.round-group:last-child {
  margin-bottom: 0;
}
.timeline-item.reflexion {
  position: relative;
  padding-left: 24px;
  margin-bottom: 16px;
}
.timeline-item.reflexion:last-child {
  margin-bottom: 0;
}
.dot.reflexion-attempt { border-color: #1D70F5; }
.dot.reflexion-evaluate { border-color: #FAB215; }
.dot.reflexion-reflect { border-color: #8B5CF6; }
.label.reflexion-attempt { color: #1D70F5; font-size: 12px; font-weight: 600; margin-bottom: 4px; }
.label.reflexion-evaluate { color: #FAB215; font-size: 12px; font-weight: 600; margin-bottom: 4px; }
.label.reflexion-reflect { color: #8B5CF6; font-size: 12px; font-weight: 600; margin-bottom: 4px; }
.evaluate-content {
  border: 1px solid #E4E7ED;
  border-radius: 4px;
  padding: 12px;
  margin-top: 8px;
}
.evaluate-score { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.evaluate-score.high { color: #15AC0C; }
.evaluate-score.mid { color: #FAB215; }
.evaluate-score.low { color: #D70016; }
.evaluate-feedback { font-size: 14px; color: #555555; line-height: 1.5; white-space: pre-wrap; word-wrap: break-word; }
.evaluate-passed { font-size: 12px; font-weight: 600; margin-top: 8px; padding: 2px 8px; border-radius: 3px; display: inline-block; background: #E3F6E1; color: #15AC0C; }
.evaluate-failed { font-size: 12px; font-weight: 600; margin-top: 8px; padding: 2px 8px; border-radius: 3px; display: inline-block; background: #FCE5E7; color: #D70016; }
</style>