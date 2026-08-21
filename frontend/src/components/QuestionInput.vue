<script setup lang="ts">
import { ref, computed } from 'vue'
import { Promotion, Delete, Close } from '@element-plus/icons-vue'
import { PATTERN_DETAILS } from '@/constants/patternDetails'

const props = defineProps<{
  streaming: boolean
  patternId: string
}>()

const emit = defineEmits<{
  submit: [question: string]
  abort: []
  clear: []
}>()

const question = ref('')

const examples = computed(() => PATTERN_DETAILS[props.patternId]?.examples ?? [])

const canSubmit = computed(() => question.value.trim().length > 0 && !props.streaming)

function onSubmit() {
  if (!canSubmit.value) return
  emit('submit', question.value)
}

function onClear() {
  question.value = ''
  emit('clear')
}

function onPickExample(example: string) {
  question.value = example
}

function onKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    onSubmit()
  }
}
</script>

<template>
  <div class="question-input">
    <label class="input-label">问题</label>
    <el-input
      v-model="question"
      type="textarea"
      :rows="3"
      :disabled="streaming"
      placeholder="请输入你的问题，例如：证明 √2 是无理数"
      @keydown="onKeydown"
    />
    <div v-if="examples.length > 0 && !streaming" class="example-section">
      <span class="example-title">示例问题</span>
      <div class="example-list">
        <button
          v-for="example in examples"
          :key="example"
          type="button"
          class="example-item"
          @click="onPickExample(example)"
        >
          {{ example }}
        </button>
      </div>
    </div>
    <div class="button-row">
      <el-button v-if="streaming" type="default" :icon="Close" @click="emit('abort')">
        中断
      </el-button>
      <el-button type="default" :icon="Delete" :disabled="streaming" @click="onClear">
        清空
      </el-button>
      <el-button
        type="primary"
        :icon="Promotion"
        :disabled="!canSubmit"
        :loading="streaming"
        @click="onSubmit"
      >
        {{ streaming ? '推理中...' : '提交问题' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.question-input {
  margin-bottom: 0;
}

.input-label {
  display: block;
  font-size: var(--design-font-size-base);
  color: var(--design-text-primary);
  font-weight: 500;
  margin-bottom: var(--design-spacing-sm);
}

.example-section {
  margin-top: var(--design-spacing-sm);
}

.example-title {
  display: block;
  font-size: var(--design-font-size-sm, 12px);
  color: var(--design-text-secondary, #909399);
  margin-bottom: var(--design-spacing-xs, 6px);
}

.example-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.example-item {
  display: block;
  width: 100%;
  padding: 6px 10px;
  text-align: left;
  font-size: var(--design-font-size-sm, 13px);
  line-height: 1.5;
  color: var(--design-text-secondary, #606266);
  background: transparent;
  border: 1px dashed var(--design-divider, #e4e7ed);
  border-radius: var(--design-radius-base, 4px);
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s, background-color 0.2s;
}

.example-item:hover {
  color: var(--design-color-primary);
  border-color: var(--design-color-primary);
  background-color: var(--design-color-primary-10, rgba(64, 158, 255, 0.08));
}

.example-item:active {
  background-color: var(--design-color-primary-20, rgba(64, 158, 255, 0.16));
}

.button-row {
  display: flex;
  gap: var(--design-spacing-sm);
  justify-content: flex-end;
  margin-top: 12px;
}

.question-input :deep(.el-textarea__inner:focus) {
  border-color: var(--design-color-primary);
}

.question-input :deep(.el-button--primary) {
  background-color: var(--design-color-primary);
  border-color: var(--design-color-primary);
  border-radius: var(--design-radius-base);
}
</style>