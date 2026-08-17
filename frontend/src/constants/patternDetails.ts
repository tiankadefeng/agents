// frontend/src/constants/patternDetails.ts

/**
 * PatternDetail - detailed information about each agent pattern.
 * Used by PatternDescriptionCard.
 */
export interface PatternDetail {
  /** Core idea explanation of the pattern */
  coreIdea: string
  /** Typical scenarios where this pattern applies */
  scenarios: string
}

/**
 * PATTERN_DETAILS - single source of truth for pattern details.
 * CoT and ReAct are fully populated; other patterns have placeholders for Phase 6+.
 */
export const PATTERN_DETAILS: Record<string, PatternDetail> = {
  cot: {
    coreIdea: '思维链（Chain of Thought）是一种让模型"一步步思考"的推理模式。通过显式展示推理过程，提高复杂问题的准确率。',
    scenarios: '数学证明、逻辑推理、多步计算、需要中间步骤的问题。',
  },
  react: {
    coreIdea: 'ReAct (Reasoning + Acting) 是一种结合推理与行动的 Agent 模式。'
      + '模型在思考过程中可以调用工具获取外部信息，基于工具返回的结果继续推理，'
      + '形成 Thought -> Action -> Observation 循环，直到得出最终答案。',
    scenarios: '需要外部信息的问题 (如天气查询、实时数据)、多步工具调用任务、'
      + '需要从环境中获取反馈的推理任务。',
  },
  selfAsk: {
    coreIdea: 'Self-Ask (自问自答) 是一种将复杂问题分解为多个子问题的 Agent 模式。'
      + '模型先提出需要回答的子问题，逐一回答每个子问题，'
      + '最后综合所有子问题的答案得出最终答案。'
      + '每个子问题就像知识链中的一个环节，环环相扣。',
    scenarios: '需要多跳推理的复杂问题 (如"蒙娜丽莎的画家还画过什么著名作品？")、'
      + '需要组合多个知识片段的问题、涉及多步事实查询的问题。',
  },
  planExecute: {
    coreIdea: 'Plan-and-Execute (计划与执行) 是一种将任务分解为"先规划、后执行"两阶段的 Agent 模式。'
      + 'Planner 首先生成结构化计划（步骤列表），Executor 按顺序逐步执行每个步骤，每步可调用工具。'
      + '步骤失败时自动重新规划剩余步骤，体现"计划会动态调整"的适应性。',
    scenarios: '需要多步骤规划的任务（如旅行计划、商业决策分析）、'
      + '需要分步执行且每步可独立完成的任务、需要动态调整计划的场景。',
  },
  tot: {
    coreIdea: '',
    scenarios: '',
  },
  reflexion: {
    coreIdea: '',
    scenarios: '',
  },
  roleplay: {
    coreIdea: '',
    scenarios: '',
  },
}