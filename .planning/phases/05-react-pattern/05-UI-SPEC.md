---
phase: 5
slug: react-pattern
status: draft
shadcn_initialized: false
preset: none
created: 2026-08-11
---

# Phase 5 - UI Design Contract

> Phase 5: ReAct Pattern - ReAct (推理+行动) 模式手动工具调用循环，Thought/Action/Observation 垂直时间线分块展示，ToolCallEventCard/ToolResultEventCard 组件。
> 视觉与交互契约，由 gsd-ui-researcher 生成，gsd-ui-checker 验证，gsd-planner / gsd-executor 消费。

---

## Phase 5 视觉基线声明

**Phase 5 继承 Phase 1/2/3/4 完整视觉基线，新增 2 个 UI 组件、改造 2 个现有组件、更新 1 个 composable、更新 1 个常量文件。**

| Phase 1/2/3/4 章节 | Phase 5 状态 |
|---------------------|--------------|
| Design System (Element Plus 2.14.3 + @element-plus/icons-vue 2.3.2 + 中文字体栈) | 沿用 - 不改 |
| Spacing Scale (4/8/16/24/32/48/64) | 沿用 - 不改 |
| Typography (14/12/18/24 px, 400/600 weight) | 沿用 - 不改 |
| Color (60/30/10 配色 + amber/blue 视觉分离 + 新增 tool-call 配色) | 沿用 - 新增 tool-call 卡片配色契约 |
| Layout Dimensions (el-header 56px / el-aside 240px / el-main 960px) | 沿用 - 不改 |
| Streaming Text Visual Treatment (4 态状态机 + shallowRef 累积) | 沿用 - 不改 |
| reasoning_content Collapse Panel | 改造 - 新增垂直时间线分块渲染 |
| Error Event Display | 沿用 - 不改 |
| Accessibility | 沿用 - 不改 |
| Registry Safety | 沿用 - 不改 |

**Phase 5 工作范围 (UI-SPEC 关心部分):**

1. **ToolCallEventCard.vue 新建** - 展示工具名称、参数 JSON (格式化)、调用时间戳
2. **ToolResultEventCard.vue 新建** - 展示工具结果文本、错误状态 (isError 时红色边框)
3. **ReasoningPanel.vue 改造** - 支持垂直时间线分块 (Thought/Action/Observation 交替)
4. **useSSEStream.ts 改造** - 新增 ToolCallEvent / ToolResultEvent 路由分支
5. **App.vue 改造** - 新增 onToolCall / onToolResult 回调处理
6. **patternDetails.ts 更新** - 填充 react 模式的 coreIdea / scenarios / examples
7. **PatternSelector.vue 更新** - react 模式启用 (Phase 5 上线)

---

## Design System

| Property | Value |
|----------|-------|
| Tool | Element Plus (沿用 Phase 1) |
| Preset | not applicable (沿用 Phase 1) |
| Component library | Element Plus 2.14.3 (沿用 Phase 1) |
| Icon library | @element-plus/icons-vue 2.3.2 (沿用 Phase 1) |
| Font | 沿用 Phase 1 默认字体栈 ("Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", Arial, sans-serif) |
| CSS approach | 沿用 Phase 1 (Element Plus CSS 变量 + `<style scoped>` 覆盖) |

**Phase 5 不新增 npm 依赖、不新增图标库。** 新增 2 个组件复用现有 Element Plus 组件。

---

## Spacing Scale

**沿用 Phase 1/2/3/4。** 详细参见 `.planning/phases/01-skeleton/01-UI-SPEC.md` §Spacing Scale。

Phase 5 新增组件使用现有 token, 不引入新间距值。

---

## Typography

**沿用 Phase 1/2/3/4。** 详细参见 `.planning/phases/01-skeleton/01-UI-SPEC.md` §Typography。

Phase 5 新增组件使用现有字号/字重规格:

| 组件 | 字号/字重用途 |
|------|--------------|
| ToolCallEventCard | 工具名 14px semibold, 参数 JSON 12px regular, 时间戳 12px regular |
| ToolResultEventCard | 工具名 14px semibold, 结果正文 14px regular, 错误状态 12px danger |

---

## Color

**沿用 Phase 1/2/3/4 全量配色。** 详细参见 `.planning/phases/01-skeleton/01-UI-SPEC.md` §Color。

Phase 5 新增 tool-call 相关配色:

| Role | Value | Usage |
|------|-------|-------|
| Tool call left border | `#1D70F5` (design-color-primary) | ToolCallEventCard 左侧强调边 |
| Tool result left border | `#15AC0C` (design-color-success) | ToolResultEventCard 左侧强调边 (成功) |
| Tool result error left border | `#D70016` (design-color-danger) | ToolResultEventCard 错误态左侧强调边 |
| Tool call card bg | `#FFFFFF` (design-bg-white) | 卡片背景 |
| Tool result card bg | `#FFFFFF` (design-bg-white) | 卡片背景 |
| Tool call tag bg | `#E3EEFF` (design-color-primary-light-9) | 工具名标签背景 |
| Tool result tag bg | `#E3F6E1` (design-color-success-light-9) | 工具结果标签背景 |
| Tool result error tag bg | `#FCE5E7` (design-color-danger-light-9) | 工具错误标签背景 |
| Timeline dot - thought | `#E6A23C` (amber/warning) | 推理过程时间线圆点 |
| Timeline dot - action | `#1D70F5` (primary blue) | 工具调用时间线圆点 |
| Timeline dot - observation | `#15AC0C` (success green) | 工具结果时间线圆点 |
| Timeline line | `#E4E7ED` (design-border-color) | 垂直时间线连接线 |

**Phase 5 不修改 60/30/10 配色结构。** Tool-call 卡片使用独立的左边界颜色语义 (blue=action, green=result, red=error), 与 amber=thinking/blue=final 的视觉分离契约不冲突。

---

## Copywriting Contract

**沿用 Phase 1/2/3/4 全量中文文案。** Phase 5 新增以下文案:

### useSSEStream 新增回调文案 (无用户可见文案, 仅事件路由)

### App.vue 新增回调文案 (无用户可见文案, 仅事件路由)

### ToolCallEventCard 文案

| Element | Copy |
|---------|------|
| 卡片标题 | 工具调用 |
| 工具名标签 | 工具: {toolName} |
| 参数标签 | 参数 |
| 时间戳标签 | 时间 |
| 空参数 | (无参数) |

### ToolResultEventCard 文案

| Element | Copy |
|---------|------|
| 卡片标题 | 工具结果 |
| 工具名标签 | 工具: {toolName} |
| 结果标签 | 结果 |
| 错误状态标签 | 调用失败 |
| 重复调用提示 | 相同参数已调用过, 复用上次结果 |

### ReasoningPanel 垂直时间线文案

| Element | Copy |
|---------|------|
| Thought 阶段标签 | 思考 (Thought) |
| Action 阶段标签 | 行动 (Action) |
| Observation 阶段标签 | 观察 (Observation) |
| 垂直时间线空状态 | 暂无推理过程。提交问题后, 这里会流式显示 ReAct 模式的 Thought/Action/Observation 循环。 |

### Pattern Details - react 模式

| Element | Copy |
|---------|------|
| coreIdea | ReAct (Reasoning + Acting) 是一种结合推理与行动的 Agent 模式。模型在思考过程中可以调用工具获取外部信息, 基于工具返回的结果继续推理, 形成 Thought -> Action -> Observation 循环, 直到得出最终答案。 |
| scenarios | 需要外部信息的问题 (如天气查询、实时数据)、多步工具调用任务、需要从环境中获取反馈的推理任务。 |
| examples | 1. 北京现在天气怎么样? 适合户外跑步吗? |
|  | 2. 计算 (3.14 * 2.5) + 18.7 的结果 |
|  | 3. "阿斯加德"的民间传说 | 查一下今天是几号 | 两个事件相隔多久? |
|  | 4. 如果今天是 2026-08-11, 距离 2027 年元旦还有多少天? |

### PatternSelector 文案

| Element | Copy |
|---------|------|
| react 模式已上线标签 | 已上线 (绿色, 替代 Phase 1-4 的 "Phase 5 上线") |
| 其余 5 个模式 (selfAsk/planExecute/tot/reflexion/roleplay) | 保持 disabled, 显示 "Phase {N} 上线" (amber) |

### 错误状态文案 (新增)

| Element | Copy |
|---------|------|
| 工具调用失败 - 工具不存在 | 工具 "{toolName}" 不存在。请检查工具名或联系开发者。 |
| 工具调用失败 - 执行异常 | 工具 "{toolName}" 执行失败: {错误信息}。该错误已返回给模型, 由模型决定后续动作。 |
| ReAct 循环超限 | ReAct 循环已达最大迭代次数 10, 请简化问题后重试。 |
| ReAct 模型不支持 tool calling | 当前模型不支持工具调用, 请检查 deepseek-reasoner 配置。 |

**文案语气规则沿用 Phase 1:**
- 错误文案必须包含: (1) 问题诊断 + (2) 下一步动作建议
- 流式状态用进行时 ("正在..."), 完成状态用"已..."
- 第二人称"你"不使用 - 用"这里会..."或被动语态

---

## ToolCallEventCard Component (UI-11 ReAct 部分)

**新建文件:** `frontend/src/components/ToolCallEventCard.vue`

**位置:** ReasoningPanel 内部, 垂直时间线中 Action 阶段

### 组件结构

```
┌──────────────────────────────────────────────────────┐
│ ┃ 工具调用                          2026-08-11T12:30 │  ← 3px blue left border
│ ┃                                                    │
│ ┃ [工具: weather]  (primary-light-9 tag)            │  ← 12px tag
│ ┃                                                    │
│ ┃ 参数                                               │  ← 12px label
│ ┃ {                                                  │
│ ┃   "city": "北京"                                   │  ← 12px code (JSON formatted)
│ ┃ }                                                  │
└──────────────────────────────────────────────────────┘
```

### 组件 Props

```typescript
interface ToolCallEventCardProps {
  toolName: string
  arguments: Record<string, unknown>
  ts: string  // ISO-8601 timestamp
}
```

### 视觉规格

| 属性 | 值 | 说明 |
|------|-----|------|
| 容器背景 | `#FFFFFF` (design-bg-white) | 白色卡片 |
| 容器边框 | `1px solid #E4E7ED` (design-border-color) | 灰色边框 |
| 容器圆角 | `4px` (design-radius-base) | 标准圆角 |
| 容器 padding | `12px 16px` | 紧凑内边距 |
| 左边框 | `3px solid #1D70F5` (design-color-primary) | 蓝色强调 |
| 标题 | `14px semibold #222222` | 工具调用 |
| 时间戳 | `12px regular #999999` | 右侧对齐 |
| 工具名标签 | `12px regular #1D70F5` 背景 `#E3EEFF` | 圆角 tag |
| 参数标签 | `12px regular #999999` | 辅助文字 |
| 参数内容 | `12px regular #555555` monospace-family | JSON 格式化 |
| 无参数提示 | `12px regular #999999` | 斜体 "无参数" |
| 卡片间距 (上下) | `8px` (sm) | 与相邻卡片间距 |
| 卡片间距 (左右) | `0` | 与时间线对齐 |

### 时间格式化

- 显示 ISO-8601 时间戳的本地时间部分 (HH:mm:ss)
- 完整 ISO-8601 字符串作为 `title` 属性 (hover 时 tooltip 显示)

---

## ToolResultEventCard Component (UI-11 ReAct 部分)

**新建文件:** `frontend/src/components/ToolResultEventCard.vue`

**位置:** ReasoningPanel 内部, 垂直时间线中 Observation 阶段

### 组件结构 (成功态)

```
┌──────────────────────────────────────────────────────┐
│ ┃ 工具结果                          2026-08-11T12:30 │  ← 3px green left border
│ ┃                                                    │
│ ┃ [工具: weather]  (success-light-9 tag)            │  ← 12px tag
│ ┃                                                    │
│ ┃ 结果                                               │  ← 12px label
│ ┃ 北京: 晴, 25度, 适合户外跑步                        │  ← 14px body
└──────────────────────────────────────────────────────┘
```

### 组件结构 (错误态)

```
┌──────────────────────────────────────────────────────┐
│ ┃ 工具结果                          2026-08-11T12:30 │  ← 3px red left border
│ ┃                                                    │
│ ┃ [工具: weather]  (danger-light-9 tag)             │  ← 12px tag
│ ┃                                                    │
│ ┃ ⚠ 调用失败                                          │  ← 12px danger label
│ ┃ 工具执行出错: API 连接超时                           │  ← 14px body
└──────────────────────────────────────────────────────┘
```

### 组件结构 (重复调用态)

```
┌──────────────────────────────────────────────────────┐
│ ┃ 工具结果                          2026-08-11T12:30 │  ← 3px gray left border
│ ┃                                                    │
│ ┃ [工具: weather]  (info-light tag)                 │  ← 12px tag
│ ┃                                                    │
│ ┃ 结果                                               │  ← 12px label
│ ┃ use previous result (相同参数已调用过, 复用上次结果) │  ← 14px body, italic
└──────────────────────────────────────────────────────┘
```

### 组件 Props

```typescript
interface ToolResultEventCardProps {
  toolName: string
  result: string
  isError: boolean
  ts: string  // ISO-8601 timestamp
}
```

### 视觉规格

| 属性 | 值 | 说明 |
|------|-----|------|
| 容器背景 | `#FFFFFF` (design-bg-white) | 白色卡片 |
| 容器边框 | `1px solid #E4E7ED` | 灰色边框 |
| 容器圆角 | `4px` | 标准圆角 |
| 容器 padding | `12px 16px` | 紧凑内边距 |
| 左边框 (成功) | `3px solid #15AC0C` (success) | 绿色强调 |
| 左边框 (错误) | `3px solid #D70016` (danger) | 红色强调 |
| 左边框 (重复) | `3px solid #909399` (info) | 灰色强调 |
| 标题 | `14px semibold #222222` | 工具结果 |
| 时间戳 | `12px regular #999999` | 右侧对齐 |
| 工具名标签 (成功) | `12px regular #15AC0C` 背景 `#E3F6E1` | green tag |
| 工具名标签 (错误) | `12px regular #D70016` 背景 `#FCE5E7` | red tag |
| 工具名标签 (重复) | `12px regular #909399` 背景 `#F0F2F5` | gray tag |
| 结果标签 | `12px regular #999999` | 辅助文字 |
| 结果正文 | `14px regular #555555` | 正文 |
| 错误标签 | `12px semibold #D70016` | 错误标识 |
| 重复调用提示 | `14px regular #909399 italic` | 斜体灰色 |
| 卡片间距 | `8px` (sm) | 与相邻卡片间距 |

---

## ReasoningPanel Refactor - 垂直时间线分块 (D-09)

**改造文件:** `frontend/src/components/ReasoningPanel.vue`

### 改造目标

从单一 `reasoningText: string` 渲染改为事件列表分块渲染, 支持 Thought/Action/Observation 交替的垂直时间线。

### 新增 Props

```typescript
interface ReasoningPanelProps {
  reasoningText: string        // 继承 - 纯文本回退
  status: StreamStatus         // 继承
  events?: AgentEvent[]        // 新增 - 完整事件列表, 用于分块渲染
}
```

### 分块渲染逻辑

当 `events` prop 存在时, 按以下规则渲染:

1. 遍历 events 数组
2. `ReasoningEvent` -> 渲染为 Thought 块 (amber 时间线圆点)
3. `ToolCallEvent` -> 渲染为 Action 块, 内嵌 `ToolCallEventCard` (blue 时间线圆点)
4. `ToolResultEvent` -> 渲染为 Observation 块, 内嵌 `ToolResultEventCard` (green 时间线圆点)
5. `FinalAnswerEvent` -> 跳过 (由 FinalAnswer 组件处理)
6. `ErrorEvent` -> 跳过 (由 el-alert 处理)
7. 其他事件类型 -> 静默忽略

当 `events` prop 不存在时, 回退到现有 `reasoningText` 纯文本渲染 (向后兼容 CoT 模式)。

### 垂直时间线视觉结构

```
ReAct 模式渲染:

┌─ ReasoningPanel ────────────────────────────────────┐
│ [▼] 推理过程     点击收起              ● 正在思考...  │
│                                                      │
│  ● (amber) ─── 思考 (Thought) ───────────────────    │
│  │ 模型开始思考: 用户想知道北京的天气...              │
│  │ 我需要调用天气工具来获取信息。                    │
│  │                                                    │
│  │ ┌─ ToolCallEventCard ──────────────────────────┐  │
│  │ │ ┃ 工具调用                        12:30:01   │  │
│  │ │ ┃ [工具: weather]                            │  │
│  │ │ ┃ 参数: {"city":"北京"}                      │  │
│  │ │ └────────────────────────────────────────────┘  │
│  │                                                    │
│  │  ● (blue) ─── 行动 (Action) ─────────────────    │
│  │                                                    │
│  │ ┌─ ToolResultEventCard ────────────────────────┐  │
│  │ │ ┃ 工具结果                        12:30:02   │  │
│  │ │ ┃ [工具: weather]                            │  │
│  │ │ ┃ 结果: 北京: 晴, 25度, 适合户外跑步          │  │
│  │ │ └────────────────────────────────────────────┘  │
│  │                                                    │
│  │  ● (green) ─── 观察 (Observation) ────────────    │
│  │                                                    │
│  │  ● (amber) ─── 思考 (Thought) ─────────────────  │
│  │  | 工具返回了北京天气信息。现在我可以回答用户...   │
│  └────────────────────────────────────────────────────┘
```

### 折叠面板逻辑 (沿用 Phase 1 D-03)

| 场景 | 行为 |
|------|------|
| 初始状态 | 折叠面板默认收起 (collapsed) |
| 流式进行中 | 面板保持当前展开/收起状态 (不自动展开) |
| 用户点击展开 | 展开面板, 显示垂直时间线 |
| 流式完成 | 面板保持当前状态 |
| 切换模式 | 保持 Phase 3 D-04 行为: 仅 abort, 不清空 |

### 时间线 CSS 规格

| 属性 | 值 | 说明 |
|------|-----|------|
| 时间线垂直线 | `2px solid #E4E7ED` | 灰色连接线 |
| 时间线圆点直径 | `12px` | 三个阶段共用尺寸 |
| 圆点 - Thought | `#E6A23C` (amber) | 思考阶段 |
| 圆点 - Action | `#1D70F5` (blue) | 行动阶段 |
| 圆点 - Observation | `#15AC0C` (green) | 观察阶段 |
| 圆点内圈 | `4px` white dot | 圆点中心留白 |
| 阶段标签 | `12px semibold` | "思考 (Thought)" 等 |
| 阶段标签颜色 | `#555555` | 与阶段圆点颜色一致 |
| 阶段内容区域 | `14px regular #555555` | 流式文本 |
| 阶段内容行距 | `1.5` | 标准正文行距 |
| 阶段间距 | `16px` (md) | 各阶段之间垂直间距 |
| 工具卡片与阶段内容间距 | `8px` (sm) | 卡片与文本间距 |
| 时间线左侧偏移 | `24px` | 圆点 + 连接线占位 |
| 内容左侧偏移 | `24px` | 对齐圆点右侧 |

---

## useSSEStream Refactor - 新增 ToolCallEvent/ToolResultEvent 路由

**改造文件:** `frontend/src/composables/useSSEStream.ts`

### SSEStreamOptions 扩展

```typescript
export interface SSEStreamOptions {
  onReasoning: (content: string, ev: ReasoningEvent) => void
  onFinal: (content: string, ev: FinalAnswerEvent) => void
  onError: (message: string, ev: ErrorEvent) => void
  // Phase 5 新增
  onToolCall?: (ev: ToolCallEvent) => void
  onToolResult?: (ev: ToolResultEvent) => void
  signal?: AbortSignal
}
```

### 路由逻辑新增 case

```typescript
switch (eventName as AgentEventName) {
  case 'ReasoningEvent':
    options.onReasoning(data.content, data)
    break
  case 'FinalAnswerEvent':
    options.onFinal(data.content, data)
    break
  case 'ErrorEvent':
    options.onError(data.message, data)
    break
  // Phase 5 新增
  case 'ToolCallEvent':
    options.onToolCall?.(data as ToolCallEvent)
    break
  case 'ToolResultEvent':
    options.onToolResult?.(data as ToolResultEvent)
    break
  default:
    // Silently ignore unmapped event names (forward compat)
    break
}
```

**回调签名约定:**
- `onToolCall` 和 `onToolResult` 接收完整 event 对象 (不同于 onReasoning/onFinal 的便捷字段 + ev 双参数)
- 理由: ToolCallEvent 和 ToolResultEvent 有多个等重要的字段 (toolName, arguments, result, isError), 没有单一"便捷字段"
- 回调可选 (optional chaining `?.`), 前向兼容

---

## App.vue Refactor - 新增 ToolCall/ToolResult 回调

**改造文件:** `frontend/src/App.vue`

### 新增 State

```typescript
// 新增: 事件列表, 用于 ReasoningPanel 分块渲染
const agentEvents = shallowRef<AgentEvent[]>([])
```

### submit() 扩展

```typescript
async function submit(question: string) {
  reasoningText.value = ''
  finalText.value = ''
  errorAlert.value = ''
  agentEvents.value = []  // 新增: 清空事件列表
  status.value = 'thinking'
  streamAborted = false
  abortController = new AbortController()

  let streamError = false

  try {
    const requestBody: AgentRequest = {
      patternId: selectedPatternId.value,
      question,
      options: {}
    }

    await startSSEStream(
      '/api/agent/execute',
      requestBody,
      {
        onReasoning: (content, ev) => {
          reasoningText.value += content
          agentEvents.value = [...agentEvents.value, ev]  // 新增: 追加事件
          status.value = 'thinking'
        },
        onToolCall: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]  // 新增: 追加工具调用事件
        },
        onToolResult: (ev) => {
          agentEvents.value = [...agentEvents.value, ev]  // 新增: 追加工具结果事件
        },
        onFinal: (content, ev) => {
          finalText.value += content
          status.value = 'answering'
        },
        onError: (message, ev) => {
          errorAlert.value = message
          status.value = 'error'
          streamError = true
        },
        signal: abortController.signal,
      }
    )

    if (!streamError && !streamAborted) {
      status.value = 'completed'
    }
  } catch (e) {
    if (!streamAborted) {
      errorAlert.value = (e as Error).message
      status.value = 'error'
    }
  }
}
```

### Template 更新

```vue
<ReasoningPanel
  :reasoning-text="reasoningText"
  :events="agentEvents"
  :status="status"
/>
```

### 事件列表性能约定

| 场景 | 行为 |
|------|------|
| 事件数量 | 每个 ReAct 轮次产生 3 个事件 (Thought + Action + Observation), 10 轮最多 30 个事件 |
| 性能风险 | 30 个事件低风险, 用 `[...agentEvents.value, ev]` 触发响应式更新即可 |
| 流式 text 累积 | ReasoningEvent.content 仍用 shallowRef 累积 (避免 5k+ 字符卡顿) |
| 事件列表不用 shallowRef | 事件数量少 (< 50), 普通 ref 即可 |

---

## PatternSelector Update - react 模式启用

**改造文件:** `frontend/src/components/PatternSelector.vue`

### 改动点

| 改动点 | 详情 |
|--------|------|
| `isPatternEnabled` 函数 | 新增 `react` 模式返回 true |
| react 模式标签 | 从 "Phase 5 上线" (amber) 改为 "已上线" (green) |
| 其余 5 个模式 | 保持 disabled, 标签不变 |
| PHASE_ONLINE_MAP | 无需修改 (react 已存在) |

### API 依赖

`GET /api/patterns` 返回的 PatternInfo 列表中, react 模式应包含:

```json
{
  "id": "react",
  "displayName": "ReAct 推理+行动",
  "description": "结合推理与工具调用"
}
```

---

## patternDetails.ts Update - react 模式填充

**改造文件:** `frontend/src/constants/patternDetails.ts`

### react 模式数据

```typescript
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
}
```

### PatternDescriptionCard 更新

PatternDescriptionCard.vue 的 `card-title` 需要更新为支持 react 模式:

```vue
<div class="card-title">{{ patternId === 'react' ? 'ReAct 推理+行动' : patternId === 'cot' ? 'CoT 思维链' : patternId }}</div>
```

---

## Interaction State Machine

### Phase 5 新增交互状态

#### ReasoningPanel 垂直时间线交互

| 场景 | 行为 |
|------|------|
| 折叠面板收起 | 显示 header 摘要: "推理过程 | 点击展开 | {status}" |
| 折叠面板展开 | 显示完整垂直时间线, 包含 Thought/Action/Observation 各阶段 |
| 流式进行中 | 当前活跃阶段 (如正在 Thinking) 显示 amber 脉冲; 时间线自动滚动到底部 |
| 流式完成 | 所有阶段完成, 时间线固定, 不再滚动 |
| 用户滚动 | 新事件到达时不强制滚动 (用户已手动滚动) |
| 切换模式 | 保持 D-04: 不清空, 仅 abort |

#### ToolCallEventCard 交互

| 场景 | 行为 |
|------|------|
| 正常显示 | 展示工具名标签 + 格式化 JSON 参数 |
| 参数 JSON 过长 (> 200 字符) | 默认折叠, 点击展开 |
| 无参数 (arguments 为空) | 显示 "(无参数)" 斜体提示 |
| 时间戳 | 显示本地时间 HH:mm:ss, hover 显示完整 ISO-8601 |

#### ToolResultEventCard 交互

| 场景 | 行为 |
|------|------|
| 成功 (isError=false) | 绿色左边框 + 绿色 tool 标签 + 正常结果文本 |
| 错误 (isError=true) | 红色左边框 + 红色 tool 标签 + "调用失败" 标签 + 错误信息 |
| 结果文本过长 (> 500 字符) | 默认折叠, 点击展开 |
| 重复调用 (result="use previous result") | 灰色左边框 + 灰色标签 + 斜体提示文字 |

---

## Phase 5 Scope Guardrails

**包含 (Phase 5 必须实现):**

- ToolCallEventCard.vue 新建 - 工具名标签 + 参数 JSON + 时间戳
- ToolResultEventCard.vue 新建 - 结果文本 + 错误态 + 去重提示
- ReasoningPanel.vue 改造 - 垂直时间线分块 (Thought/Action/Observation), 向后兼容 CoT
- useSSEStream.ts 改造 - 新增 onToolCall/onToolResult 回调分支
- App.vue 改造 - 新增 agentEvents state + 回调处理 + 传入 events prop
- PatternSelector.vue 更新 - react 模式启用
- patternDetails.ts 更新 - react 模式的 coreIdea/scenarios/examples
- react 示例问题 - 4 个示例 (D-03a 式)

**不包含 (明确推迟):**

- Self-Ask 模式 (Phase 6)
- Plan-and-Execute 模式 (Phase 7)
- Tree of Thoughts 模式 (Phase 8)
- Reflexion 模式 (Phase 9)
- Role-playing 模式 (Phase 10)
- MCP 工具集成 (Phase 11)
- 工具调试面板 (v2/Phase 11 后可考虑)
- 参数配置 UI (v2, DIFF-02/03)
- Token 统计 / 每步耗时 (v2, DIFF-03)
- 工具调用详情展开 (v2, DIFF-04)
- 跨模式对比 (v2, DIFF-01)

**反模式规避 (沿用 Phase 1/2/3/4):**

- 不用 setTimeout 模拟流式 - 所有流式必须来自真实 DeepSeek SSE
- reasoning_content 必须可见 (折叠面板默认收起但可展开)
- shallowRef 累积, 前端必须实时渲染 chunk 到达
- 不使用 v-html 渲染流式文本 (XSS 防护)
- ToolCallEvent/ToolResultEvent 不 alter 现有 ReasoningEvent 流式累积行为
- 垂直时间线不阻塞流式渲染 - 事件列表追加与文本累积并行

---

## Registry Safety

**沿用 Phase 1/2/3/4。** 详细参见 `.planning/phases/01-skeleton/01-UI-SPEC.md` §Registry Safety。

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| Element Plus (npm) | 沿用 Phase 1/2/3/4 全量组件 | not required (npm 包) |
| @element-plus/icons-vue (npm) | 沿用 Phase 1/2/3/4 全量图标 | not required (npm 包) |
| 第三方 registry | none | N/A |

**Phase 5 不新增 npm 依赖。** 新增 2 个组件纯 Vue SFC, 不引入第三方 registry。

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS - 沿用 Phase 1/2/3/4 全量中文文案 + 新增 ToolCallEventCard/ToolResultEventCard 文案 + ReAct 错误文案 (含诊断+建议) + react 模式详情文案 + 垂直时间线文案
- [ ] Dimension 2 Visuals: PASS - 沿用 Phase 1/2/3/4 完整视觉基线 + 新增 2 个组件 (ToolCallEventCard/ToolResultEventCard) + 改造 ReasoningPanel 垂直时间线分块, 时间线阶段与配色语义对齐
- [ ] Dimension 3 Color: PASS - 沿用 Phase 1 60/30/10 配色 + amber/blue 视觉分离契约 + 新增 tool-call 配色 (blue=action, green=result, red=error, gray=dedup), 不破坏现有配色语义
- [ ] Dimension 4 Typography: PASS - 沿用 Phase 1 4 档字号 2 档字重, 新增组件使用现有字号规格
- [ ] Dimension 5 Spacing: PASS - 沿用 Phase 1 4px 倍数 token, 新增组件使用现有间距 token
- [ ] Dimension 6 Registry Safety: PASS - 沿用 Phase 1 Element Plus npm 安装, 无第三方 registry 需审查, Phase 5 不新增依赖

**Approval:** pending
