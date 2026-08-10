<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { PatternInfo } from '@/types/agent'
import PatternDescriptionCard from './PatternDescriptionCard.vue'

const props = withDefaults(
  defineProps<{
    selected?: string
    disabled?: boolean
  }>(),
  {
    selected: 'cot',
    disabled: false,
  }
)

const emit = defineEmits<{
  select: [patternId: string]
}>()

const patterns = ref<PatternInfo[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

// Fallback data if API fails
const HARDCODED_PATTERNS_FALLBACK: PatternInfo[] = [
  { id: 'cot', displayName: 'CoT 思维链', description: '一步步写推理过程' },
  { id: 'react', displayName: 'ReAct 推理+行动', description: '结合推理与工具调用' },
  { id: 'selfAsk', displayName: 'Self-Ask 自问自答', description: '拆大问题为小问题' },
  { id: 'planExecute', displayName: 'Plan-and-Execute 计划与执行', description: '先规划再逐步执行' },
  { id: 'tot', displayName: 'Tree of Thoughts 树状思维', description: '多分支探索择优' },
  { id: 'reflexion', displayName: 'Reflexion 反思迭代', description: '犯错后自我纠错' },
  { id: 'roleplay', displayName: 'Role-playing 角色扮演', description: '多智能体分工协作' },
]

// Phase online mapping for disabled patterns
const PHASE_ONLINE_MAP: Record<string, number> = {
  react: 5,
  selfAsk: 6,
  planExecute: 7,
  tot: 8,
  reflexion: 9,
  roleplay: 10,
}

function isPatternEnabled(patternId: string): boolean {
  return patternId === 'cot'
}

function handlePatternClick(patternId: string) {
  if (!isPatternEnabled(patternId) || props.disabled) {
    return
  }
  emit('select', patternId)
}

onMounted(async () => {
  try {
    const resp = await fetch('/api/patterns')
    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status}`)
    }
    patterns.value = await resp.json()
  } catch (e) {
    error.value = '无法获取模式列表，请刷新页面或检查后端服务。'
    patterns.value = HARDCODED_PATTERNS_FALLBACK
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="pattern-selector">
    <div class="selector-title">Agent 设计模式</div>
    <div class="selector-subtitle">选择一种模式开始学习</div>

    <div v-if="loading" class="loading-state">加载中...</div>

    <div v-else class="pattern-list">
      <div
        v-for="p in patterns"
        :key="p.id"
        :class="[
          'pattern-item',
          {
            enabled: isPatternEnabled(p.id) && !disabled,
            disabled: !isPatternEnabled(p.id) || disabled,
            selected: p.id === selected,
          },
        ]"
        :aria-disabled="!isPatternEnabled(p.id) || disabled"
        :aria-label="`${p.displayName}${isPatternEnabled(p.id) ? '' : `，Phase ${PHASE_ONLINE_MAP[p.id]} 上线，当前不可用`}`"
        @click="handlePatternClick(p.id)"
      >
        <div class="pattern-header">
          <div class="pattern-name">{{ p.displayName }}</div>
          <el-icon v-if="p.id === selected" class="selected-icon">
            <Check />
          </el-icon>
        </div>
        <div
          :class="[
            'pattern-tag',
            isPatternEnabled(p.id) ? 'green' : 'amber',
          ]"
        >
          {{ isPatternEnabled(p.id) ? '已上线' : `Phase ${PHASE_ONLINE_MAP[p.id]} 上线` }}
        </div>
      </div>
    </div>

    <PatternDescriptionCard
      v-if="selected"
      :pattern-id="selected"
      class="pattern-description-card"
    />
  </div>
</template>

<style scoped>
.pattern-selector {
  color: var(--design-text-primary);
}

.selector-title {
  font-size: var(--design-font-size-lg);
  font-weight: var(--design-font-weight-bold);
  color: var(--design-text-primary);
}

.selector-subtitle {
  font-size: var(--design-font-size-xs);
  color: var(--design-text-secondary);
  margin-top: var(--design-spacing-xs);
  margin-bottom: var(--design-spacing-md);
}

.loading-state {
  font-size: var(--design-font-size-base);
  color: var(--design-text-secondary);
  text-align: center;
  padding: 24px 0;
}

.pattern-list {
  display: flex;
  flex-direction: column;
  gap: var(--design-spacing-sm);
}

.pattern-item {
  padding: 12px 16px;
  border: 1px solid var(--design-border-color);
  border-radius: var(--design-radius-base);
  background: var(--design-bg-white);
  transition: all 0.2s;
}

.pattern-item.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.pattern-item.disabled:hover {
  background: var(--design-bg-white);
}

.pattern-item.enabled {
  cursor: pointer;
}

.pattern-item.enabled:hover {
  background: var(--design-color-primary-light-9);
  border-color: var(--design-color-primary);
}

.pattern-item.selected {
  background: var(--design-color-primary);
  border-color: var(--design-color-primary);
}

.pattern-item.selected .pattern-name,
.pattern-item.selected .pattern-tag {
  color: var(--design-bg-white);
}

.pattern-item.selected .pattern-tag.green {
  background: rgba(255, 255, 255, 0.2);
  color: var(--design-bg-white);
}

.pattern-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pattern-name {
  font-size: var(--design-font-size-base);
  color: var(--design-text-primary);
}

.selected-icon {
  color: var(--design-bg-white);
}

.pattern-tag {
  font-size: var(--design-font-size-xs);
  margin-top: var(--design-spacing-xs);
  padding: 2px 6px;
  border-radius: var(--design-radius-sm);
  display: inline-block;
}

.pattern-tag.green {
  color: var(--design-color-success);
  background: rgba(21, 172, 12, 0.1);
}

.pattern-tag.amber {
  color: var(--design-color-warning);
  background: rgba(250, 178, 21, 0.1);
}

.pattern-description-card {
  margin-top: var(--design-spacing-md);
}
</style>