# 功能研究

**领域:** Agent 设计模式教学案例库（Agent Design Patterns Teaching Library）
**研究日期:** 2026-08-04
**置信度:** HIGH（基于 Spring AI 官方文档 + 项目 PROJECT.md + 7 种模式的行业共识实现）

## 功能概览

### 基础功能（用户期望必备）

教学项目的基本功能--缺失任意一个都会让学习者觉得"不完整"或"看不懂"。

| 功能 | 为何期望 | 复杂度 | 备注 |
|---------|--------------|------------|-------|
| 模式选择界面（7 种模式） | 项目核心就是"7 种模式"，必须可选 | LOW | 前端下拉/卡片选择，路由到对应后端 endpoint |
| 问题输入框 | 用户要能提问才能触发 agent | LOW | 文本输入 + 提交按钮，支持 Enter 提交 |
| SSE 流式返回 | "实时观察推理过程"是 PROJECT.md 明确要求的核心体验 | MEDIUM | Spring AI `ChatClient.stream()` 返回 `Flux`，需 SseEmitter 或 WebFlux 转发到前端 |
| 推理过程可视化（非仅最终答案） | 教学价值在于"看清 agent 怎么思考"，仅给答案是失败的 | HIGH | 每种模式有专属的可视化结构（线性/链式/循环/树/对话），不能一刀切 |
| 最终答案展示 | 学习者需要确认 agent 的结论 | LOW | 区分"推理过程"和"最终答案"两个区块 |
| 7 种模式各自的实现 | PROJECT.md Active 列表明确列出 7 种 | HIGH | 见下方"7 种模式最小教学案例" |
| 内置示例工具（ReAct 用） | ReAct 需要工具调用，无工具则无法演示 | MEDIUM | 至少 3 个：天气查询（mock）、计算器、时间查询 |
| 每种模式的系统提示词 | 模式行为由 prompt 主导，必须有可见的 prompt 设计 | LOW | 后端硬编码或常量类管理，前端可显示"当前模式 prompt" |
| 模式说明文档（每种模式是什么/何时用） | 学习者第一次进来需要上下文 | LOW | 前端每个模式卡片附简介，参考 PROJECT.md 中的概览表 |
| 错误处理（LLM 调用失败/超时/工具异常） | 教学项目也要可靠，崩溃会让学习者困惑 | MEDIUM | 全局异常处理 + 前端错误提示，SSE 中断时优雅降级 |
| 清空/重新开始按钮 | 学习者会反复尝试不同问题 | LOW | 重置当前会话状态，清空前端展示区 |
| 中文界面 | 目标用户是中文开发者（参考 PROJECT.md 学习者画像） | LOW | 前端文案中文，后端 prompt 中文 |

### 差异化功能（竞争优势）

让这个项目从"能跑的 demo"变成"有价值的教学工具"的功能。不强制要求，但能显著提升教学价值。

| 功能 | 价值主张 | 复杂度 | 备注 |
|---------|-------------------|------------|-------|
| 每种模式的预设示例问题 | 学习者不知道问什么时，一键体验典型场景；每种模式 3-5 个 | LOW | 前端硬编码 JSON，按模式分组 |
| 模式专属推理可视化结构 | CoT=线性文本、Self-Ask=Q-A 链、ReAct=Thought/Action/Observation 块、Plan-Execute=清单、ToT=树、Reflexion=尝试-反思循环、Role-playing=多角色对话 | HIGH | 每种模式设计独立组件，使用 Element Plus 折叠面板/时间线/标签等 |
| 同问题跨模式对比 | 同一个问题用不同模式跑，直观看出模式差异--这是教学杀手锏 | MEDIUM | 前端支持选择 2-3 个模式并行请求，左右分栏展示结果 |
| 当前模式实现源码查看 | 学习者看完演示想看"怎么写的"--直接跳转 GitHub 源码 | LOW | 每个模式卡片附 `查看源码` 链接，指向具体类 |
| 参数可配置（temperature/maxTokens/model） | 让学习者感受参数对推理的影响 | MEDIUM | 前端侧边栏表单，传入 Spring AI `ChatOptions` |
| 推理过程耗时统计 | 显示每步耗时，理解"模式越复杂越慢"的代价 | LOW | 后端在每个步骤打时间戳，前端展示 |
| Token 用量统计 | 教学价值：让学习者直观看到不同模式的 token 消耗差异 | MEDIUM | 从 `ChatResponse.metadata().usage()` 提取 |
| MCP 外部工具配置入口 | ReAct 模式可接入外部 MCP Server，演示扩展性 | MEDIUM | 前端输入 MCP server URL，后端动态注册 `ToolCallbackProvider` |
| 模式对比矩阵（首页静态） | 一张表对比 7 种模式的核心思想/场景/复杂度，引导学习者选择 | LOW | 静态 markdown/HTML 表格，参考 PROJECT.md 概览表 |
| 步骤级状态指示（运行中/完成/失败） | 长流程模式（Plan-Execute、Reflexion）需要看进度 | LOW | 每个步骤节点显示 loading/success/error 状态 |
| 工具调用细节展开（ReAct） | 点击 Action 块可查看工具名、入参、返回值 | LOW | 折叠面板展示 JSON 详情 |
| Reflexion 评估器可视化 | 显示"为什么这次被认为失败"--评估标准 + 评估结果 | MEDIUM | 如果用 LLM-as-judge，展示评估 prompt 和反馈 |

### 反功能（常被请求但通常有问题）

教学项目要主动避免的范围蔓延。PROJECT.md "Out of Scope" 已列出大部分，这里补充教学场景特有的反模式。

| 功能 | 为何被请求 | 为何有问题 | 替代方案 |
|---------|---------------|-----------------|-------------|
| 用户登录/注册 | "想保存用户的实验记录" | 教学演示单用户即可，登录系统是另一个项目的复杂度 | 本地无认证访问；学习者自己跑 |
| 数据库持久化（对话历史） | "想回看之前的实验" | 增加部署门槛，教学场景下重提一次问题成本极低 | 重启丢失可接受；前端会话内保留即可 |
| 生产部署（Docker/k8s/监控） | "想给别人演示" | 教学项目本地 `mvn spring-boot:run` 即可，运维复杂度会吓退学习者 | README 写清本地启动步骤 |
| 移动端响应式 | "想在手机上看" | 桌面端才能完整展示推理过程的复杂可视化 | 桌面端优先，明确不支持移动端 |
| 多 LLM 提供商切换 | "想对比 DeepSeek 和 OpenAI" | 教学聚焦模式本身，不是 LLM 比较；多提供商配置复杂 | 固定 DeepSeek，PROJECT.md 已决策 |
| 自定义工具构建器（UI 拖拽） | "想让用户自己加工具" | 工具是 Java 代码定义的，UI 拖拽不现实；偏离教学核心 | 内置固定工具集 + MCP 配置入口 |
| RAG / 向量数据库集成 | "想演示 RAG agent" | RAG 不是 7 种模式之一；增加向量库部署门槛 | 不做；如需，作为独立项目 |
| 自定义 Agent 编排器 | "想让用户拖拽组合模式" | 远超教学范围，是产品级功能 | 7 种模式独立演示即可 |
| 对话历史导出（PDF/Markdown） | "想保存教学案例" | 无持久化层的前提，导出功能反而需要额外存储 | 学习者自行截图或复制 |
| 真实外部 API 调用（真天气 API） | "想让 ReAct 更真实" | 需要外部 API key，增加环境配置；mock 足够教学 | 内置 mock 工具，返回固定/随机数据 |
| 多语言国际化（i18n） | "想支持英文用户" | 目标用户是中文开发者，i18n 是工程负担 | 中文优先，代码注释/prompt 中文 |
| 暗黑模式/主题切换 | "看着酷" | 非教学核心，UI 工作量翻倍 | 单一主题，Element Plus 默认 |
| 实时多用户协作 | "想课堂演示给学生看" | 单用户教学场景，多用户实时同步是另一个量级 | 教师本地演示，学生各自跑 |
| 用户反馈/评分系统 | "想收集学习者反馈" | 教学项目无用户系统，反馈走 GitHub issue | README 引导提 issue |

## 7 种模式最小教学案例

每种模式的"最小可演示版本"--能讲清原理即可，不追求生产级健壮性。

### 模式 1: CoT（Chain of Thought 思维链）

**核心演示点：** LLM 一步步推理后再给答案。

**最小实现：**
- 系统提示词：`请一步步思考后再给出答案，先展示推理过程，最后用"答案：xxx"给出结论`
- 一次 `ChatClient.stream()` 调用
- 前端流式渲染完整文本

**可视化结构：** 线性流式文本，"答案："之后高亮为最终结果块

**典型问题：** `小明有 5 个苹果，给了小红 2 个，又买了 3 个，现在小明有几个苹果？`

**复杂度：** LOW（半天）-- 仅 prompt 工程

### 模式 2: Self-Ask（自问自答）

**核心演示点：** 大问题拆成子问题，逐个回答后合成。

**最小实现：**
- 系统提示词要求 LLM 输出结构化格式：`Follow-up: <子问题>\nAnswer: <子答案>\n... Final: <最终答案>`
- 一次 LLM 调用（让 LLM 自己拆解并回答）；或多次调用逐个回答子问题
- 前端解析 `Follow-up/Answer/Final` 标签，渲染为链式结构

**可视化结构：** 子问题 -> 子答案链式时间线，最后是最终答案块

**典型问题：** `iPhone 制造商的 CEO 的母校是哪所大学？`（多跳事实检索）

**复杂度：** MEDIUM（1-2 天）-- 需要结构化输出解析

**注意：** 无外部检索时，子答案来自 LLM 参数知识--教学时说明这是"无检索的 Self-Ask"，仍能演示拆解逻辑

### 模式 3: ReAct（Reasoning + Acting 推理+行动）

**核心演示点：** Thought -> Action -> Observation 循环，工具调用穿插推理。

**最小实现：**
- 系统提示词：ReAct 范式说明 + 可用工具列表
- Spring AI `ChatClient.prompt().tools(weatherCallback, calculatorCallback, timeCallback).stream()`
- 至少 3 个内置工具：
  - `getWeather(city)` -- 返回 mock 天气数据
  - `calculate(expression)` -- 真实计算器（用 `ScriptEngine` 或 `exp4j`）
  - `getCurrentTime()` -- 返回当前时间
- MCP 扩展：支持配置外部 MCP Server URL，动态加载工具

**可视化结构：** Thought 块（蓝色）-> Action 块（绿色，显示工具名+入参）-> Observation 块（灰色，显示工具返回值）-> 循环 -> Final Answer 块

**典型问题：** `北京现在的天气怎么样？另外帮我算一下 25 * 17`

**工具调用要求（明确）：**
- 内置工具：3 个 Java 方法，用 `@Tool` 注解或 `FunctionToolCallback.builder()` 注册
- MCP 工具：通过 `ToolCallbackProvider`（如 Spring AI MCP Client Starter）接入外部 MCP Server
- 配置入口：前端提供 MCP Server URL 输入框，后端动态创建 MCP client

**复杂度：** MEDIUM（2-3 天）-- Spring AI 工具调用 + MCP 集成 + 前端块状可视化

### 模式 4: Plan-and-Execute（计划与执行）

**核心演示点：** 先生成步骤清单，再逐步执行。

**最小实现：**
- 阶段 1（Planner）：LLM 调用，系统提示词 `将任务分解为 3-6 个具体步骤，输出 JSON 数组`，解析为 `List<String>`
- 阶段 2（Executor）：对每个步骤，单独 LLM 调用，将步骤 + 已完成步骤的结果作为上下文
- 前端展示：步骤清单（checkbox 风格），逐步打勾 + 展示该步输出

**可视化结构：** 上方计划清单（带状态：待执行/执行中/已完成），下方逐步输出滚动区

**典型问题：** `写一篇关于 AI Agent 的 3 段式博客`（计划：列大纲、写引言、写正文、写结论、通读润色）

**复杂度：** MEDIUM（2 天）-- 两阶段逻辑 + JSON 解析 + 顺序执行

### 模式 5: Tree of Thoughts（树状思维）

**核心演示点：** 多分支生成 -> 评估打分 -> 择优搜索。

**最小实现：**
- 输入问题后，LLM 生成 3 个候选"下一步思考"
- LLM 评估每个候选（打分 1-10）
- 选最高分作为当前节点，重复直到达到深度上限或找到答案
- 限制：深度 ≤ 3，分支数 = 3（避免 token 爆炸）
- 前端：树形结构可视化（可用 Element Plus `el-tree` 或自定义 SVG）

**可视化结构：** 树状图，每个节点显示思考内容 + 评分，剪枝的分支灰色显示，最优路径高亮

**典型问题：** `用 4、7、8、8 通过加减乘除得到 24`（24 点游戏，需要试错）

**复杂度：** HIGH（3-5 天）-- 树数据结构 + 生成/评估两次 LLM 调用 + 搜索算法（BFS 或贪心）+ 树可视化

**简化建议：** 第一版用"贪心选最优"而非完整 BFS/DFS，避免实现复杂搜索

### 模式 6: Reflexion（反思迭代）

**核心演示点：** 失败 -> 反思错误 -> 带反思重试 -> 直至成功。

**最小实现：**
- 阶段 1（Generate）：LLM 生成代码/答案
- 阶段 2（Evaluate）：执行代码或用 LLM-as-judge 评估
  - 简化版：用 LLM-as-judge，prompt 含测试用例或评判标准
  - 进阶版：实际执行生成的 Python/Java 代码（复杂，不推荐 v1）
- 阶段 3（Reflect）：若失败，LLM 反思"为什么失败、下次怎么改"
- 阶段 4（Retry）：将反思加入 prompt，重新生成
- 最多 3 次尝试

**可视化结构：** 尝试 1 -> 结果（失败）-> 反思 -> 尝试 2 -> 结果（成功）的循环时间线，每次尝试展开显示代码/答案 + 评估反馈

**典型问题：** `写一个 Python 函数判断字符串是否回文`（评估：跑 3 个测试用例 `aba`、`abc`、`a`）

**复杂度：** HIGH（3-5 天）-- 评估器设计 + 反思 prompt 工程 + 循环控制 + 失败/成功状态机

**简化建议：** v1 用 LLM-as-judge（给定测试用例让 LLM 判定通过与否），不实际执行代码

### 模式 7: Role-playing（角色扮演）

**核心演示点：** 多智能体分工协作，不同角色视角互补。

**最小实现：**
- 定义 3 个角色，每个角色独立 `ChatClient` + 独立系统提示词：
  - 产品经理：明确需求、验收
  - 开发工程师：技术实现
  - 测试工程师：找漏洞、提边界情况
- 编排逻辑：用户提问 -> PM 分析需求 -> Dev 给方案 -> Tester 提质疑 -> Dev 修正 -> PM 总结
- 每个角色的输出流式展示，标注角色名 + 头像/颜色

**可视化结构：** 多角色对话流（类似群聊），每条消息标注角色、颜色区分，可折叠同一角色的连续发言

**典型问题：** `设计一个用户登录功能`

**复杂度：** MEDIUM-HIGH（2-4 天）-- 多 agent 编排 + 角色间消息传递 + 对话流 UI

**简化建议：** 固定 3 角色和固定对话轮次（如 PM->Dev->Tester->Dev->PM，5 轮），不做动态调度

## 功能依赖关系

```
[模式选择界面]
    └──requires──> [7 种模式各自实现]
                       ├──requires──> [SSE 流式返回]
                       ├──requires──> [推理过程可视化（模式专属）]
                       └──requires──> [系统提示词管理]

[ReAct 模式实现]
    └──requires──> [内置示例工具（≥3 个）]
    └──enhances──> [MCP 外部工具配置入口]

[同问题跨模式对比]
    └──requires──> [7 种模式各自实现]（全部完成）
    └──requires──> [并行 SSE 通道]（前端并发请求 + 分栏展示）

[参数可配置]
    └──enhances──> [所有模式]（通过 Spring AI ChatOptions 注入）

[Token 用量统计]
    └──requires──> [从 ChatResponse.metadata().usage() 提取]
    └──enhances──> [推理过程可视化]（每步显示 token）

[Reflexion 评估器]
    └──requires──> [LLM-as-judge prompt 设计]
    └──conflicts──> [实际代码执行]（v1 不做，避免安全/复杂度）

[Tree of Thoughts 树可视化]
    └──requires──> [树数据结构 + 生成/评估 LLM 调用]
    └──enhances──> [剪枝/最优路径高亮]
```

### 依赖关系说明

- **所有模式 requires SSE 流式返回：** SSE 是项目核心体验（PROJECT.md 明确），任何模式实现前必须先打通 SSE 管道
- **同问题跨模式对比 requires 全部 7 模式：** 对比功能依赖所有模式就绪，建议放在最后一个 milestone
- **ReAct enhances MCP 配置：** ReAct 内置工具是基础，MCP 是扩展--MCP 失败不影响 ReAct 基础演示
- **Reflexion conflicts 实际代码执行：** v1 用 LLM-as-judge 评估，不执行用户生成的代码--安全且简单
- **ToT 简化（贪心 vs BFS）：** v1 用贪心选最优分支，避免实现完整搜索算法--足够演示"多分支探索择优"思想
- **参数可配置 enhances 所有模式：** 通过 `ChatOptions` 注入，是横切增强，非阻塞依赖

## MVP 定义

### 启动版本 (v1)

最小可用版本--验证"能用案例讲清 7 种模式"这个核心假设。

- [ ] 前后端骨架打通（Spring Boot + Vue 3 + DeepSeek 连通）-- PROJECT.md 第一个 milestone 范围
- [ ] SSE 流式返回 + 前端实时渲染 -- 核心体验
- [ ] 模式选择界面（7 种模式可选）
- [ ] 问题输入 + 提交 + 清空
- [ ] 7 种模式各自最小实现（见上方"最小教学案例"）
- [ ] 推理过程可视化（每种模式专属结构，第一版可简化但必须区分）
- [ ] 内置 3 个工具（ReAct 用）
- [ ] 每种模式 3-5 个预设示例问题
- [ ] 模式说明文档（首页对比矩阵 + 每模式卡片简介）
- [ ] 错误处理（LLM 失败、SSE 中断友好提示）

### 验证后添加 (v1.x)

v1 跑通后，提升教学价值的增强功能。

- [ ] 同问题跨模式对比 -- 依赖 7 模式稳定，是教学杀手锏
- [ ] 参数可配置（temperature / maxTokens）-- 让学习者感受参数影响
- [ ] Token 用量 + 耗时统计 -- 量化"模式复杂度代价"
- [ ] MCP 外部工具配置入口 -- ReAct 扩展性演示
- [ ] 工具调用细节展开（ReAct 的 Action/Observation 折叠详情）
- [ ] 源码查看链接（每模式跳转 GitHub 类文件）
- [ ] Reflexion 评估器可视化（展示评估 prompt 和反馈）
- [ ] ToT 剪枝/最优路径高亮

### 未来考虑 (v2+)

非教学核心，未来可能扩展。

- [ ] 实际代码执行（Reflexion 用真实沙箱跑生成的代码）-- 安全和复杂度问题
- [ ] 自定义角色组合（Role-playing 让用户选角色）-- 偏离"7 模式演示"焦点
- [ ] 模式混搭演示（如 ReAct + Reflexion）-- 演示模式组合，但增加复杂度
- [ ] 学习路径引导（推荐先看 CoT 再看 ToT）-- 教学法增强，非核心
- [ ] 导出对比结果（Markdown 报告）-- 依赖对比功能且增加存储逻辑

## 功能优先级矩阵

| 功能 | 用户价值 | 实现成本 | 优先级 |
|---------|------------|---------------------|----------|
| 模式选择界面 | HIGH | LOW | P1 |
| 问题输入 + 提交 | HIGH | LOW | P1 |
| SSE 流式返回 | HIGH | MEDIUM | P1 |
| 7 种模式最小实现 | HIGH | HIGH | P1 |
| 推理过程可视化（模式专属） | HIGH | HIGH | P1 |
| 内置工具（ReAct） | HIGH | MEDIUM | P1 |
| 预设示例问题 | HIGH | LOW | P1 |
| 模式说明文档 | MEDIUM | LOW | P1 |
| 错误处理 | MEDIUM | MEDIUM | P1 |
| 同问题跨模式对比 | HIGH | MEDIUM | P2 |
| 参数可配置 | MEDIUM | MEDIUM | P2 |
| Token/耗时统计 | MEDIUM | MEDIUM | P2 |
| MCP 工具配置入口 | MEDIUM | MEDIUM | P2 |
| 源码查看链接 | MEDIUM | LOW | P2 |
| 工具调用详情展开 | LOW | LOW | P2 |
| Reflexion 评估器可视化 | MEDIUM | MEDIUM | P2 |
| ToT 剪枝高亮 | LOW | LOW | P2 |
| 实际代码执行（Reflexion） | LOW | HIGH | P3 |
| 自定义角色组合 | LOW | HIGH | P3 |
| 模式混搭演示 | MEDIUM | HIGH | P3 |

**优先级说明:**
- P1: 启动必备 -- v1 必备
- P2: 应有，可能时添加 -- v1.x 增强
- P3: 锦上添花，未来考虑 -- v2+ 考虑

## 竞品功能分析

| 功能 | LangChain（Python 文档/示例） | LangChain4j（Java） | Spring AI 官方示例 | PromptingGuide.ai | 本项目方案 |
|---------|--------------------------|--------------------|------------------|-------------------|------------|
| 7 种模式覆盖 | 部分（ReAct、ToT 有，其他散落） | 仅 ReAct 较完整 | 不按模式组织 | 全部 7 种有文字说明 | **全部 7 种可运行** |
| 可运行案例 | Python notebook 居多 | Java 但偏框架示例 | 功能 demo，非模式视角 | 无（纯文章） | **Java + Spring AI 可运行** |
| 推理过程可视化 | 命令行输出为主 | 控制台日志 | 控制台日志 | 静态文本图 | **前端可视化（模式专属结构）** |
| 同问题跨模式对比 | 无 | 无 | 无 | 文字对比表格 | **支持并行对比** |
| 中文友好 | 英文为主 | 英文为主 | 英文为主 | 中文（文章） | **全中文界面 + 中文 prompt** |
| MCP 集成 | 有 | 有 | 有 | 无 | **ReAct 模式可接入** |
| 教学定位 | 框架文档 | 框架文档 | 框架文档 | 概念科普 | **可运行教学案例库** |

**差异化定位：** 现有资源要么是框架文档（按功能组织，不按模式），要么是纯文章（无运行案例）。本项目填补"中文 + 可运行 + 7 模式完整 + 前端可视化"这个空白。

## 来源

- 项目上下文：`/Volumes/D/dev/workspace/AI/agents/.planning/PROJECT.md`（HIGH 置信度，项目定义）
- Spring AI 官方文档（Context7 /websites/spring_io_spring-ai_reference）：
  - Tool calling & MCP：`https://docs.spring.io/spring-ai/reference/api/tools.html`、`https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-examples.html`（HIGH）
  - Streaming & ChatClient：`https://docs.spring.io/spring-ai/reference/api/chatclient.html`、`https://docs.spring.io/spring-ai/reference/api/advisors.html`（HIGH）
  - DeepSeek 配置：`https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html`（HIGH）
  - LLM-as-judge（Reflexion 评估器参考）：`https://docs.spring.io/spring-ai/reference/guides/llm-as-judge.html`（HIGH）
- 7 种模式行业共识（基于 PROJECT.md 引用的鱼皮AI导航文章 + 训练数据中的 ReAct/ToT/Reflexion 论文共识）：MEDIUM-HIGH
  - ReAct 论文：Yao et al., 2022（Thought/Action/Observation 范式）
  - ToT 论文：Yao et al., 2023（decompose/generate/evaluate/search 四步）
  - Reflexion 论文：Shinn et al., 2023（fail-reflect-retry 循环）

---
*功能研究: Agent 设计模式教学案例库*
*研究日期: 2026-08-04*
