<script setup lang="ts">
import { computed } from 'vue'
import { PATTERN_DETAILS } from '@/constants/patternDetails'

const props = defineProps<{
  patternId: string
  onSelectExample?: (question: string) => void
}>()

const emit = defineEmits<{
  selectExample: [question: string]
}>()

const detail = computed(() => PATTERN_DETAILS[props.patternId])

function handleExampleClick(question: string) {
  if (props.onSelectExample) {
    props.onSelectExample(question)
  } else {
    emit('selectExample', question)
  }
}
</script>

<template>
  <div v-if="detail" class="pattern-description-card">
    <div class="card-title">{{ patternId === 'cot' ? 'CoT 思维链' : patternId }}</div>

    <div class="card-section">
      <div class="section-label">核心思想</div>
      <div class="section-content">{{ detail.coreIdea }}</div>
    </div>

    <div class="card-section">
      <div class="section-label">适用场景</div>
      <div class="section-content">{{ detail.scenarios }}</div>
    </div>

    <div v-if="detail.examples.length > 0" class="card-section">
      <div class="section-label">示例问题</div>
      <div class="example-buttons">
        <el-button
          v-for="(example, index) in detail.examples"
          :key="index"
          size="small"
          type="default"
          @click="handleExampleClick(example)"
        >
          {{ example }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pattern-description-card {
  background: #F5F7FA;
  border: 1px solid #E4E7ED;
  border-radius: 4px;
  padding: 16px;
  margin-top: 16px;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.card-section {
  margin-top: 12px;
}

.section-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.section-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.5;
}

.example-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>