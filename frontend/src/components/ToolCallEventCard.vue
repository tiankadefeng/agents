<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  toolName: string
  arguments: Record<string, unknown>
  ts: string
}>()

const formattedArgs = computed(() => {
  if (!props.arguments || Object.keys(props.arguments).length === 0) return null
  return JSON.stringify(props.arguments, null, 2)
})

const localTime = computed(() => {
  const d = new Date(props.ts)
  return d.toLocaleTimeString()
})
</script>

<template>
  <div class="tool-call-card">
    <div class="card-header">
      <span class="card-title">工具调用</span>
      <span class="timestamp" :title="ts">{{ localTime }}</span>
    </div>
    <div class="card-body">
      <span class="tool-tag">工具: {{ toolName }}</span>
      <div v-if="formattedArgs" class="args-section">
        <div class="section-label">参数</div>
        <pre class="args-json">{{ formattedArgs }}</pre>
      </div>
      <div v-else class="no-args">(无参数)</div>
    </div>
  </div>
</template>

<style scoped>
.tool-call-card {
  background: #FFFFFF;
  border: 1px solid #E4E7ED;
  border-left: 3px solid #1D70F5;
  border-radius: 4px;
  padding: 12px 16px;
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
  color: #1D70F5;
  background: #E3EEFF;
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
  align-self: flex-start;
}

.section-label {
  font-size: 12px;
  color: #999;
}

.args-json {
  font-size: 12px;
  font-family: monospace;
  color: #555;
  white-space: pre;
  margin: 0;
  background: #F5F7FA;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}

.no-args {
  font-size: 12px;
  color: #999;
  font-style: italic;
}
</style>