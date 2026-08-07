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
 * CoT is fully populated; other patterns have placeholders for Phase 5+.
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
    coreIdea: '',
    scenarios: '',
    examples: []
  },
  selfAsk: {
    coreIdea: '',
    scenarios: '',
    examples: []
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