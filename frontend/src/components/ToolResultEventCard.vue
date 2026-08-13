<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  toolName: string
  result: string
  isError: boolean
  ts: string
}>()

const localTime = computed(() => {
  const d = new Date(props.ts)
  return d.toLocaleTimeString()
})

const isDedup = computed(() => {
  return props.result === 'use previous result'
})
</script>

<template>
  <div :class="['tool-result-card', { error: isError, dedup: isDedup }]">
    <div class="card-header">
      <span class="card-title">工具结果</span>
      <span class="timestamp" :title="ts">{{ localTime }}</span>
    </div>
    <div class="card-body">
      <span :class="['tool-tag', { error: isError, dedup: isDedup }]">
        工具: {{ toolName }}
      </span>
      <div v-if="isError" class="error-label">调用失败</div>
      <div class="section-label">结果</div>
      <div :class="['result-text', { 'dedup-text': isDedup }]">
        {{ result }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.tool-result-card {
  background: #FFFFFF;
  border: 1px solid #E4E7ED;
  border-left: 3px solid #15AC0C;
  border-radius: 4px;
  padding: 12px 16px;
}

.tool-result-card.error {
  border-left: 3px solid #D70016;
}

.tool-result-card.dedup {
  border-left: 3px solid #909399;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #222;
}

.timestamp {
  font-size: 12px;
  color: #999;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-tag {
  font-size: 12px;
  color: #15AC0C;
  background: #E3F6E1;
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
  align-self: flex-start;
}

.tool-tag.error {
  color: #D70016;
  background: #FCE5E7;
}

.tool-tag.dedup {
  color: #909399;
  background: #F0F2F5;
}

.error-label {
  font-size: 12px;
  font-weight: 600;
  color: #D70016;
}

.section-label {
  font-size: 12px;
  color: #999;
}

.result-text {
  font-size: 14px;
  color: #555;
  line-height: 1.5;
}

.dedup-text {
  color: #909399;
  font-style: italic;
}
</style>