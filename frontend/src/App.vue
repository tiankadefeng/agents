<script setup lang="ts">
import { shallowRef, ref, watch } from 'vue'
import { startSSEStream } from '@/composables/useSSEStream'
import type { StreamStatus } from '@/types/sse'
import type { AgentRequest } from '@/types/agent'
import { PATTERN_DETAILS } from '@/constants/patternDetails'
import PatternSelector from '@/components/PatternSelector.vue'
import QuestionInput from '@/components/QuestionInput.vue'
import ReasoningPanel from '@/components/ReasoningPanel.vue'
import FinalAnswer from '@/components/FinalAnswer.vue'

// Pattern selection state
const selectedPatternId = ref<string>('cot')

// Stream state - use shallowRef for streaming text (D-16, Pitfall #7)
const reasoningText = shallowRef<string>('')
const finalText = shallowRef<string>('')

// Error alert uses ref (not streaming, single string)
const errorAlert = ref<string>('')

// Status tracking
const status = ref<StreamStatus>('idle')

// Abort controller for stream cancellation
let abortController: AbortController | null = null

// Track abort state for completion check (module-level for abort() access)
let streamAborted = false

// D-04: Pattern switch aborts SSE, does NOT clear state
watch(selectedPatternId, () => {
  if (status.value === 'thinking' || status.value === 'answering') {
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
        onReasoning: (content, _ev) => {
          reasoningText.value += content
          status.value = 'thinking'
        },
        onFinal: (content, _ev) => {
          finalText.value += content
          status.value = 'answering'
        },
        onError: (message, _ev) => {
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
      errorAlert.value = (e as Error).message
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
            :examples="PATTERN_DETAILS[selectedPatternId]?.examples || []"
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

          <ReasoningPanel :reasoning-text="reasoningText" :status="status" />

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
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  padding: 0 24px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-bar {
  width: 4px;
  height: 24px;
  background: #409eff;
  border-radius: 2px;
}

.app-header h1 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.app-aside {
  background: #f5f7fa;
  padding: 16px;
}

.app-main {
  background: #fff;
  padding: 24px 32px;
}

.main-content {
  max-width: 960px;
  margin: 0 auto;
}
</style>