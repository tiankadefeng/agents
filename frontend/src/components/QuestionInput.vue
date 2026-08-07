<script setup lang="ts">
import { ref, computed } from 'vue'
import { Promotion, Delete, Close } from '@element-plus/icons-vue'

const props = defineProps<{
  streaming: boolean
  examples?: string[]
}>()

const emit = defineEmits<{
  submit: [question: string]
  abort: []
  clear: []
  selectExample: [question: string]
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

function handleSelectExample(example: string) {
  question.value = example
  emit('selectExample', example)
}
</script>

<template>
  <div class="question-input">
    <label class="input-label">问题</label>
    <div v-if="examples && examples.length > 0" class="example-questions">
      <el-button
        v-for="ex in examples"
        :key="ex"
        size="small"
        type="default"
        :disabled="streaming"
        @click="handleSelectExample(ex)"
      >
        {{ ex }}
      </el-button>
    </div>
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
  margin-bottom: 24px;
}

.input-label {
  display: block;
  font-size: 14px;
  color: #303133;
  margin-bottom: 8px;
}

.example-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.button-row {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>