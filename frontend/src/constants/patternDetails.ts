// frontend/src/constants/patternDetails.ts

/**
 * PatternDetail - detailed information about each agent pattern.
 * Used by PatternDescriptionCard and ExampleQuestions components.
 */
export interface PatternDetail {
  /** Core idea explanation of the pattern */
  coreIdea: string
  /** Typical scenarios where this pattern applies */
  scenarios: string
  /** Example questions users can click to try */
  examples: string[]
}

/**
 * PATTERN_DETAILS - single source of truth for pattern details.
 * CoT and ReAct are fully populated; other patterns have placeholders for Phase 6+.
 */
export const PATTERN_DETAILS: Record<string, PatternDetail> = {
  cot: {
    coreIdea: '思维链（Chain of Thought）是一种让模型"一步步思考"的推理模式。通过显式展示推理过程，提高复杂问题的准确率。',
    scenarios: '数学证明、逻辑推理、多步计算、需要中间步骤的问题。',
    examples: [
      '证明 sqrt(2) 是无理数',
      '狼鸡菜过河问题如何解决？',
      '地球到月球的距离如何估算？',
      '为什么天空是蓝色的？'
    ]
  },
  react: {
    coreIdea: 'ReAct (Reasoning + Acting) 是一种结合推理与行动的 Agent 模式。'
      + '模型在思考过程中可以调用工具获取外部信息，基于工具返回的结果继续推理，'
      + '形成 Thought -> Action -> Observation 循环，直到得出最终答案。',
    scenarios: '需要外部信息的问题 (如天气查询、实时数据)、多步工具调用任务、'
      + '需要从环境中获取反馈的推理任务。',
    examples: [
      '北京现在天气怎么样？适合户外跑步吗？',
      '计算 (3.14 * 2.5) + 18.7 的结果',
      '阿斯加德的民间传说 | 查一下今天是几号 | 两个事件相隔多久？',
      '如果今天是 2026-08-11，距离 2027 年元旦还有多少天？'
    ]
  },
  selfAsk: {
    coreIdea: 'Self-Ask (自问自答) 是一种将复杂问题分解为多个子问题的 Agent 模式。'
      + '模型先提出需要回答的子问题，逐一回答每个子问题，'
      + '最后综合所有子问题的答案得出最终答案。'
      + '每个子问题就像知识链中的一个环节，环环相扣。',
    scenarios: '需要多跳推理的复杂问题 (如"蒙娜丽莎的画家还画过什么著名作品？")、'
      + '需要组合多个知识片段的问题、涉及多步事实查询的问题。',
    examples: [
      '蒙娜丽莎的画家还画过什么著名作品？',
      '北京和上海哪个城市离深圳更近？距离分别是多少？',
      '爱因斯坦是哪一年获得诺贝尔奖的？他当时多大年纪？',
      '长江流经哪些省份？其中哪个省份人口最多？'
    ]
  },
  planExecute: {
    coreIdea: '',
    scenarios: '',
    examples: []
  },
  tot: {
    coreIdea: '',
    scenarios: '',
    examples: []
  },
  reflexion: {
    coreIdea: '',
    scenarios: '',
    examples: []
  },
  roleplay: {
    coreIdea: '',
    scenarios: '',
    examples: []
  }
}