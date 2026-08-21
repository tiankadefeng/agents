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
    coreIdea: 'Tree of Thoughts (树状思维) 是一种通过多分支探索和评估来寻找最优答案的 Agent 模式。'
      + '与思维链的线性推理不同，ToT 在每一步生成多个候选分支，'
      + '用 LLM 对每个分支评分，保留高分支继续探索、剪掉低分支，'
      + '最终从所有存活分支中选出最优路径。'
      + '整个过程像一棵不断生长、修剪的树--"多思考几条路，选最好的走"。',
    scenarios: '需要多方案探索的开放性问题（如"如何用 1,3,4,6 算出 24？"）、'
      + '规划类问题（如"最优周末出行方案"）、'
      + '需要权衡多个因素的决策问题（如"选择最佳投资方案"）、'
      + '解谜和搜索类问题（如"8 皇后问题"、"数独"）。',
  },
  reflexion: {
    coreIdea: 'Reflexion（反思迭代）是一种让模型通过"尝试->评估->反思->改进"循环来自我纠错的 Agent 模式。'
      + 'Generator 生成初始答案，Evaluator（LLM-as-judge）评估答案质量并给出分数和反馈，'
      + 'Reflector 根据反馈生成反思。下一轮 Generator 依据反思内容改进答案，'
      + '直到评估通过或达到最大轮次。'
      + '整个过程就像一个学习者不断检查自己的作业并改进——"犯错不可怕，关键是要从错误中学习"。',
    scenarios: '需要迭代改进的创作任务（如"写一段代码然后优化"、"撰写文案并改进"）、'
      + '需要多轮打磨的推理问题（如"证明一个数学定理"）、'
      + '需要自我纠错的复杂任务（如"设计一个算法并分析其复杂度"）、'
      + '从差到好逐步改进的开放性问题（如"为一款新产品撰写宣传文案"）。',
  },
  roleplay: {
    coreIdea: 'Role-playing（角色扮演）是一种让多个 AI 角色通过协作讨论来解决问题的 Agent 模式。'
      + '三个角色（PM/Dev/Tester）按固定顺序发言：PM 提出需求并持续追问、Dev 逐步实现、Tester 逐轮验证。'
      + '每角色有独立身份和专业视角，通过 5 轮对话不断澄清需求、改进方案、验证质量。'
      + '整个过程就像一个小型开发团队在开会--"不同角色从不同角度审视同一个问题"。',
    scenarios: '需要多角色协作讨论的问题（如"设计一个用户登录功能"、"制定一个 API 接口规范"）、'
      + '需要需求/实现/验证多视角审视的任务（如"设计一个电商系统的架构"）、'
      + '需要多方权衡的决策问题（如"讨论一个高并发订单系统的设计方案"）、'
      + '教学演示团队协作流程的开放性问题（如"为一款新 App 制定完整开发计划"）。',
  },
}