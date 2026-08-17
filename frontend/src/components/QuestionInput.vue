<script setup lang="ts">
import { ref, computed } from 'vue'
import { Promotion, Delete, Close } from '@element-plus/icons-vue'

const props = defineProps<{
  streaming: boolean
}>()

const emit = defineEmits<{
  submit: [question: string]
  abort: []
  clear: []
}>()

const question = ref('')

const canSubmit = computed(() => question.value.trim().length > 0 && !props.streaming)

function onSubmit() {
  if (!canSubmit.value) return
  emit('submit', question.value)
}

function onClear() {
  question.value = ''
  emit('clear')
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