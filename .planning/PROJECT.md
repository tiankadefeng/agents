# Agent 设计模式教学案例库

## What This Is

基于 Spring AI + DeepSeek 的 Agent 设计模式教学案例库，实现了 7 种经典 agent 设计模式（CoT、Self-Ask、ReAct、Plan-and-Execute、Tree of Thoughts、Reflexion、Role-playing）。用户可在前端选择不同模式，输入问题，通过 SSE 流式实时观察 agent 的推理过程（思考链、工具调用、子问题分解等）和最终答案。面向学习 agent 设计模式的开发者。

## Core Value

让学习者通过可运行的案例，直观理解 7 种 agent 设计模式的工作原理与差异——能看清每种模式"怎么思考"、"为何这么设计"。

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ 架构骨架搭建（前后端骨架 + DeepSeek 打通，暂不含 agent 模式） — Phase 1
- ✓ 前后端分离架构：Spring Boot 后端 + Vue 3 前端 — Phase 1
- ✓ DeepSeek LLM 集成（通过 Spring AI） — Phase 2
- ✓ 模式选择界面：用户可选择 7 种 agent 模式之一 — Phase 2
- ✓ SSE 流式返回，前端实时展示 agent 输出 — Phase 2
- ✓ 前端展示完整推理过程（思考链、工具调用、子问题等） — Phase 2
- ✓ CoT（思维链）模式实现 — Phase 3
- ✓ Self-Ask（自问自答）模式实现 — Phase 6
- ✓ ReAct（推理+行动）模式实现 — Phase 5
- ✓ Plan-and-Execute（计划与执行）模式实现 — Phase 7
- ✓ Tree of Thoughts（树状思维）模式实现 — Phase 8
- ✓ Reflexion（反思迭代）模式实现 — Phase 9
- ✓ Role-playing（角色扮演）模式实现 — Phase 10
- ✓ 内置示例工具（天气查询、计算器、时间查询等，供 ReAct 使用） — Phase 5

### Active

<!-- Current scope. Building toward these. -->

- [ ] MCP 工具集成支持（可接入外部 MCP Server）

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- 数据库持久化 - 教学项目，对话无需保存，重启丢失可接受
- 用户认证/登录 - 教学演示用途，无需多用户隔离
- 生产部署与运维 - 本地运行即可，不做容器化/监控/告警
- 移动端适配 - 桌面端优先，移动端体验不在本期范围
- 多租户/权限管理 - 单用户教学场景
- 对话历史回看 - 无数据库，不保存历史

## Context

**项目背景：**
- 参考《7 种智能体（Agent）设计模式深度解析》文章（鱼皮AI导航，执笔经年，2026-07-10）
- 7 种模式覆盖了从线性推理（CoT）到多智能体协作（Role-playing）的完整谱系
- 模式并非互斥，实际开发中常混搭使用；理解这些模式有助于框架选型（LangChain4j、Spring AI Alibaba、Dify 等）和架构设计

**7 种模式概览：**
| 模式 | 核心思想 | 典型场景 |
|------|---------|---------|
| CoT（思维链） | 一步步写推理过程 | 数学计算、逻辑推理 |
| Self-Ask（自问自答） | 拆大问题为小问题 | 多跳事实检索 |
| ReAct（推理+行动） | 思考与工具调用交替 | 实时信息查询、API 调用 |
| Plan-and-Execute | 先规划再逐步执行 | 多步骤长任务 |
| Tree of Thoughts | 多分支探索择优 | 解谜、复杂规划 |
| Reflexion（反思迭代） | 犯错后自我纠错 | 代码生成、流程执行 |
| Role-playing（角色扮演） | 多智能体分工协作 | 软件开发、跨职能协同 |

**技术环境：**
- 现有 Maven 骨架：`com.atguigu.gulimall:agents`，Java 21
- 无现有源代码，greenfield 开发
- 当前阶段：架构搭建（第一个 milestone）

**学习者画像：**
- 想理解 agent 设计模式原理的 Java/Spring 开发者
- 希望看到可运行案例而非纯理论
- 关心"每种模式怎么实现"、"推理过程长什么样"

## Constraints

- **Tech stack**: Spring AI + DeepSeek + Vue 3 + Element Plus - 技术栈已定，Java 生态 agent 框架 + 国内可访问 LLM
- **Java version**: 21 - pom.xml 已设定，Spring AI 需要 Java 17+
- **LLM**: DeepSeek - 成本友好，国内可访问，性能足够教学演示
- **No database**: 纯演示，无持久化层 - 教学项目简化架构，降低部署门槛
- **Streaming**: SSE 流式返回 - 实时展示推理过程是教学核心体验
- **Build tool**: Maven - 已有 pom.xml 骨架
- **当前阶段范围**: 仅架构骨架，不含 agent 模式实现 - 第一个 milestone 聚焦打通前后端 + DeepSeek

## Key Decisions

<!-- Decisions that constrain future work. Add throughout project lifecycle. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Spring AI 作为 agent 框架 | Java 生态原生支持，与 Spring Boot 集成好，官方维护 | - Pending |
| DeepSeek 作为 LLM | 成本友好，国内可访问，性能足够教学 | - Pending |
| SSE 流式返回 | 实时展示推理过程，教学体验好，Spring AI 原生支持 | - Pending |
| Vue 3 + Element Plus 前端 | 国内主流，组件丰富，适合中后台型教学界面 | - Pending |
| 无数据库持久化 | 教学项目简化架构，降低部署门槛 | - Pending |
| 内置工具 + MCP 双支持 | 内置工具快速演示 ReAct，MCP 支持扩展外部工具生态 | - Pending |
| 先架构骨架后加模式 | 第一个 milestone 聚焦打通前后端 + DeepSeek，后续逐个加 7 种模式 | - Pending |
| 展示完整推理过程 | 教学核心价值在于"看清 agent 怎么思考"，非仅最终答案 | - Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions
5. "What This Is" still accurate? -> Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check - still the right priority?
3. Audit Out of Scope - reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-21 after Phase 10*
