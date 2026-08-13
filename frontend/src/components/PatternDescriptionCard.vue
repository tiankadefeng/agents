<script setup lang="ts">
import { computed } from 'vue'
import { PATTERN_DETAILS } from '@/constants/patternDetails'

const props = defineProps<{
  patternId: string
}>()

const emit = defineEmits<{
  selectExample: [question: string]
}>()

const detail = computed(() => PATTERN_DETAILS[props.patternId])
</script>

<template>
  <div v-if="detail" class="pattern-description-card">
    <div class="card-title">{{ patternId === 'cot' ? 'CoT 思维链' : patternId === 'react' ? 'ReAct 推理+行动' : patternId }}</div>

    <div class="card-section">
      <div class="section-label">核心思想</div>
      <div class="section-content">{{ detail.coreIdea }}</div>
    </div>

    <div class="card-section">
      <div class="section-label">适用场景</div>
      <div class="section-content">{{ detail.scenarios }}</div>
    </div>
  </div>
</template>

<style scoped>
.pattern-description-card {
  background: var(--design-bg-white);
  border: 1px solid var(--design-border-color);
  border-radius: var(--design-radius-base);
  padding: 16px;
  margin-top: 0;
}

.card-title {
  font-size: var(--design-font-size-md);
  font-weight: var(--design-font-weight-bold);
  color: var(--design-text-primary);
  margin-bottom: 12px;
}

.card-section {
  margin-top: 12px;
}

.section-label {
  font-size: var(--design-font-size-xs);
  color: var(--design-text-secondary);
  margin-bottom: var(--design-spacing-xs);
}

.section-content {
  font-size: var(--design-font-size-base);
  color: var(--design-text-regular);
  line-height: var(--design-line-height-base);
}
</style>