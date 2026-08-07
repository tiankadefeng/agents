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
  color: #303133;
}

.selector-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.selector-subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  margin-bottom: 16px;
}

.loading-state {
  font-size: 14px;
  color: #909399;
  text-align: center;
  padding: 24px 0;
}

.pattern-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pattern-item {
  padding: 8px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
  transition: all 0.2s;
}

.pattern-item.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.pattern-item.disabled:hover {
  background: #fff;
}

.pattern-item.enabled {
  cursor: pointer;
}

.pattern-item.enabled:hover {
  background: #ecf5ff;
  border-color: #409eff;
}

.pattern-item.selected {
  background: #409eff;
  border-color: #409eff;
}

.pattern-item.selected .pattern-name,
.pattern-item.selected .pattern-tag {
  color: #fff;
}

.pattern-item.selected .pattern-tag.green {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.pattern-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pattern-name {
  font-size: 14px;
  color: #303133;
}

.selected-icon {
  color: #fff;
}

.pattern-tag {
  font-size: 12px;
  margin-top: 4px;
  padding: 2px 6px;
  border-radius: 3px;
  display: inline-block;
}

.pattern-tag.green {
  color: #67c23a;
  background: rgba(103, 194, 58, 0.1);
}

.pattern-tag.amber {
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.1);
}

.pattern-description-card {
  margin-top: 16px;
}
</style>