<!-- GSD:project-start source:PROJECT.md -->
## Project

**Agent 设计模式教学案例库**

基于 Spring AI + DeepSeek 的 Agent 设计模式教学案例库，实现了 7 种经典 agent 设计模式（CoT、Self-Ask、ReAct、Plan-and-Execute、Tree of Thoughts、Reflexion、Role-playing）。用户可在前端选择不同模式，输入问题，通过 SSE 流式实时观察 agent 的推理过程（思考链、工具调用、子问题分解等）和最终答案。面向学习 agent 设计模式的开发者。

**Core Value:** 让学习者通过可运行的案例，直观理解 7 种 agent 设计模式的工作原理与差异——能看清每种模式"怎么思考"、"为何这么设计"。

### Constraints

- **Tech stack**: Spring AI + DeepSeek + Vue 3 + Element Plus - 技术栈已定，Java 生态 agent 框架 + 国内可访问 LLM
- **Java version**: 21 - pom.xml 已设定，Spring AI 需要 Java 17+
- **LLM**: DeepSeek - 成本友好，国内可访问，性能足够教学演示
- **No database**: 纯演示，无持久化层 - 教学项目简化架构，降低部署门槛
- **Streaming**: SSE 流式返回 - 实时展示推理过程是教学核心体验
- **Build tool**: Maven - 已有 pom.xml 骨架
- **当前阶段范围**: 仅架构骨架，不含 agent 模式实现 - 第一个 milestone 聚焦打通前后端 + DeepSeek
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Executive Recommendation
## Recommended Stack
### Core Technologies
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **JDK** | Java 21 (LTS) | Runtime + compile target | Already in `pom.xml`. LTS until Sept 2028. Spring Boot 4.x requires 17+, so 21 is the safe modern LTS. Java 25 is newer LTS but 21 has broader library support and matches the existing project skeleton. |
| **Spring Boot** | 4.1.0 | App framework, auto-configuration, embedded Tomcat | Latest GA line (4.x). Required by Spring AI 2.0.x ("Spring AI 2.0.x is compatible with Spring Boot versions 4.0.x and 4.1.x" — verified in Spring AI reference). 4.1 over 4.0 because 4.1 is current patch line with bug fixes; both work with Spring AI 2.0. |
| **Spring AI** | 2.0.0 (BOM) | Agent framework: ChatClient, tool calling, MCP, memory, structured output | Latest GA. Released 2026-06-12 (Maven Central `lastUpdated`). First stable release with the mature `@Tool`/`ToolCallback` API, the `@McpTool`/`@McpToolParam` annotation stack, `MessageChatMemoryAdvisor`, and the dedicated DeepSeek starter. Using the BOM (`spring-ai-bom:2.0.0`) manages all module versions consistently. |
| **Spring AI DeepSeek starter** | `spring-ai-starter-model-deepseek` (managed by BOM) | DeepSeek chat model integration | Native DeepSeek integration: `DeepSeekChatModel`, `DeepSeekChatOptions`, `DeepSeekApi.ChatModel` enum. Auto-configures off `spring.ai.deepseek.*` properties. Preferred over the OpenAI starter pointed at DeepSeek's base URL — see "What NOT to Use". |
| **Spring AI MCP client starter** | `spring-ai-starter-mcp-client` (managed by BOM) | MCP tool server consumption (project requirement: MCP support) | Auto-configures MCP clients with stdio / HTTP / Streamable HTTP transports. Pairs with `SyncMcpToolCallbackProvider` to expose MCP server tools to `ChatClient`. Verified in Spring AI 2.0 reference. |
| **Vue** | 3.5.40 | Frontend framework (Composition API) | Latest stable 3.x (npm `vue@latest`). Composition API is the default for new projects. 3.5.x has `useTemplateRef`, reactive props destructure, and SSR improvements over 3.4. Stays on 3.x (not 4.x) — Vue 4 does not exist as a stable release. |
| **Element Plus** | 2.14.3 | UI component library (Vue 3) | Latest stable (npm `element-plus@latest`). Peer-depends on `vue ^3.3.7` — satisfied by 3.5.40. Standard choice for Chinese-language admin/teaching UIs; rich components (`el-tabs`, `el-input`, `el-collapse`, `el-steps`) directly map to the 7-pattern selector + reasoning-step display needs. |
| **Vite** | 8.2.0 | Frontend dev server + bundler | Latest stable (npm `vite@latest` = 8.2.0; `previous` tag = 7.3.6). Vite 8 is the current line; `@vitejs/plugin-vue@6.0.8` peer-supports Vite `^5 \|\| ^6 \|\| ^7 \|\| ^8`. Use 8.x for fresh project — no breaking migration from older Vite. |
| **@vitejs/plugin-vue** | 6.0.8 | Vue 3 SFC compilation for Vite | Latest stable. Required to compile `.vue` files. Peer-compatible with Vite 8.x. |
| **Maven** | 3.9+ (build tool) | Backend build | Already chosen (existing `pom.xml`). Spring Boot 4.x parent POM works with Maven 3.9+. No Gradle consideration — project committed to Maven. |
### Supporting Libraries
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-web` | 4.1.0 (via parent) | Servlet stack, MVC controllers, embedded Tomcat | Always. Primary web stack. Handles both REST endpoints and SSE streaming (via reactive return types). |
| `spring-boot-starter-webflux` | (do NOT add) | — | DO NOT add as a starter. `spring-ai-starter-model-deepseek` already pulls `reactor-core` + `reactor-netty` transitively for `Flux` support. Adding `spring-boot-starter-webflux` triggers Spring Boot's WebFlux auto-configuration and conflicts with MVC. |
| `@element-plus/icons-vue` | 2.3.2 | Icon set for Element Plus UI | Always (paired with Element Plus). Needed for buttons, tabs, status indicators in the pattern selector and reasoning visualizer. |
| `reactor-core` | (transitive via Spring AI) | `Flux` / `Mono` for streaming | Automatic. Used in controllers to return `Flux<ServerSentEvent<...>>`. No explicit dependency needed. |
| `spring-ai-starter-model-openai` | (do NOT add) | — | Skip. Use the dedicated DeepSeek starter. OpenAI starter only relevant if you also want GPT models. |
| `axios` | ^1.7+ | HTTP client for non-streaming API calls | Optional. For SSE consumption use the browser-native `EventSource` (or `fetch` + ReadableStream for POST-based SSE). Use axios only for one-shot calls like fetching the list of available patterns. |
| TypeScript | ^5.6+ | Type safety on frontend | Recommended. `@vitejs/plugin-vue` peer-depends on `typescript: *`. Vue SFC + `<script setup lang="ts">` is the 2026 default. Skip only if team is TS-averse. |
### Development Tools
| Tool | Purpose | Notes |
|------|---------|-------|
| **Spring Boot Maven Plugin** | `mvn spring-boot:run`, fat-jar packaging | Add `<build><plugins><plugin>org.springframework.boot:spring-boot-maven-plugin</plugin></plugins></build>`. Plugin version inherited from `spring-boot-starter-parent:4.1.0`. |
| **Node.js 22 LTS** | Frontend runtime for Vite | Required by Vite 8.x. Node 22 is the current LTS. Node 20 works but is in maintenance. |
| **`create-vue`** | Frontend scaffolding | `npm create vue@latest` — official Vue 3 + Vite scaffold. Pick TS + Composition API. Then `npm install element-plus @element-plus/icons-vue`. |
| **Spring Initializr** | Backend scaffolding (alternative to manual pom.xml) | https://start.spring.io — select Spring Boot 4.1.0, Java 21, dependencies: Web, Spring AI OpenAI (then swap to DeepSeek starter manually). Useful to verify the correct pom.xml shape. |
| **DeepSeek API key** | LLM access | Set `DEEPSEEK_API_KEY` env var. Get from https://platform.deepseek.com/. Free tier sufficient for teaching demo. |
## Installation
### Backend (Maven)
### Frontend (npm)
# Scaffold Vue 3 + Vite + TS project in ./frontend
#   (select: TypeScript = Yes, Vue Router = optional, Pinia = optional, Vitest = optional)
# UI library
# (Optional) HTTP client for non-streaming calls
# Dev tooling (optional but recommended)
### Run
# Backend
# Frontend (separate terminal)
## Alternatives Considered
| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| **Spring AI 2.0.0** | Spring AI 1.1.8 | Only if you must stay on Spring Boot 3.5.x (e.g., existing corp stack). 1.1.x is the last 3.x line; gets bug fixes but no new features. For a greenfield 2026 project, 2.0 is the right call. |
| **Spring AI 2.0.0** | LangChain4j | When you want a non-Spring framework or need a feature Spring AI lacks. For this project Spring AI is mandated by PROJECT.md and is the better fit (Spring-native idioms, ChatClient, MCP, BOM-managed). LangChain4j is mature but introduces a parallel abstraction layer. |
| **Spring AI 2.0.0** | Spring AI Alibaba | When you specifically need Alibaba's agent abstractions (Graph, Flow) or DashScope models. Overkill for teaching pure design patterns; adds a layer of magic that obscures the patterns being taught. |
| **`spring-ai-starter-model-deepseek`** | `spring-ai-starter-model-openai` pointed at `https://api.deepseek.com/v1` | When you want to swap LLM providers without changing code. For this project, the native DeepSeek starter gives correct default model names, proper error handling, and DeepSeek-specific options (e.g., `deepseek-reasoner` reasoning content). The OpenAI-compatible shim works but loses fidelity. |
| **Spring Boot 4.1.0** | Spring Boot 3.5.x | When your organization hasn't approved 4.x yet. Forces Spring AI 1.1.x. Fine for teaching but you'll be one major version behind. |
| **Java 21** | Java 25 | When you want the newest LTS (Sept 2025) and your toolchain supports it. Spring Boot 4.1 runs on 17+, so 25 works. But 21 is more battle-tested and already in the pom.xml — no reason to migrate. |
| **Spring MVC + `Flux<ServerSentEvent>`** | Spring WebFlux (full reactive) | When you need end-to-end reactive pipeline (backpressure, non-blocking DB drivers, etc.). For this project, no DB, no high concurrency — MVC's reactive-return-type support is sufficient and keeps the mental model simpler. |
| **Spring MVC + `Flux<ServerSentEvent>`** | Spring MVC + `SseEmitter` | When you need fine-grained control over individual SSE event IDs, comments, or heartbeats. For most teaching scenarios, `Flux<ServerSentEvent>` is more idiomatic and ~5× less boilerplate. Use SseEmitter only if you find a specific need. |
| **Vue 3.5.40** | React / Svelte | When team expertise dictates. PROJECT.md mandates Vue 3. |
| **Element Plus 2.14.3** | Naive UI / Ant Design Vue / Arco Design | When you prefer a different visual style. Element Plus is the de-facto standard for Chinese-language Vue 3 admin UIs and has the best Chinese docs — important for this teaching audience. |
| **Vite 8.2.0** | Vite 7.3.6 (previous tag) | When a critical plugin hasn't updated for Vite 8 yet. `@vitejs/plugin-vue@6.0.8` already supports Vite 8, so use 8. |
| **Maven** | Gradle (Kotlin DSL) | When team prefers Gradle. PROJECT.md mandates Maven (existing pom.xml). |
## What NOT to Use
| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **LangChain4j** | Project constraint: PROJECT.md mandates Spring AI. LangChain4j is a parallel ecosystem; mixing the two creates confusion. Spring AI 2.0 has feature parity for everything this project needs (tools, MCP, memory, structured output). | `spring-ai-starter-model-deepseek` + `spring-ai-starter-mcp-client` |
| **`spring-boot-starter-webflux`** (as the primary stack) | Pulls in Netty, disables Tomcat auto-config, forces full reactive mental model. Unnecessary for an app with no DB and ~10 routes. Spring MVC 6+ supports `Flux` return types natively. | `spring-boot-starter-web` (MVC) + return `Flux<ServerSentEvent<...>>` from streaming endpoints. `reactor-core` comes transitively via Spring AI. |
| **OpenAI-compatible DeepSeek integration** (`spring-ai-starter-model-openai` + custom `base-url`) | Works but loses DeepSeek-specific features: native `deepseek-reasoner` reasoning_content field, proper model enum, DeepSeek error semantics. Adds an abstraction layer between your code and the actual API. | `spring-ai-starter-model-deepseek` — first-class, auto-configured, model enum included. |
| **Database (JPA / H2 / PostgreSQL / MongoDB)** | PROJECT.md explicitly out of scope: "数据库持久化 - 教学项目，对话无需保存，重启丢失可接受". Adding one doubles the surface area for zero teaching value. | In-memory `MessageWindowChatMemory` (Spring AI built-in) for chat history within a session. |
| **Spring Security** | PROJECT.md: "用户认证/登录 - 教学演示用途，无需多用户隔离". Adds CSRF, session, CORS complexity that fights SSE. | None. Open endpoint. (Acceptable for local teaching tool.) |
| **WebSocket** | SSE is unidirectional server→client, which is exactly what streaming LLM output needs. WebSocket is bidirectional and adds framing/handshake complexity for no benefit. | SSE via `Flux<ServerSentEvent<...>>` or `SseEmitter`. |
| **`PromptChatMemoryAdvisor`** | Removed in Spring AI 2.0. Still appears in old tutorials. | `MessageChatMemoryAdvisor` (verified in Spring AI 2.0 upgrade notes). |
| **Old artifact IDs** (`spring-ai-openai-spring-boot-starter`) | Renamed in Spring AI 2.0 M3+. Old IDs won't resolve. | `spring-ai-starter-model-openai` / `spring-ai-starter-model-deepseek` (new naming convention: `spring-ai-starter-model-*`). |
| **Old MCP imports** (`org.springaicommunity.mcp.annotation.*`) | Moved to `org.springframework.ai.mcp.annotation.*` in Spring AI 2.0. Old packages won't compile. | New package paths per Spring AI 2.0 migration guide. |
| **Vue 2 / Options API** | Vue 2 EOL'd Dec 2023. Options API still works in Vue 3 but Composition API + `<script setup>` is the 2026 default and what students should learn. | Vue 3.5 + `<script setup lang="ts">` + Composition API. |
| **Webpack / vue-cli** | Replaced by Vite years ago. Slower dev server, slower HMR. | Vite 8.x. |
| **`mcp-spring-webflux` / `mcp-spring-webmvc`** under `io.modelcontextprotocol.sdk` | Moved to `org.springframework.ai` group in Spring AI 2.0. Old coordinates will fail to resolve. | Use the BOM-managed coordinates (no version needed). |
## Stack Patterns by Variant
### Primary pattern: SSE streaming from Spring MVC
### Variant: When you need fine-grained SSE control (SseEmitter)
### Variant: DeepSeek `reasoner` model (for Reflexion / Tree of Thoughts)
### Variant: MCP external tools (for ReAct with external servers)
# application.yml
## Version Compatibility
| Component | Version | Compatible With | Notes |
|-----------|---------|-----------------|-------|
| Spring AI BOM | 2.0.0 | Spring Boot 4.0.x, 4.1.x | Verified: "Spring AI 2.0.x is compatible with Spring Boot versions 4.0.x and 4.1.x" (Spring AI reference, getting-started). |
| Spring AI BOM | 1.1.8 (legacy) | Spring Boot 3.4.x, 3.5.x | Last 1.x line. Use only if locked to Boot 3.x. |
| Spring AI BOM | 1.0.9 (legacy) | Spring Boot 3.4.x | Oldest maintained 1.0.x. Avoid for new projects. |
| Spring Boot | 4.1.0 | Java 17+ (we use 21) | Verified from `spring-boot-starter-parent-4.1.0.pom`: `<java.version>17</java.version>`. |
| Spring Boot | 3.5.x (legacy) | Java 17+ | Still in OSS support window but not recommended for new 2026 projects. |
| `spring-ai-starter-model-deepseek` | 2.0.0 | Spring AI BOM 2.0.0 | Available since 1.0.0-RC1; GA in 2.0.0. |
| `spring-ai-starter-mcp-client` | 2.0.0 | Spring AI BOM 2.0.0 | MCP annotations moved to `org.springframework.ai.mcp.annotation.*` in 2.0. |
| Vue | 3.5.40 | Element Plus ^2.x (peer `vue ^3.3.7`) | Satisfies Element Plus peer dep. |
| Element Plus | 2.14.3 | Vue ^3.3.7 | Verified via `npm view element-plus peerDependencies`. |
| Vite | 8.2.0 | `@vitejs/plugin-vue` ^5 \|\| ^6 \|\| ^7 \|\| ^8 | Verified via `npm view @vitejs/plugin-vue peerDependencies`. |
| `@vitejs/plugin-vue` | 6.0.8 | Vite ^5 \|\| ^6 \|\| ^7 \|\| ^8, Vue ^3.2.25 | Same source. |
| Node.js | 22 LTS | Vite 8.x | Node 20 also works (maintenance). Node 18 EOL'd April 2025. |
| DeepSeek API | `https://api.deepseek.com/v1` | OpenAI-compatible chat completions endpoint | Verified reachable: `POST /v1/chat/completions` returns standard OpenAI error format on auth failure. Models: `deepseek-chat` (V3), `deepseek-reasoner` (R1). Spring AI 2.0 docs also reference `deepseek-v4-pro` / `deepseek-v4-flash` enums for newer models — verify availability in your DeepSeek account. |
## Configuration Snippets
### `application.yml` (backend)
### Frontend SSE consumption (Vue 3 + native EventSource alternative)
## Sources
### HIGH confidence (verified via Maven Central / npm registries + Context7 official docs)
- **Maven Central `spring-ai-bom` maven-metadata.xml** — verified latest = 2.0.0, release = 2.0.0, `lastUpdated` = 2026-06-12. Confirms all 1.0.x / 1.1.x / 2.0.x versions.
- **Maven Central `spring-boot-starter-parent` maven-metadata.xml** — verified latest = 4.1.0. Confirms Spring Boot 4.x is GA.
- **Maven Central `spring-ai-starter-model-deepseek` maven-metadata.xml** — verified 2.0.0 GA available. Available since 1.0.0-RC1.
- **Maven Central `spring-boot-starter-parent-4.1.0.pom`** — verified `<java.version>17</java.version>` (Java 21 satisfies).
- **npm registry `vue`** — latest = 3.5.40.
- **npm registry `element-plus`** — latest = 2.14.3; peer deps `{ vue: '^3.3.7' }`.
- **npm registry `vite`** — latest = 8.2.0; `previous` = 7.3.6.
- **npm registry `@vitejs/plugin-vue`** — latest = 6.0.8; peer deps `{ vite: '^5.0.0 || ^6.0.0 || ^7.0.0 || ^8.0.0', vue: '^3.2.25' }`.
- **npm registry `@element-plus/icons-vue`** — latest = 2.3.2.
- **DeepSeek API endpoint probe** — `https://api.deepseek.com/v1/chat/completions` returns standard OpenAI-compatible auth error format. Confirms endpoint and OpenAI-compatible shape.
- **Context7 `/websites/spring_io_spring-ai_reference`** — verified:
### MEDIUM confidence (single source or older docs)
- **DeepSeek model names `deepseek-v4-pro` / `deepseek-v4-flash`** — listed in Spring AI 2.0 reference enum but not yet confirmed in DeepSeek's own marketing (couldn't fetch api-docs.deepseek.com due to fetch restrictions). If these aren't available in your DeepSeek account, fall back to `deepseek-chat` / `deepseek-reasoner`. LOW confidence on availability; HIGH confidence that `deepseek-chat` and `deepseek-reasoner` work.
### LOW confidence
- **DeepSeek pricing tier / rate limits for teaching use** — not researched in depth. Assume free tier or pay-as-you-go is sufficient for demo. Verify at https://platform.deepseek.com/ before classroom use.
- **Spring Boot 4.0 → 4.1 breaking changes** — not enumerated here. Review https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide if migrating an existing app. For greenfield, no migration concern.
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
