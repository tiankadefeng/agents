# 架构研究

**领域：** Spring AI + DeepSeek Agent 设计模式教学案例库（7 种模式教学平台）
**研究日期：** 2026-08-04
**置信度：** HIGH（Spring AI 1.0.x 文档已通过 Context7 验证；Spring AI examples 仓库已直接查阅；DeepSeek 集成为原生支持）

## 标准架构

### 系统总览

```
┌──────────────────────────────────────────────────────────────────────┐
│                          Browser (Vue 3 + Element Plus)               │
│  ┌────────────┐  ┌────────────────┐  ┌──────────────────────────┐    │
│  │ Pattern    │  │ Chat / Q&A      │  │ Reasoning Trace Panel    │    │
│  │ Selector   │  │ Panel           │  │ (events rendered live)   │    │
│  └─────┬──────┘  └────────┬────────┘  └───────────▲──────────────┘    │
│        │                  │                       │                    │
│        │       ┌──────────▼─────────┐             │                    │
│        │       │  useSSEStream      │  events ────┘                    │
│        │       │  (fetch+Readable)  │                                  │
│        │       └──────────┬─────────┘                                  │
└────────┼──────────────────┼──────────────────────────────────────────┘
         │ HTTP              │ SSE (text/event-stream)
         │ GET /patterns     │ POST /api/agent/execute
         ▼                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│                  Spring Boot Backend (Java 21)                        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  API Layer (REST Controllers)                                    │  │
│  │  ┌──────────────────┐  ┌──────────────────────────────────────┐ │  │
│  │  │ PatternController│  │ AgentController (SseEmitter)          │ │  │
│  │  │ GET /api/patterns│  │ POST /api/agent/execute -> SseEmitter  │ │  │
│  │  └──────────────────┘  └──────────────────────────────────────┘ │  │
│  └─────────────────────────────┬───────────────────────────────────┘  │
│                                │                                       │
│  ┌─────────────────────────────▼───────────────────────────────────┐  │
│  │  Agent Layer (Pattern Registry + 7 Patterns)                    │  │
│  │  ┌──────────────────┐  ┌──────────────────────────────────┐    │  │
│  │  │ AgentPattern     │  │ AgentRegistry (Spring-injected   │    │  │
│  │  │   (interface)    │◄─┤ Map<String, AgentPattern>)        │    │  │
│  │  └──────────────────┘  └──────────────────────────────────┘    │  │
│  │           ▲                                                     │  │
│  │  ┌────────┴───────┬────────────┬───────────┬─────────────┐    │  │
│  │  │CoTPattern  SelfAskPattern  │ReActPattern│PlanExecute  │... │  │
│  │  │              │              │            │Pattern      │    │  │
│  │  └──────────────┴──────────────┴────────────┴─────────────┘    │  │
│  └─────────────────────────────┬───────────────────────────────────┘  │
│                                │ uses                                  │
│  ┌─────────────────────────────▼───────────────────────────────────┐  │
│  │  Tool Layer                                                      │  │
│  │  ┌──────────────────────┐  ┌────────────────────────────────┐   │  │
│  │  │ ToolRegistry         │  │ MCP ToolCallbackProvider        │   │  │
│  │  │ (built-in @Tool)     │  │ (spring-ai-mcp-client-starter)  │   │  │
│  │  └──────────────────────┘  └────────────────────────────────┘   │  │
│  └─────────────────────────────┬───────────────────────────────────┘  │
│                                │                                       │
│  ┌─────────────────────────────▼───────────────────────────────────┐  │
│  │  LLM Layer (Spring AI ChatClient + DeepSeekChatModel)            │  │
│  │  ChatClient.builder(chatModel).defaultAdvisors(...).build()      │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
                  ┌──────────────────────────┐
                  │  DeepSeek API            │
                  │  (api.deepseek.com)      │
                  └──────────────────────────┘
                                │
                                ▼ (optional, for MCP)
                  ┌──────────────────────────┐
                  │  External MCP Servers    │
                  │  (filesystem, search...) │
                  └──────────────────────────┘
```

### 组件职责

| 组件 | 职责 | 典型实现 |
|------|------|----------|
| **PatternController** | 列出可用模式及其元数据（不调用 LLM） | `@RestController`，从 `AgentRegistry` 返回 `List<PatternInfo>` |
| **AgentController** | 接收执行请求，打开 SSE 流，分发给对应模式，回流事件 | `@RestController`，使用 `SseEmitter`（若用 WebFlux 则为 `Flux<ServerSentEvent>`） |
| **AgentPattern**（接口） | 7 种模式的通用契约；产生 `Flux<AgentEvent>` | Java 接口 + 密封事件层次结构 |
| **AgentRegistry** | 启动时发现模式，按 id 查找 | Spring 自动注入 `List<AgentPattern>` 到一个 `@Component`，构建 `Map<String, AgentPattern>` |
| **模式实现**（CoT、Self-Ask、ReAct、Plan-Execute、ToT、Reflexion、Role-playing） | 每个类封装一种模式的算法，发射推理事件 | 每个模式一个 `@Component` 类，位于 `agent.patterns.<name>` 包 |
| **ToolRegistry** | 聚合内置工具 + MCP 工具，暴露为 `ToolCallback[]` | Spring bean，收集 `@Tool` 注解方法 + `ToolCallbackProvider` |
| **内置工具** | 天气、计算器、时间 - 用于 ReAct 演示的样例工具 | `@Component` 类，含 `@Tool` 注解方法 |
| **MCP client** | 连接外部 MCP 服务器，将其工具暴露为 `ToolCallback`s | `spring-ai-mcp-client-spring-boot-starter` + `SyncMcpToolCallbackProvider` |
| **ChatClientConfig** | 构建共享 `ChatClient`（系统提示、默认工具、advisors） | `@Configuration` 含 `ChatClient` bean；注入 `DeepSeekChatModel` |
| **前端 SSE 客户端** | 打开 POST SSE 连接，解析事件流，分发到 store | `composables/useSSEStream.ts`，使用 `fetch()` + `ReadableStream` + `TextDecoder`（**不用** `EventSource` - 见反模式） |
| **Pinia store** | 持有会话状态：当前模式、消息列表、trace 事件 | `stores/agentSession.ts` |
| **推理 trace 面板** | 按时间顺序渲染事件，按类型差异化样式 | `components/ReasoningTrace.vue`，遍历 store 中的事件 |

## 推荐项目结构

### 后端（Spring Boot, Java 21）

```
src/main/java/com/atguigu/gulimall/agents/
├── AgentsApplication.java              # @SpringBootApplication entry point
├── config/
│   ├── ChatClientConfig.java           # ChatClient bean (DeepSeek + default tools + advisors)
│   ├── DeepSeekConfig.java             # (optional) custom DeepSeekChatOptions beans
│   ├── McpClientConfig.java            # MCP server connections (when MCP enabled)
│   └── WebConfig.java                  # CORS for Vite dev server (localhost:5173)
├── api/
│   ├── PatternController.java          # GET /api/patterns
│   ├── AgentController.java            # POST /api/agent/execute (SseEmitter)
│   ├── dto/
│   │   ├── PatternInfo.java            # record(id, displayName, description, features)
│   │   ├── AgentRequest.java           # record(patternId, question, options)
│   │   └── ErrorResponse.java          # record(code, message)
│   └── GlobalExceptionHandler.java     # @RestControllerAdvice
├── agent/
│   ├── core/
│   │   ├── AgentPattern.java           # interface (see §Agent Abstraction)
│   │   ├── AgentContext.java           # record passed to execute()
│   │   ├── AgentEvent.java             # sealed event interface
│   │   ├── events/                     # ReasoningEvent, ToolCallEvent, ... (records implementing AgentEvent)
│   │   └── AgentRegistry.java          # @Component wrapping Map<String, AgentPattern>
│   └── patterns/
│       ├── cot/CoTPattern.java
│       ├── selfask/SelfAskPattern.java
│       ├── react/ReActPattern.java
│       ├── planexecute/PlanExecutePattern.java
│       ├── tot/TreeOfThoughtsPattern.java
│       ├── reflexion/ReflexionPattern.java
│       └── roleplay/RolePlayingPattern.java
├── tool/
│   ├── ToolRegistry.java               # @Component; merges built-in + MCP tools
│   ├── builtin/
│   │   ├── WeatherTool.java            # @Component with @Tool methods
│   │   ├── CalculatorTool.java
│   │   └── TimeTool.java
│   └── mcp/
│       └── McpToolAdapter.java         # adapts ToolCallbackProvider -> ToolCallback[]
└── streaming/
    └── SseEventEmitter.java            # helper: AgentEvent -> ServerSentEvent.Builder

src/main/resources/
├── application.yml                     # DeepSeek config, MCP config, server.port
├── application-dev.yml                 # dev profile (CORS, logging)
└── prompts/                            # (optional) externalized prompt templates
    ├── cot.st
    ├── react-system.st
    └── ...
```

### 前端（Vue 3 + Vite + Element Plus）

```
src/
├── main.ts                             # app entry, Element Plus, Pinia
├── App.vue
├── router/
│   └── index.ts                        # routes: /, /pattern/:id
├── api/
│   ├── client.ts                       # axios instance (baseURL, error interceptor)
│   ├── patterns.ts                     # getPatterns()
│   └── agent.ts                        # executePattern() -> returns ReadableStream
├── composables/
│   ├── useSSEStream.ts                 # fetch+ReadableStream SSE parser (POST + body)
│   └── useAgentSession.ts              # wraps store + SSE composable
├── stores/
│   └── agentSession.ts                 # Pinia: currentPattern, messages, traceEvents[]
├── views/
│   ├── HomeView.vue                    # pattern selection grid + chat layout
│   └── PatternDetailView.vue           # (optional) per-pattern deep dive
├── components/
│   ├── PatternSelector.vue             # Element Plus cards for 7 patterns
│   ├── ChatPanel.vue                   # question input + send button
│   ├── MessageBubble.vue               # user/assistant message rendering (markdown)
│   ├── ReasoningTrace.vue              # chronological event list (the core teaching UI)
│   ├── events/
│   │   ├── ReasoningEventCard.vue
│   │   ├── ToolCallEventCard.vue
│   │   ├── ToolResultEventCard.vue
│   │   ├── SubQuestionEventCard.vue
│   │   ├── PlanEventCard.vue
│   │   ├── StepEventCard.vue
│   │   └── FinalAnswerEventCard.vue
│   └── layout/
│       └── AppHeader.vue
└── types/
    ├── agent.ts                        # PatternInfo, AgentRequest, AgentEvent (mirror of backend)
    └── sse.ts                          # SSEEvent<T> wrapper
```

### 结构设计理由

**后端：**
- **`agent/core/` 与 `agent/patterns/` 分离**：契约（接口、事件、注册表）是稳定的；7 个模式实现频繁变动。分离后，"新增一个模式"只需在 `patterns/<name>/` 中操作一个文件。
- **每个模式一个包**：每个模式有自己的提示词、辅助 record，可能还有内部类。包级别隔离让模式可作为整体阅读。
- **`tool/` 与 `agent/` 平级**：模式依赖工具，反过来不成立。`ToolRegistry` 是内置工具 + MCP 工具合并的唯一位置，模式无需关心工具来源。
- **`api/dto/` records**：Java 21 record 提供不可变 DTO，样板代码最少；与 Spring AI 自身风格一致（如官方示例中的 `OrchestratorResponse`、`RefinedResponse` record）。
- **`streaming/SseEventEmitter`**：隔离 `AgentEvent` -> `ServerSentEvent` 的映射。模式发射领域事件；helper 处理 SSE 接线。模式可不依赖 SSE 进行测试。

**前端：**
- **`composables/useSSEStream.ts`**：SSE 解析逻辑非平凡（手动 chunk 解析、事件名路由、错误/重连）。集中管理让组件保持简洁，解析器可独立测试。
- **每种事件类型一个事件卡片组件**：每种推理事件类型有差异化可视化（工具调用显示参数 + 结果；计划显示编号步骤；子问题显示缩进的后续追问）。按类型分组件让渲染逻辑保持小巧。
- **Pinia store 作为唯一数据源**：`ChatPanel`（输入）和 `ReasoningTrace`（输出）从同一 store 读取，流式事件可实时更新 trace 面板，无需 prop 透传。
- **`types/agent.ts` 镜像后端 DTO**：端到端类型安全；后端新增事件类型时，前端可在编译时发现 schema 偏移。

## 架构模式

### 模式 1：Strategy + Spring Plugin Registry（用于 7 种 agent 模式）

**是什么：** 定义通用 `AgentPattern` 接口。每个模式是一个实现该接口的 `@Component`。Spring 自动收集所有实现到 `List<AgentPattern>`（或以 bean 名为 key 的 `Map<String, AgentPattern>`），注入 `AgentRegistry`。控制器在运行时按 id 查找模式。

**何时使用：** 当有 N 个可在运行时互换的算法，且希望新增算法是单文件操作、无需修改现有代码时。

**权衡：**
- 优点：可插拔扩展（新模式 = 新 `@Component` 类，自动注册）；可隔离测试；无 `if/else` 链；Spring 处理装配。
- 缺点：所有模式在启动时加载（7 个没问题，1000 个就浪费）；模式必须共享同一 `AgentEvent` 词汇表。

**示例：**
```java
// agent/core/AgentPattern.java
public interface AgentPattern {
    String id();                  // "react", "cot", ...
    String displayName();         // "ReAct (推理 + 行动)"
    String description();         // shown in pattern selector
    Set<PatternFeature> features(); // [TOOLS, MULTI_STEP, ...] for UI hints
    Flux<AgentEvent> execute(AgentContext ctx);
}

// agent/core/AgentRegistry.java
@Component
public class AgentRegistry {
    private final Map<String, AgentPattern> patterns;

    public AgentRegistry(List<AgentPattern> all) {              // Spring injects every @Component
        this.patterns = all.stream().collect(
            Collectors.toUnmodifiableMap(AgentPattern::id, p -> p));
    }

    public AgentPattern require(String id) {
        AgentPattern p = patterns.get(id);
        if (p == null) throw new NoSuchPatternException(id);
        return p;
    }

    public List<PatternInfo> list() {
        return patterns.values().stream()
            .map(p -> new PatternInfo(p.id(), p.displayName(), p.description(), p.features()))
            .toList();
    }
}

// agent/patterns/react/ReActPattern.java
@Component
public class ReActPattern implements AgentPattern {
    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    public ReActPattern(ChatClient chatClient, ToolRegistry toolRegistry) { /* ... */ }

    @Override public String id()          { return "react"; }
    @Override public String displayName() { return "ReAct (推理 + 行动)"; }
    @Override public Set<PatternFeature> features() { return Set.of(TOOLS, MULTI_STEP); }

    @Override
    public Flux<AgentEvent> execute(AgentContext ctx) {
        return Flux.create(sink -> {
            // ... ReAct loop: stream thoughts -> emit ReasoningEvent
            //                on tool call -> emit ToolCallEvent, run tool, emit ToolResultEvent
            //                final answer -> emit FinalAnswerEvent
            sink.complete();
        });
    }
}
```

### 模式 2：Sealed Event Hierarchy（用于推理 trace 流）

**是什么：** 将 SSE 事件 schema 建模为 Java 密封接口 + record 子类型。模式代码发射强类型事件；一个 helper 将它们序列化为 SSE 线路格式。前端有 TypeScript 可辨识联合类型镜像 Java 层次结构。

**何时使用：** 当流承载语义上不同的事件类型、UI 需差异化渲染时。替代临时的 `Map<String,Object>` 负载。

**权衡：**
- 优点：Java（模式匹配）和 TypeScript（可辨识联合）中的穷尽 `switch` 在编译时捕获缺失的事件处理器；新事件类型 = 新 record，由 UI 选择是否支持。
- 缺点：需维护更多类型；序列化需 `@JsonTypeInfo`/`@JsonSubTypes`（或在 SSE helper 中手动分发）。

**示例：**
```java
// agent/core/AgentEvent.java
public sealed interface AgentEvent permits
        ReasoningEvent, ToolCallEvent, ToolResultEvent,
        SubQuestionEvent, PlanEvent, StepStartEvent, StepCompleteEvent,
        FinalAnswerEvent, ErrorEvent {

    String patternId();
    int step();
    Instant ts();
}

// agent/core/events/ToolCallEvent.java
public record ToolCallEvent(
        String patternId, int step, Instant ts,
        String toolName, Map<String, Object> arguments
) implements AgentEvent {}

// streaming/SseEventEmitter.java
@Component
public class SseEventEmitter {
    public ServerSentEvent<String> toSse(AgentEvent ev) {
        return ServerSentEvent.<String>builder()
            .id(ev.ts().toString())
            .event(ev.getClass().getSimpleName())    // "ToolCallEvent"
            .data(toJson(ev))
            .build();
    }
}
```

```typescript
// types/agent.ts (frontend mirror)
export type AgentEvent =
  | { type: 'ReasoningEvent';     patternId: string; step: number; ts: string; content: string }
  | { type: 'ToolCallEvent';      patternId: string; step: number; ts: string; toolName: string; arguments: Record<string, unknown> }
  | { type: 'ToolResultEvent';    patternId: string; step: number; ts: string; toolName: string; result: string; isError: boolean }
  | { type: 'SubQuestionEvent';   patternId: string; step: number; ts: string; question: string }
  | { type: 'PlanEvent';          patternId: string; step: number; ts: string; steps: string[] }
  | { type: 'StepStartEvent';     patternId: string; step: number; ts: string; description: string }
  | { type: 'StepCompleteEvent';  patternId: string; step: number; ts: string; result: string }
  | { type: 'FinalAnswerEvent';   patternId: string; step: number; ts: string; content: string }
  | { type: 'ErrorEvent';         patternId: string; step: number; ts: string; code: string; message: string };
```

### 模式 3：Tool Registry 合并内置 + MCP 工具

**是什么：** 单个 `ToolRegistry` bean 暴露统一的 `List<ToolCallback>`，由以下两部分组装：(a) `@Component` 类上 `@Tool` 注解的方法，(b) 任何 `ToolCallbackProvider` bean（即 Spring AI MCP starter 注册的）。模式向 registry 请求工具，从不询问"工具来自哪里"。

**何时使用：** 当有多个工具来源（演示用内置、扩展用 MCP）需要透明组合时。

**权衡：**
- 优点：模式不知道也不关心 `weather` 是内置还是来自 MCP 服务器；用户可更换 MCP 服务器而无需触碰模式代码。
- 缺点：内置与 MCP 服务器之间的工具名冲突 - 如需教学清晰度，可通过命名空间前缀缓解（`builtin:weather` vs `mcp:my-server:weather`）（该决策可延后）。

**示例：**
```java
// tool/ToolRegistry.java
@Component
public class ToolRegistry {
    private final List<ToolCallback> all;

    public ToolRegistry(
            List<Object> toolBeans,                       // @Component classes with @Tool methods
            @Autowired(required = false) List<ToolCallbackProvider> providers) {
        var built = toolBeans.stream()
            .flatMap(b -> Arrays.stream(ToolCallbacks.from(b)))
            .toList();
        var mcp = providers.stream()
            .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
            .toList();
        this.all = Stream.concat(built.stream(), mcp.stream()).toList();
    }

    public List<ToolCallback> all() { return all; }

    public List<ToolCallback> forPattern(AgentPattern p) {
        // Patterns can opt out of tools (CoT, Self-Ask don't need them)
        return p.features().contains(PatternFeature.TOOLS) ? all : List.of();
    }
}
```

### 模式 4：ChatClient + Advisor Chain（Spring AI 规范）

**是什么：** 从 `DeepSeekChatModel` 构建一个共享 `ChatClient`，配置默认 advisors（`ToolCallingAdvisor` 用于 ReAct 风格的自动工具调用，可选 `MessageChatMemoryAdvisor` 用于多轮对话）。每个模式调用 `chatClient.prompt().user(...).tools(...).stream()` 并消费 `Flux<ChatClientResponse>`。

**何时使用：** 始终使用 - 这是 Spring AI 的惯用法。直接使用 `ChatModel` 留给高级场景（如为显式教学 ReAct 机制而自定义工具调用循环）。

**权衡：**
- 优点：原生流式、自动工具调用生命周期、通过 `.entity()` 实现结构化输出、advisor 组合处理横切关注点。
- 缺点：为显式教学 ReAct 的*内部*循环，自动 `ToolCallingAdvisor` 可能隐藏太多 - 见"模式 5"。

**示例：**
```java
// config/ChatClientConfig.java
@Configuration
public class ChatClientConfig {
    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolRegistry toolRegistry) {
        return builder
            .defaultSystem("You are a teaching assistant demonstrating agent patterns.")
            .defaultTools(toolRegistry.all().toArray(ToolCallback[]::new))
            .build();
    }
}
```

### 模式 5：手动工具调用循环（用于教学 ReAct 机制）

**是什么：** 特别针对 ReAct 模式，绕过 `ToolCallingAdvisor`（设置 `toolCallingAdvisorAutoRegister(false)`），使用 `ToolCallingManager` + `MessageAggregator` 手动驱动工具调用循环。在每次循环迭代中发射 `ReasoningEvent`/`ToolCallEvent`/`ToolResultEvent`，让学习者清楚看到 ReAct 的实际行为。

**何时使用：** 当模式的*核心*就是暴露循环机制时（ReAct、Plan-and-Execute）。对工具使用只是附带 patterns（如 Role-playing），使用自动 advisor。

**权衡：**
- 优点：教学上忠实 - SSE 流展示字面意义的 Think -> Act -> Observe 循环。
- 缺点：代码更多；需通过 `MessageAggregator` 处理多 chunk 工具调用（已从 Spring AI 文档验证）。

**示例（草稿 - 完整代码在 ReAct 模式阶段）：**
```java
Flux<AgentEvent> execute(AgentContext ctx) {
    return Flux.create(sink -> {
        var mgr = ToolCallingManager.builder().build();
        var options = ToolCallingChatOptions.builder()
            .toolCallbacks(toolRegistry.forPattern(this).toArray(ToolCallback[]::new))
            .build();
        var prompt = new Prompt(List.of(new UserMessage(ctx.question())), options);

        driveLoop(prompt, options, mgr, sink);   // recursive helper
    });
}

private void driveLoop(Prompt prompt, ChatOptions options,
                       ToolCallingManager mgr, FluxSink<AgentEvent> sink) {
    var agg = new AtomicReference<ChatResponse>();
    new MessageAggregator()
        .aggregate(chatClient.prompt().messages(prompt.getInstructions())
            .options(options).advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
            .stream().chatResponse(),
            agg::set)
        .doOnNext(r -> sink.next(new ReasoningEvent(id(), step++, Instant.now(),
                              r.getResult().getOutput().getText())))
        .blockLast();

    if (agg.get().hasToolCalls()) {
        var result = mgr.executeToolCalls(prompt, agg.get());
        for (var tc : agg.get().getResult().getOutput().getToolCalls()) {
            sink.next(new ToolCallEvent(id(), step++, Instant.now(),
                          tc.name(), tc.arguments()));
        }
        // ... emit ToolResultEvent from result.conversationHistory()
        driveLoop(new Prompt(result.conversationHistory(), options), options, mgr, sink);
    } else {
        sink.next(new FinalAnswerEvent(id(), step, Instant.now(),
                       agg.get().getResult().getOutput().getText()));
        sink.complete();
    }
}
```

## 数据流

### 请求流（问题 -> SSE 流 -> 推理事件 -> 前端）

```
[User types question "北京明天天气?" + selects ReAct]
    │
    ▼
[ChatPanel.vue]  ->  agentSession.run(patternId="react", question)
    │
    ▼
[useSSEStream.ts]  fetch("/api/agent/execute", { method: POST, body: JSON })
    │                                            ▼ Content-Type: application/json
    │                                            ▼ Accept: text/event-stream
    ▼
[AgentController.execute(req) -> SseEmitter]
    │
    ▼
[AgentRegistry.require("react")] -> ReActPattern
    │
    ▼
[ReActPattern.execute(ctx) -> Flux<AgentEvent>]
    │
    ├──(1) ChatClient.stream(prompt)  ->  DeepSeek API (streaming)
    │        ▲
    │        │ tool calls returned in stream chunks
    │        ▼
    ├──(2) MessageAggregator merges chunks  ->  ReasoningEvent emitted
    │
    ├──(3) ToolCallingManager.executeToolCalls()
    │        │
    │        ├──► ToolRegistry -> built-in WeatherTool OR MCP tool
    │        │                      │
    │        │                      ▼
    │        │              External API call (e.g., weather service)
    │        │
    │        └──► ToolResultEvent emitted
    │
    ├──(4) Loop back to (1) with updated conversation history
    │
    └──(5) No more tool calls -> FinalAnswerEvent emitted -> Flux completes
    │
    ▼
[SseEventEmitter.toSse(ev)]  ->  ServerSentEvent{event:"ToolCallEvent", data:"{...}"}
    │
    ▼  (HTTP chunked, text/event-stream)
[fetch ReadableStream in browser]
    │
    ▼  TextDecoder + SSE parser (split on \n\n, parse `event:` and `data:`)
[useSSEStream.ts -> push to agentSession.traceEvents[]]
    │
    ▼  Pinia reactivity
[ReasoningTrace.vue re-renders]  ->  new ToolCallEventCard appended live
    │
    ▼
[FinalAnswerEvent arrives] ->  ChatPanel shows final answer, send button re-enabled
```

### 状态管理（前端）

```
                          ┌────────────────────────────────┐
                          │  agentSession (Pinia store)    │
                          │  ──────────────────────────    │
                          │  - currentPatternId: string    │
                          │  - messages: Message[]         │
                          │  - traceEvents: AgentEvent[]   │
                          │  - isStreaming: boolean        │
                          │  - error: string | null        │
                          └─────────────┬──────────────────┘
                                        │
            ┌───────────────────────────┼────────────────────────────┐
            │                           │                            │
            ▼                           ▼                            ▼
   [PatternSelector]            [ChatPanel]                  [ReasoningTrace]
   reads currentPatternId       writes currentPatternId       reads traceEvents
   writes currentPatternId      calls run() action            reads isStreaming
                                                                for live cursor

   Action: run(patternId, question)
     ├─ sets isStreaming = true, clears error
     ├─ appends user Message to messages
     ├─ calls useSSEStream('/api/agent/execute', { patternId, question })
     ├─ for each parsed event: traceEvents.push(event)
     ├─ on FinalAnswerEvent: appends assistant Message
     └─ finally: isStreaming = false
```

### 关键数据流

1. **模式发现流：** `GET /api/patterns` -> `PatternController` -> `AgentRegistry.list()` -> 返回 `List<PatternInfo>`（不调用 LLM；仅 bean 元数据）。前端 `PatternSelector` 渲染卡片。
2. **推理流（核心教学流）：** `POST /api/agent/execute` -> `AgentController` -> `AgentRegistry.require(id)` -> `pattern.execute(ctx)` 返回 `Flux<AgentEvent>` -> 每个事件映射为 `ServerSentEvent` -> 前端解析并追加到 `traceEvents`。**这是平台的教学核心** - 学习者实时观察模式"思考"。
3. **工具调用流（仅 ReAct）：** 模式在 LLM 流中检测到工具调用 -> `ToolCallingManager.executeToolCalls()` -> `ToolRegistry` 解析工具（内置或 MCP）-> 工具执行 -> 结果追加到对话历史 -> 下一次 LLM 流迭代。每个阶段发射自己的事件，trace 展示完整的 Act -> Observe 循环。
4. **MCP 工具发现流（启动时一次性）：** `spring-ai-mcp-client-spring-boot-starter` 连接配置的 MCP 服务器 -> `ToolCallbackProvider` bean 填充 -> `ToolRegistry` 构造器注入并与内置合并。无每请求运行时开销。

## 扩展性考量

| 规模 | 架构调整 |
|------|----------|
| 1 用户（本地教学） | 单 Spring Boot 进程，无 DB，内存状态。默认配置即可。 |
| 10-50 并发用户（课堂） | 增加 Tomcat 线程（`server.tomcat.threads.max=200`）；若长连接 SSE 较多，考虑 `Flux<ServerSentEvent>` + WebFlux。无需 DB。 |
| 100+ 并发用户 | 超出 PROJECT.md 范围（教学项目）。如需：加 Redis 存会话状态、DeepSeek 调用排队、按 IP 限流。 |

### 扩展优先级

1. **第一瓶颈：DeepSeek API 限流，不是后端。** 通过请求排队和向前端发送清晰错误事件来缓解。对教学场景，这不太可能成为问题。
2. **第二瓶颈：长连接 SSE 占用 Tomcat 线程。** 若出现，将控制器切换为 WebFlux（`spring-boot-starter-webflux` + `Flux<ServerSentEvent>`）- Spring AI 的 `Flux` 流式天然适配。**决策可延后** - 从 `SseEmitter` 起步（更简单、更熟悉），仅在课堂负载需要时切换。

## 反模式

### 反模式 1：使用原生 `EventSource` 建立 SSE 连接

**常见做法：** 使用 `new EventSource("/api/agent/execute?patternId=react&question=...")`，因为这是"标准"SSE API。
**为何错误：** `EventSource` 仅支持 `GET` 请求，无请求体、无自定义头。agent 端点需要 `POST` + JSON body（问题 + 选项）。URL 编码问题在长输入、多行、非 ASCII 内容时会出问题 - 这些在教学演示中都很常见。
**正确做法：** 使用 `fetch()`，设 `method: 'POST'`、`body: JSON.stringify(req)`、`Accept: text/event-stream`，然后读取 `response.body.getReader()` 并手动解析 SSE 文本协议（按 `\n\n` 分割，解析 `event:`/`data:` 行，JSON 解析 data）。将其封装在 `composables/useSSEStream.ts` 中。这是知名模式；如不愿手写解析器，可用 `@microsoft/fetch-event-source` 等库。

### 反模式 2：用 `if/else` 或 `switch` 硬编码模式分发

**常见做法：** 在控制器中：`switch(patternId) { case "cot": return cotPattern.execute(...); case "react": return reactPattern.execute(...); ... }`
**为何错误：** 每个新模式都需要修改控制器。控制器知道每个模式。测试需要 mock 所有模式。7 个模式后 switch 会变得难以维护。
**正确做法：** 使用 Strategy + Plugin Registry 模式（§架构模式 #1）。控制器调用 `registry.require(patternId).execute(ctx)`。新增模式 = 新增 `@Component` 类。控制器永不改动。

### 反模式 3：模式类发射原始字符串 / 无类型 Map 作为事件

**常见做法：** 模式直接发射 `Map<String, Object>` 或预格式化的 JSON 字符串，"为保持灵活性"。
**为何错误：** 前端没有可编译的 schema。新增字段会静默地既不破坏任何东西也破坏一切。trace UI 会长出 `if (ev.type === 'tool_call' && ev.toolName && ev.args)` 意大利面条。新事件类型仅在运行时被发现。
**正确做法：** 使用密封事件层次结构（§架构模式 #2）。每个事件是实现了 `AgentEvent` 的 Java `record`。前端镜像为 TypeScript 可辨识联合。两种语言的穷尽 `switch` 在编译时捕获缺失处理器。

### 反模式 4：在 ReAct 模式中让 `ToolCallingAdvisor` 自动管理工具

**常见做法：** 对 ReAct，直接调用 `chatClient.prompt().user(q).tools(...).stream().content()` 让 `ToolCallingAdvisor` 处理，将流视为不透明文本。
**为何错误：** ReAct 作为*教学*模式的全部意义就是暴露 Think -> Act -> Observe 循环。自动 advisor 将工具调用隐藏在流内；trace 面板只会显示最终答案，违背教学目的。
**正确做法：** 对 ReAct（和 Plan-and-Execute），禁用自动注册（`toolCallingAdvisorAutoRegister(false)`），用 `ToolCallingManager` + `MessageAggregator` 手动驱动循环（§模式 5）。在每个阶段发射 `ReasoningEvent` -> `ToolCallEvent` -> `ToolResultEvent`。对非教学性模式（Role-playing），自动 advisor 没问题。

### 反模式 5：一个巨型 "AgentController" 同时管 SSE、工具和提示词

**常见做法：** `AgentController` 直接构造提示词、调用 `ChatClient`、管理工具、格式化 SSE 事件。
**为何错误：** 违反单一职责；不可测试；每次模式变更都要改控制器。
**正确做法：** 控制器只做 HTTP <-> 领域转换（解析请求、通过 registry 查找模式、返回连接到模式 `Flux<AgentEvent>` 的 `SseEmitter`）。模式拥有提示词构造和工具选择。`SseEventEmitter` 拥有 SSE 序列化。`ToolRegistry` 拥有工具装配。

### 反模式 6：将 DeepSeek API key 写入 `application.yml`

**常见做法：** `spring.ai.deepseek.api-key=sk-xxxxxxxx` 提交到 git。
**为何错误：** 密钥泄漏；轮换需改代码。
**正确做法：** 使用环境变量替换：`application.yml` 中写 `spring.ai.deepseek.api-key: ${DEEPSEEK_API_KEY}`。在 README 中说明所需环境变量。可选支持 `application-local.yml`（gitignore）存放个人密钥。

## 集成点

### 外部服务

| 服务 | 集成模式 | 说明 |
|------|----------|------|
| **DeepSeek API** | 原生 Spring AI：`spring-ai-starter-model-deepseek` + `spring.ai.deepseek.api-key` 属性。自动配置 `DeepSeekChatModel` bean。 | 已验证：Spring AI 1.0.x 将 DeepSeek 作为一级提供商支持。通过 `Flux<ChatResponse>` 支持流式。模型：`deepseek-chat`（通用）、`deepseek-reasoner`（R1 风格推理，适合 CoT 演示）。 |
| **外部 MCP 服务器** | `spring-ai-mcp-client-spring-boot-starter` + `spring.ai.mcp.client.sse.connections.*` 下的配置。自动注册 `ToolCallbackProvider` bean。 | MCP 在本项目中是**可选的** - Phase 7+。启用时，MCP 服务器的工具与内置工具合并到同一 `ToolRegistry`。为简化起见，从 SSE transport（基于 HTTP）起步；STDIO transport 可用于本地子进程 MCP 服务器。 |
| **Vite dev server**（前端） | CORS：`WebConfig.java` 在 dev profile 中将 `http://localhost:5173` 加入允许来源。 | 仅 dev 需要；生产环境中前端由 Spring Boot 静态资源或反向代理提供。 |

### 内部边界

| 边界 | 通信 | 说明 |
|------|------|------|
| `api` ↔ `agent` | `AgentController` 调用 `AgentRegistry.require(id)`，再调用 `pattern.execute(ctx)` 返回 `Flux<AgentEvent>`。 | 控制器从不直接实例化模式。 |
| `agent` ↔ `tool` | 模式通过 DI 接收 `ToolRegistry`；调用 `toolRegistry.forPattern(this)` 获取其被允许使用的 `ToolCallback[]`。 | 模式声明 `features()`（如 `TOOLS`）；registry 据此过滤。CoT/Self-Ask 获得空工具列表。 |
| `agent` ↔ LLM | 模式通过 DI 接收共享 `ChatClient`。手动工具循环时，模式还使用 `ToolCallingManager`（Spring AI 类型，非项目内部）。 | 所有模式共享一个 `ChatClient` bean - 让 DeepSeek 配置集中化。 |
| `tool` ↔ MCP | `ToolRegistry` 注入 `List<ToolCallbackProvider>`（可选）。MCP starter 提供这些 bean。 | 模式不直接调用 `McpClient` - 抽象保持完整。 |
| 前端 `composables` ↔ `stores` | `useSSEStream` 与 UI 无关；通过回调将解析的事件推入 Pinia store。Store 是组件的唯一数据源。 | SSE composable 可隔离测试（传入 mock fetch）。 |

## 构建顺序（对路线图的影响）

阶段排序时必须遵守以下依赖关系。每个阶段的前置条件已明确列出。

### Phase 1：骨架（无模式、无工具）

**构建：**
- Spring Boot 应用启动，classpath 含 `spring-ai-starter-model-deepseek`。
- `application.yml` 含 `spring.ai.deepseek.api-key: ${DEEPSEEK_API_KEY}` + `spring.ai.deepseek.chat.model=deepseek-chat`。
- `ChatClientConfig` 产出 `ChatClient` bean。
- 一个简单的 `@RestController` `GET /api/ping`，调用 `chatClient.prompt("say hi").call().content()` 并返回字符串 - 验证 DeepSeek 连通性。
- Vue 3 + Vite + Element Plus 应用渲染单页，调用 `/api/ping` 并显示结果。CORS 已配置。
- `useSSEStream.ts` composable 存在但未使用（或连接到 `/api/ping-stream` 测试端点，流式发射 3 个简单事件）。

**为何第一：** 其他一切都依赖 DeepSeek 端到端工作。如果 API key、模型名或网络有问题，应在 Phase 1 发现，而不是 Phase 5。

**前置条件用于：** Phase 2（需要 `ChatClient` bean）、Phase 3（需要 Vue + SSE composable）。

### Phase 2：Agent 抽象（暂不实现模式）

**构建：**
- `AgentPattern` 接口。
- `AgentEvent` 密封接口 + 所有 record 子类型。
- `AgentRegistry`（注入 `List<AgentPattern>` - 当前为空）。
- `PatternController` 从 `registry.list()` 返回空列表。
- `AgentController` 含 `POST /api/agent/execute`，查找模式，未找到则返回 404（暂无真实模式可运行）。
- `SseEventEmitter` helper。
- 前端 `types/agent.ts` 镜像 DTO。

**为何第二：** 契约不存在就无法新增模式。Registry、控制器、SSE 接线是一次性构建、永不触碰的基础设施。

**前置条件用于：** Phase 3（模式实现 `AgentPattern`）。

### Phase 3：第一个模式 - CoT（最简单，无工具）

**构建：**
- `CoTPattern` `@Component` - 单次 `chatClient.prompt().user(q).stream()` 调用，每个 chunk 包装为 `ReasoningEvent`，最终 chunk 作为 `FinalAnswerEvent`。
- 前端 `PatternSelector`、`ChatPanel`、`ReasoningTrace` 及 `ReasoningEventCard` + `FinalAnswerEventCard`。

**为何 CoT 第一：** 无工具、无循环、无结构化输出。以最少活动部件端到端验证整个事件流。如果 SSE 事件出现在 trace 面板，架构就成立。

**前置条件用于：** Phase 5（ReAct 需要 trace UI 工作）。

### Phase 4：工具层（仅内置工具，无 MCP）

**构建：**
- `ToolRegistry`（不含 MCP 注入 - `@Autowired(required = false)`）。
- `WeatherTool`、`CalculatorTool`、`TimeTool` 作为 `@Component` 类，含 `@Tool` 方法。
- 用一次性测试端点验证，直接调用某工具。

**为何在 ReAct 之前：** ReAct 依赖工具。先构建工具层，ReAct 直接消费即可。

**前置条件用于：** Phase 5（ReAct）。

### Phase 5：ReAct 模式（验证工具层 + 手动循环）

**构建：**
- `ReActPattern` 含手动工具调用循环（§模式 5）。
- 前端 `ToolCallEventCard`、`ToolResultEventCard`。

**为何这个顺序：** 这是第一个练习工具和手动 `ToolCallingManager` 循环的模式。是风险最高的模式；早期构建可在工具集成 bug 被 5 个模式覆盖之前暴露它们。

**前置条件用于：** Phase 6（其他模式可复用此处建立的模式）。

### Phase 6：剩余模式（可并行）

**构建（任意顺序，全部独立）：**
- `SelfAskPattern`（子问题，无工具 - 使用 `SubQuestionEvent`）。
- `PlanExecutePattern`（使用 `PlanEvent`、`StepStartEvent`、`StepCompleteEvent`；可能用工具）。
- `TreeOfThoughtsPattern`（多分支 - 使用推理流的并行 `Flux.merge`）。
- `ReflexionPattern`（evaluator-optimizer 循环 - 仿照 Spring AI 示例；评审轮次使用 `ReasoningEvent`）。
- `RolePlayingPattern`（多 ChatClient 角色；可用自动 `ToolCallingAdvisor`，因为循环机制不是教学点）。

**为何最后：** 每个都独立。可任意顺序或并行。此时契约已验证；这些只是变体。

**前置条件用于：** 无（终态）。

### Phase 7：MCP 集成（可选，扩展性）

**构建：**
- 添加 `spring-ai-mcp-client-spring-boot-starter` 依赖。
- 在 `application.yml` 的 `spring.ai.mcp.client.sse.connections.*` 下配置一个演示用 MCP 服务器。
- `McpClientConfig`（如需自定义处理器 - `@McpToolListChanged` 等）。
- `ToolRegistry` 已注入 `ToolCallbackProvider` - 无模式代码改动。
- 前端 `ToolCallEventCard` 显示徽章标识工具来源（内置 vs MCP 服务器名）- 教学辅助。

**为何最后：** MCP 是"扩展性"故事，非核心教学。内置工具已演示工具使用。MCP 增添真实世界风味而不改变架构。

### 构建-顺序依赖图

```
Phase 1 (Skeleton: DeepSeek + Vue + ping)
   │
   ▼
Phase 2 (Agent Abstraction: interface, registry, SSE wiring)
   │
   ├─────────────────┐
   ▼                 ▼
Phase 3 (CoT)    Phase 4 (Tool Layer)
   │                 │
   └────────┬────────┘
            ▼
      Phase 5 (ReAct)  ← highest-risk pattern, validates tool layer
            │
            ▼
      Phase 6 (Self-Ask, Plan-Execute, ToT, Reflexion, Role-playing)  ← parallelizable
            │
            ▼
      Phase 7 (MCP Integration)  ← optional, extensibility
```

### 即插即用新增模式（架构红利）

Phase 2 之后，新增模式只需：

1. 创建 `agent/patterns/<name>/<Name>Pattern.java`，实现 `AgentPattern`。
2. 标注 `@Component`。
3. 实现 `execute()` 返回 `Flux<AgentEvent>`，使用现有事件类型（如确实需要，可在密封层次结构中新增事件 record - 需修改 `AgentEvent.java` permits 子句 + 前端联合类型，但这是罕见且有意的）。
4. 完成。无需改控制器、无需改 registry、无需改配置。Spring 自动发现；`/api/patterns` 下次请求即包含；前端 `PatternSelector` 渲染它。

这是架构最重要的属性，也是 Strategy + Plugin Registry 模式不可妥协的原因。

## 来源

- [Spring AI Reference (1.0.x) - DeepSeek Chat Model](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html) - 验证 DeepSeek 配置属性、模型名、流式支持。**HIGH 置信度。**
- [Spring AI Reference - ChatClient stream() return values](https://docs.spring.io/spring-ai/reference/api/chatclient.html) - 验证 `Flux<String>` / `Flux<ChatResponse>` / `Flux<ChatClientResponse>` 选项。**HIGH 置信度。**
- [Spring AI Reference - Tools / ToolCallingManager](https://docs.spring.io/spring-ai/reference/api/tools.html) - 验证使用 `MessageAggregator` + `ToolCallingManager` + `toolCallingAdvisorAutoRegister(false)` 的手动工具调用循环。对 ReAct 教学模式至关重要。**HIGH 置信度。**
- [Spring AI Reference - Advisors](https://docs.spring.io/spring-ai/reference/api/advisors.html) - 验证 `CallAdvisor` / `StreamAdvisor` 接口，用于自动工具执行的 `ToolCallingAdvisor`。**HIGH 置信度。**
- [Spring AI Reference - MCP Client](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html) - 验证 SSE transport 配置、`ToolCallbackProvider` 自动注册。**HIGH 置信度。**
- [Spring AI Examples - agentic-patterns/](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns) - 直接查阅 `OrchestratorWorkers.java`、`EvaluatorOptimizer.java`、`ChainWorkflow.java` 源码。确认"普通 Java 类 + ChatClient + process()/loop() 方法 + record DTO"的惯用法。**HIGH 置信度。**
- [Spring AI Examples - evaluator-optimizer README](https://github.com/spring-projects/spring-ai-examples/blob/main/agentic-patterns/evaluator-optimizer/README.md) - 确认模式：双 LLM（generator + evaluator）、含 PASS/NEEDS_IMPROVEMENT/FAIL 枚举的结构化 `EvaluationResponse` record、递归 `loop()` 方法。直接适用于 Reflexion 模式。**HIGH 置信度。**
- [Anthropic - Building Effective Agents](https://www.anthropic.com/research/building-effective-agents) - 被 Spring AI 示例引用；定义工作流模式词汇（chain、parallelization、routing、orchestrator-workers、evaluator-optimizer）。映射到本项目的 7 种模式。**HIGH 置信度。**
- [MDN - Server-Sent Events (EventSource limitations)](https://developer.mozilla.org/en-US/docs/Web/API/EventSource) - 确认 `EventSource` 仅支持 GET、无自定义头/body。证明 `fetch` + `ReadableStream` 方法的合理性。**HIGH 置信度（成熟浏览器 API）。**

---
*架构研究用于：Spring AI + DeepSeek Agent 设计模式教学案例库*
*研究日期：2026-08-04*
