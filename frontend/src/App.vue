<script setup lang="ts">
import { shallowRef, ref, watch } from 'vue'
import { startSSEStream } from '@/composables/useSSEStream'
import { useTotTree } from '@/composables/useTotTree'
import type { StreamStatus } from '@/types/sse'
import type { AgentRequest, AgentEvent } from '@/types/agent'
import PatternSelector from '@/components/PatternSelector.vue'
import QuestionInput from '@/components/QuestionInput.vue'
import ReasoningPanel from '@/components/ReasoningPanel.vue'
import FinalAnswer from '@/components/FinalAnswer.vue'

// Pattern selection state
const selectedPatternId = ref<string>('cot')

// Stream state - use shallowRef for streaming text (D-16, Pitfall #7)
const reasoningText = shallowRef<string>('')
const finalText = shallowRef<string>('')

// 流式改造：临时态 delta 状态（delta 事件拼接，完整事件到达后收口替换；
// 不进 agentEvents 时间线，保持事件序列干净）
const activeThought = shallowRef<string>('')
const activeSpeech = shallowRef<{ round: number; role: string; content: string } | null>(null)

// 分组协议：任何非 delta 事件到达即关闭当前 thought 临时卡片
// （后端保证一轮内 delta 连续，轮间必隔完整事件）
function closeActiveThought() {
  if (activeThought.value) {
    activeThought.value = ''
  }
}

// 分组协议：完整 Role*Event 到达即收口匹配 (round, role) 的临时气泡
function closeRoleSpeech(round: number, role: string) {
  if (activeSpeech.value
      && activeSpeech.value.round === round
      && activeSpeech.value.role === role) {
    activeSpeech.value = null
  }
}

// Error alert uses ref (not streaming, single string)
const errorAlert = ref<string>('')

// AgentEvent list for ReasoningPanel vertical timeline (Phase 5)
const agentEvents = ref<AgentEvent[]>([])

// ToT 树结构状态 (Phase 8)
const totTree = useTotTree()

// Status tracking
const status = ref<StreamStatus>('idle')

// Abort controller for stream cancellation
let abortController: AbortController | null = null

// Track abort state for completion check (module-level for abort() access)
let streamAborted = false

// D-04: Pattern switch aborts SSE, does NOT clear state
watch(selectedPatternId, () => {
  if (status.value === 'thinking' || status.value === 'answering') {
    streamAborted = true
    abortController?.abort()
    status.value = 'aborted'
  }
  // DO NOT clear reasoningText / finalText / inputText (per D-04)
})

/**
 * Handle pattern selection from PatternSelector
 */
function handlePatternSelect(patternId: string) {
  selectedPatternId.value = patternId
}

/**
 * Submit a question and start SSE stream.
 */
async function submit(question: string) {
  reasoningText.value = ''
  finalText.value = ''
  errorAlert.value = ''
  agentEvents.value = []
  activeThought.value = ''
  activeSpeech.value = null
  totTree.clear() // Phase 8: 每次提交重置树状态
  status.value = 'thinking'
  streamAborted = false
  abortController = new AbortController()

  // Track error state for completion check
  let streamError = false

  try {
    const requestBody: AgentRequest = {
      patternId: selectedPatternId.value,
      question,
      options: {}
    }

    await startSSEStream(
      '/api/agent/execute',
      requestBody,
      {
        onReasoning: (content, ev) => {
          closeActiveThought()
          if (selectedPatternId.value === 'reflexion' || selectedPatternId.value === 'roleplay') {
            // Reflexion/Role-playing 模式：Generator/角色发言为 call() 一次性调用，
            // 不产生流式 ReasoningEvent。若收到则忽略，避免与 AttemptEvent/Role*Event 重复
            return
          }
          reasoningText.value += content
          // CoT 模式下每个 token 都是独立 ReasoningEvent，不加入 timeline（避免逐词显示为"思考"块）
          if (selectedPatternId.value !== 'cot') {
            agentEvents.value = [...agentEvents.value, ev]
          }
          status.value = 'thinking'
        },
        // 流式改造：ReAct Thought 增量 -> 临时卡片逐字生长，不进时间线
        onReasoningDelta: (content) => {
          activeThought.value += content
          status.value = 'thinking'
        },
        onToolCall: (ev) => {
          closeActiveThought()
          agentEvents.value = [...agentEvents.value, ev]
        },
        onToolResult: (ev) => {
          closeActiveThought()
          agentEvents.value = [...agentEvents.value, ev]
        },
        onSubQuestion: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]
        },
        onSubAnswer: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]
        },
        onPlan: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]
        },
        onStepStart: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]
        },
        onStepComplete: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]
        },
        // Phase 8: ToT 树事件 -- 双写：树状态 + 事件时间线
        onTotNode: (ev) => {
          totTree.addNode(ev)
          agentEvents.value = [...agentEvents.value, ev]
        },
        onTotPrune: (ev) => {
          totTree.markPruned(ev)
          agentEvents.value = [...agentEvents.value, ev]
        },
        // Phase 9: Reflexion 事件回调 -- 全部经 agentEvents 传递，不追加 reasoningText
        onReflexionAttempt: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]
        },
        onReflexionEvaluate: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]
        },
        onReflexionReflect: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]
        },
        // Phase 10: Role-playing 事件回调 -- 全部经 agentEvents 传递，不追加 reasoningText
        // 流式改造：完整事件到达即收口对应 (round, role) 的临时气泡
        onRolePm: (ev) => {
          closeRoleSpeech(ev.round, ev.role)
          agentEvents.value = [...agentEvents.value, ev]
        },
        onRoleDev: (ev) => {
          closeRoleSpeech(ev.round, ev.role)
          agentEvents.value = [...agentEvents.value, ev]
        },
        onRoleTester: (ev) => {
          closeRoleSpeech(ev.round, ev.role)
          agentEvents.value = [...agentEvents.value, ev]
        },
        // 流式改造：角色发言增量 -> 临时气泡逐字生长，(round, role) 为分组键
        onRoleSpeechDelta: (ev) => {
          if (activeSpeech.value
              && activeSpeech.value.round === ev.round
              && activeSpeech.value.role === ev.role) {
            activeSpeech.value = { ...activeSpeech.value, content: activeSpeech.value.content + ev.content }
          } else {
            activeSpeech.value = { round: ev.round, role: ev.role, content: ev.content }
          }
          status.value = 'thinking'
        },
        onFinal: (content, _ev) => {
          closeActiveThought()
          finalText.value += content
          status.value = 'answering'
        },
        onError: (message, _ev) => {
          closeActiveThought()
          activeSpeech.value = null
          errorAlert.value = message
          status.value = 'error'
          streamError = true
        },
        signal: abortController.signal,
      }
    )

    if (!streamError && !streamAborted) {
      status.value = 'completed'
    }
  } catch (e) {
    if (!streamAborted) {
      errorAlert.value = e instanceof Error ? e.message : String(e)
      status.value = 'error'
    }
  }
}

/**
 * Abort the current stream.
 */
function abort() {
  streamAborted = true
  abortController?.abort()
  status.value = 'aborted'
}

/**
 * Clear all state and reset to idle.
 */
function clear() {
  reasoningText.value = ''
  finalText.value = ''
  errorAlert.value = ''
  agentEvents.value = []
  activeThought.value = ''
  activeSpeech.value = null
  status.value = 'idle'
}
</script>

<template>
  <el-container class="app-container">
    <el-header class="app-header" height="56px">
      <div class="header-title">
        <span class="title-bar"></span>
        <h1>Agent 设计模式教学案例库</h1>
      </div>
    </el-header>
    <el-container>
      <el-aside width="240px" class="app-aside">
        <PatternSelector
          :selected="selectedPatternId"
          @select="handlePatternSelect"
        />
      </el-aside>
      <el-main class="app-main">
        <div class="main-content">
          <QuestionInput
            :streaming="status === 'thinking' || status === 'answering'"
            :pattern-id="selectedPatternId"
            @submit="submit"
            @abort="abort"
            @clear="clear"
          />

          <el-alert
            v-if="errorAlert"
            type="error"
            :title="errorAlert"
            :closable="true"
            @close="errorAlert = ''"
            show-icon
          />

          <ReasoningPanel
            :reasoning-text="reasoningText"
            :events="agentEvents"
            :status="status"
            :tot-tree="totTree"
            :selected-pattern="selectedPatternId"
            :active-thought="activeThought"
            :active-speech="activeSpeech"
          />

          <FinalAnswer :final-text="finalText" :status="status" />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-container {
  height: 100vh;
}

.app-header {
  height: 56px;
  background: var(--design-bg-white);
  border-bottom: 1px solid var(--design-divider);
  display: flex;
  align-items: center;
  padding: 0 var(--design-spacing-lg);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-bar {
  width: 4px;
  height: 24px;
  background: var(--design-color-primary);
  border-radius: 2px;
}

.app-header h1 {
  font-size: var(--design-font-size-lg);
  font-weight: var(--design-font-weight-bold);
  color: var(--design-text-primary);
  margin: 0;
}

.app-aside {
  background: var(--design-bg-page);
  padding: 20px;
}

.app-main {
  background: var(--design-bg-page);
  padding: 20px;
}

.main-content {
  max-width: 1184px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--design-spacing-md);
  width: 100%;
}

.main-content :deep(.el-alert) {
  margin-bottom: 0;
}
</style>