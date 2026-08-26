# 技术栈调研

**领域：** Agent 设计模式教学案例库 (Spring AI + DeepSeek + Vue 3, SSE 流式推理可视化)
**调研日期：** 2026-08-04
**置信度：** HIGH（版本已通过 Maven Central + npm 仓库验证；API 表面已通过 Context7 Spring AI 2.0 参考文档验证）

## 执行建议

后端基于 **Spring Boot 4.1.0 + Spring AI 2.0.0 + Java 21**，前端采用 **Vue 3.5.40 + Element Plus 2.14.3 + Vite 8.2.0**。DeepSeek 集成使用专用的 **`spring-ai-starter-model-deepseek`** starter（而非 OpenAI 兼容 shim）。通过 **Spring MVC 返回 `Flux<ServerSentEvent>`** 实现流式输出——无需 WebFlux，无需 SseEmitter 样板代码。

这是 2026 年 8 月当前的 GA 技术栈。Spring AI 2.0.0 于 2026-06-12 发布到 Maven Central（通过 `maven-metadata.xml` 的 `lastUpdated` 验证）。它是首个面向 Spring Boot 4.x 的稳定版 Spring AI，而 Spring Boot 4.x 本身也已 GA（Spring Boot 4.1.0 是最新版本线）。Spring AI 2.0 也是 DeepSeek、MCP、工具调用和结构化输出首次同时达到 API 成熟度的版本——这使其成为全新 agent 教学项目的自然基线。

## 推荐技术栈

### 核心技术

| 技术 | 版本 | 用途 | 推荐理由 |
|------------|---------|---------|-----------------|
| **JDK** | Java 21 (LTS) | 运行时 + 编译目标 | 已在 `pom.xml` 中。LTS 支持到 2028 年 9 月。Spring Boot 4.x 要求 17+，因此 21 是安全的现代 LTS。Java 25 是较新的 LTS，但 21 具有更广泛的库支持，且与现有项目骨架匹配。 |
| **Spring Boot** | 4.1.0 | 应用框架、自动配置、内嵌 Tomcat | 最新 GA 版本线（4.x）。Spring AI 2.0.x 所需（"Spring AI 2.0.x 兼容 Spring Boot 4.0.x 和 4.1.x 版本"——已在 Spring AI 参考文档中验证）。选 4.1 而非 4.0，因为 4.1 是当前补丁版本线，包含 bug 修复；两者都与 Spring AI 2.0 兼容。 |
| **Spring AI** | 2.0.0 (BOM) | Agent 框架：ChatClient、工具调用、MCP、记忆、结构化输出 | 最新 GA。2026-06-12 发布（Maven Central `lastUpdated`）。首个稳定版，包含成熟的 `@Tool`/`ToolCallback` API、`@McpTool`/`@McpToolParam` 注解栈、`MessageChatMemoryAdvisor` 以及专用的 DeepSeek starter。使用 BOM（`spring-ai-bom:2.0.0`）统一管理所有模块版本。 |
| **Spring AI DeepSeek starter** | `spring-ai-starter-model-deepseek`（由 BOM 管理） | DeepSeek 聊天模型集成 | 原生 DeepSeek 集成：`DeepSeekChatModel`、`DeepSeekChatOptions`、`DeepSeekApi.ChatModel` 枚举。通过 `spring.ai.deepseek.*` 属性自动配置。优先于指向 DeepSeek base URL 的 OpenAI starter——参见"不应使用的内容"。 |
| **Spring AI MCP client starter** | `spring-ai-starter-mcp-client`（由 BOM 管理） | MCP 工具服务器消费（项目需求：支持 MCP） | 自动配置支持 stdio / HTTP / Streamable HTTP 传输的 MCP 客户端。与 `SyncMcpToolCallbackProvider` 配合，将 MCP 服务器工具暴露给 `ChatClient`。已在 Spring AI 2.0 参考文档中验证。 |
| **Vue** | 3.5.40 | 前端框架（Composition API） | 最新稳定版 3.x（npm `vue@latest`）。Composition API 是新项目的默认选择。3.5.x 相比 3.4 增加了 `useTemplateRef`、响应式 props 解构和 SSR 改进。保持在 3.x（而非 4.x）——Vue 4 目前不存在稳定版。 |
| **Element Plus** | 2.14.3 | UI 组件库（Vue 3） | 最新稳定版（npm `element-plus@latest`）。Peer 依赖 `vue ^3.3.7`——被 3.5.40 满足。中文管理/教学 UI 的标准选择；丰富组件（`el-tabs`、`el-input`、`el-collapse`、`el-steps`）直接对应 7 种模式选择器 + 推理步骤展示需求。 |
| **Vite** | 8.2.0 | 前端开发服务器 + 打包器 | 最新稳定版（npm `vite@latest` = 8.2.0；`previous` 标签 = 7.3.6）。Vite 8 是当前版本线；`@vitejs/plugin-vue@6.0.8` peer 支持 Vite `^5 \|\| ^6 \|\| ^7 \|\| ^8`。新项目使用 8.x——无需从旧版 Vite 进行破坏性迁移。 |
| **@vitejs/plugin-vue** | 6.0.8 | 为 Vite 提供 Vue 3 SFC 编译 | 最新稳定版。编译 `.vue` 文件所必需。与 Vite 8.x peer 兼容。 |
| **Maven** | 3.9+（构建工具） | 后端构建 | 已选定（现有 `pom.xml`）。Spring Boot 4.x parent POM 兼容 Maven 3.9+。无需考虑 Gradle——项目已确定使用 Maven。 |

### 辅助库

| 库 | 版本 | 用途 | 使用场景 |
|---------|---------|---------|-------------|
| `spring-boot-starter-web` | 4.1.0（通过 parent） | Servlet 栈、MVC 控制器、内嵌 Tomcat | 始终使用。主要 Web 栈。同时处理 REST 端点和 SSE 流式传输（通过响应式返回类型）。 |
| `spring-boot-starter-webflux` | （不要添加） | - | 不要作为 starter 添加。`spring-ai-starter-model-deepseek` 已经传递性引入 `reactor-core` + `reactor-netty` 以支持 `Flux`。添加 `spring-boot-starter-webflux` 会触发 Spring Boot 的 WebFlux 自动配置并与 MVC 冲突。 |
| `@element-plus/icons-vue` | 2.3.2 | Element Plus UI 的图标集 | 始终使用（与 Element Plus 配对）。用于模式选择器和推理可视化器中的按钮、标签页、状态指示器。 |
| `reactor-core` | （通过 Spring AI 传递引入） | 用于流式的 `Flux` / `Mono` | 自动引入。在控制器中用于返回 `Flux<ServerSentEvent<...>>`。无需显式依赖。 |
| `spring-ai-starter-model-openai` | （不要添加） | - | 跳过。使用专用的 DeepSeek starter。OpenAI starter 仅在您同时需要 GPT 模型时才相关。 |
| `axios` | ^1.7+ | 用于非流式 API 调用的 HTTP 客户端 | 可选。对于 SSE 消费，使用浏览器原生 `EventSource`（或对基于 POST 的 SSE 使用 `fetch` + ReadableStream）。axios 仅用于一次性调用，如获取可用模式列表。 |
| TypeScript | ^5.6+ | 前端类型安全 | 推荐。`@vitejs/plugin-vue` peer 依赖 `typescript: *`。Vue SFC + `<script setup lang="ts">` 是 2026 年的默认选择。仅在团队排斥 TS 时跳过。 |

### 开发工具

| 工具 | 用途 | 备注 |
|------|---------|-------|
| **Spring Boot Maven Plugin** | `mvn spring-boot:run`、fat-jar 打包 | 添加 `<build><plugins><plugin>org.springframework.boot:spring-boot-maven-plugin</plugin></plugins></build>`。插件版本从 `spring-boot-starter-parent:4.1.0` 继承。 |
| **Node.js 22 LTS** | Vite 的前端运行时 | Vite 8.x 所需。Node 22 是当前 LTS。Node 20 可用但处于维护阶段。 |
| **`create-vue`** | 前端脚手架 | `npm create vue@latest`——官方 Vue 3 + Vite 脚手架。选择 TS + Composition API。然后 `npm install element-plus @element-plus/icons-vue`。 |
| **Spring Initializr** | 后端脚手架（替代手动 pom.xml） | https://start.spring.io——选择 Spring Boot 4.1.0、Java 21、依赖：Web、Spring AI OpenAI（然后手动替换为 DeepSeek starter）。用于验证正确的 pom.xml 形状。 |
| **DeepSeek API key** | LLM 访问 | 设置 `DEEPSEEK_API_KEY` 环境变量。从 https://platform.deepseek.com/ 获取。免费额度足以用于教学演示。 |

## 安装

### 后端（Maven）

`pom.xml`（替换当前骨架）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.atguigu.gulimall</groupId>
    <artifactId>agents</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>2.0.0</spring-ai.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- DeepSeek chat model (native, not OpenAI-compatible) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-deepseek</artifactId>
        </dependency>

        <!-- MCP client support (for external tool servers) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 前端（npm）

```bash
# Scaffold Vue 3 + Vite + TS project in ./frontend
npm create vue@latest frontend
#   (select: TypeScript = Yes, Vue Router = optional, Pinia = optional, Vitest = optional)

cd frontend
npm install

# UI library
npm install element-plus @element-plus/icons-vue

# (Optional) HTTP client for non-streaming calls
npm install axios

# Dev tooling (optional but recommended)
npm install -D @types/node
```

### 运行

```bash
# Backend
export DEEPSEEK_API_KEY=sk-xxxxx
mvn spring-boot:run

# Frontend (separate terminal)
cd frontend && npm run dev
```

## 考虑过的替代方案

| 推荐方案 | 替代方案 | 何时使用替代方案 |
|-------------|-------------|-------------------------|
| **Spring AI 2.0.0** | Spring AI 1.1.8 | 仅当您必须停留在 Spring Boot 3.5.x（例如现有企业技术栈）。1.1.x 是最后的 3.x 版本线；获得 bug 修复但无新功能。对于全新的 2026 年项目，2.0 是正确选择。 |
| **Spring AI 2.0.0** | LangChain4j | 当您想使用非 Spring 框架或需要 Spring AI 缺少的功能时。对于本项目，PROJECT.md 规定使用 Spring AI，且更合适（Spring 原生惯用法、ChatClient、MCP、BOM 管理）。LangChain4j 成熟但引入了并行抽象层。 |
| **Spring AI 2.0.0** | Spring AI Alibaba | 当您特别需要阿里巴巴的 agent 抽象（Graph、Flow）或 DashScope 模型时。对于教授纯设计模式来说过于重量级；增加了一层魔法，会掩盖所教的模式。 |
| **`spring-ai-starter-model-deepseek`** | 指向 `https://api.deepseek.com/v1` 的 `spring-ai-starter-model-openai` | 当您希望在不更改代码的情况下切换 LLM 提供商时。对于本项目，原生 DeepSeek starter 提供正确的默认模型名、适当的错误处理和 DeepSeek 特定选项（例如 `deepseek-reasoner` 推理内容）。OpenAI 兼容 shim 可用但会损失保真度。 |
| **Spring Boot 4.1.0** | Spring Boot 3.5.x | 当您的组织尚未批准 4.x 时。强制使用 Spring AI 1.1.x。适合教学，但您将落后一个主版本。 |
| **Java 21** | Java 25 | 当您想要最新的 LTS（2025 年 9 月）且工具链支持时。Spring Boot 4.1 在 17+ 上运行，因此 25 可用。但 21 更经受过实战检验，且已在 pom.xml 中——没有迁移理由。 |
| **Spring MVC + `Flux<ServerSentEvent>`** | Spring WebFlux（完全响应式） | 当您需要端到端响应式管道（背压、非阻塞 DB 驱动等）时。对于本项目，无 DB、无高并发——MVC 的响应式返回类型支持已足够，且保持心智模型更简单。 |
| **Spring MVC + `Flux<ServerSentEvent>`** | Spring MVC + `SseEmitter` | 当您需要对单个 SSE 事件 ID、注释或心跳的细粒度控制时。对于大多数教学场景，`Flux<ServerSentEvent>` 更惯用且样板代码减少约 5 倍。仅在发现特定需求时使用 SseEmitter。 |
| **Vue 3.5.40** | React / Svelte | 当团队专长决定时。PROJECT.md 规定使用 Vue 3。 |
| **Element Plus 2.14.3** | Naive UI / Ant Design Vue / Arco Design | 当您偏好不同的视觉风格时。Element Plus 是中文 Vue 3 管理 UI 的事实标准，拥有最好的中文文档——对这一教学受众很重要。 |
| **Vite 8.2.0** | Vite 7.3.6（previous 标签） | 当关键插件尚未为 Vite 8 更新时。`@vitejs/plugin-vue@6.0.8` 已支持 Vite 8，因此使用 8。 |
| **Maven** | Gradle（Kotlin DSL） | 当团队偏好 Gradle 时。PROJECT.md 规定使用 Maven（现有 pom.xml）。 |

## 不应使用的内容

| 避免 | 原因 | 改用 |
|-------|-----|-------------|
| **LangChain4j** | 项目约束：PROJECT.md 规定使用 Spring AI。LangChain4j 是并行生态系统；混合两者会造成混乱。Spring AI 2.0 对本项目所需的一切（工具、MCP、记忆、结构化输出）具有功能对等性。 | `spring-ai-starter-model-deepseek` + `spring-ai-starter-mcp-client` |
| **`spring-boot-starter-webflux`**（作为主要栈） | 引入 Netty、禁用 Tomcat 自动配置、强制完全响应式心智模型。对于无 DB 且约 10 个路由的应用来说不必要。Spring MVC 6+ 原生支持 `Flux` 返回类型。 | `spring-boot-starter-web`（MVC）+ 从流式端点返回 `Flux<ServerSentEvent<...>>`。`reactor-core` 通过 Spring AI 传递性引入。 |
| **OpenAI 兼容的 DeepSeek 集成**（`spring-ai-starter-model-openai` + 自定义 `base-url`） | 可用但会丢失 DeepSeek 特定功能：原生 `deepseek-reasoner` reasoning_content 字段、适当的模型枚举、DeepSeek 错误语义。在代码和实际 API 之间增加抽象层。 | `spring-ai-starter-model-deepseek`——一等公民、自动配置、包含模型枚举。 |
| **数据库（JPA / H2 / PostgreSQL / MongoDB）** | PROJECT.md 明确超出范围："数据库持久化 - 教学项目，对话无需保存，重启丢失可接受"。添加一个会使表面积翻倍，教学价值为零。 | 使用内存 `MessageWindowChatMemory`（Spring AI 内置）存储会话内的聊天历史。 |
| **Spring Security** | PROJECT.md："用户认证/登录 - 教学演示用途，无需多用户隔离"。增加 CSRF、session、CORS 复杂性，与 SSE 冲突。 | 无。开放端点。（对于本地教学工具可接受。） |
| **WebSocket** | SSE 是单向服务器->客户端，正是流式 LLM 输出所需的。WebSocket 是双向的，增加了帧/握手复杂性，无任何收益。 | 通过 `Flux<ServerSentEvent<...>>` 或 `SseEmitter` 使用 SSE。 |
| **`PromptChatMemoryAdvisor`** | 在 Spring AI 2.0 中已移除。仍出现在旧教程中。 | `MessageChatMemoryAdvisor`（已在 Spring AI 2.0 升级说明中验证）。 |
| **旧 artifact ID**（`spring-ai-openai-spring-boot-starter`） | 在 Spring AI 2.0 M3+ 中已重命名。旧 ID 无法解析。 | `spring-ai-starter-model-openai` / `spring-ai-starter-model-deepseek`（新命名约定：`spring-ai-starter-model-*`）。 |
| **旧 MCP 导入**（`org.springaicommunity.mcp.annotation.*`） | 在 Spring AI 2.0 中已迁移到 `org.springframework.ai.mcp.annotation.*`。旧包无法编译。 | 使用 Spring AI 2.0 迁移指南中的新包路径。 |
| **Vue 2 / Options API** | Vue 2 于 2023 年 12 月 EOL。Options API 在 Vue 3 中仍可用，但 Composition API + `<script setup>` 是 2026 年默认选择，也是学生应该学习的。 | Vue 3.5 + `<script setup lang="ts">` + Composition API。 |
| **Webpack / vue-cli** | 多年前已被 Vite 取代。开发服务器较慢，HMR 较慢。 | Vite 8.x。 |
| **`io.modelcontextprotocol.sdk` 下的 `mcp-spring-webflux` / `mcp-spring-webmvc`** | 在 Spring AI 2.0 中已迁移到 `org.springframework.ai` group。旧坐标将无法解析。 | 使用 BOM 管理的坐标（无需版本号）。 |

## 按变体划分的技术栈模式

### 主要模式：从 Spring MVC 进行 SSE 流式传输

**对所有 7 个 agent 模式端点使用此模式。** Spring MVC 6+ 支持从 `@RestController` 方法直接返回响应式类型（`Flux`/`Mono`）。Spring AI 的 `chatModel.stream(prompt)` 返回 `Flux<ChatResponse>`；将其映射为 `Flux<ServerSentEvent<String>>` 以获得类型化的 SSE 事件。

```java
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ChatClient chatClient;  // built from DeepSeekChatModel

    public AgentController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @PostMapping(value = "/react", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> react(@RequestBody AgentRequest req) {
        return chatClient.prompt()
            .user(req.question())
            .tools(new WeatherTool(), new CalculatorTool())  // ReAct tools
            .stream()
            .chatResponse()
            .map(cr -> ServerSentEvent.<String>builder()
                .event("token")                              // event type
                .data(cr.getResult().getOutput().getText())
                .build())
            .concatWith(Flux.just(ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build()));
    }
}
```

**为何无需 WebFlux starter 也能工作：** `spring-boot-starter-web`（MVC）包含 Spring 6 的 `ReactiveTypeHandler`，它将 `Flux` 适配为流式 Servlet 响应。`reactor-core` 通过 Spring AI 传递性引入。无需 Netty，无需完全响应式管道——只是一个流式响应。

### 变体：当需要细粒度 SSE 控制时（SseEmitter）

仅在需要事件 ID、重试提示或注释帧时使用：

```java
@GetMapping(value = "/cot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter cot(@RequestParam String question) {
    SseEmitter emitter = new SseEmitter(0L);  // no timeout
    chatClient.prompt().user(question).stream()
        .chatResponse()
        .subscribe(
            cr -> {
                try {
                    emitter.send(SseEmitter.event()
                        .id(String.valueOf(cr.hashCode()))
                        .name("token")
                        .data(cr.getResult().getOutput().getText()));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            },
            emitter::completeWithError,
            emitter::complete
        );
    return emitter;
}
```

新代码优先使用 `Flux<ServerSentEvent>` 形式。仅在超出其能力时使用 SseEmitter。

### 变体：DeepSeek `reasoner` 模型（用于 Reflexion / Tree of Thoughts）

DeepSeek-R1（`deepseek-reasoner`）在单独的 `reasoning_content` 字段中返回推理 token。Spring AI 2.0 的 `DeepSeekChatModel` 通过 `AssistantMessage` 元数据暴露此字段。在教授 Reflexion 时使用（模型的推理轨迹就是反思本身）：

```properties
spring.ai.deepseek.chat.model=deepseek-reasoner
```

```java
// Access reasoning_content (the <think> tokens)
ChatResponse resp = chatClient.prompt().user(q).call().chatResponse();
String reasoning = resp.getResult().getOutput().getMetadata().get("reasoning_content");
```

### 变体：MCP 外部工具（用于带外部服务器的 ReAct）

```yaml
# application.yml
spring:
  ai:
    mcp:
      client:
        stdio:
          servers-configuration: classpath:mcp-servers.json
```

```java
// MCP tools auto-exposed as ToolCallbackProvider
@PostMapping("/react-mcp")
public Flux<ServerSentEvent<String>> reactMcp(@RequestBody AgentRequest req,
                                               ToolCallbackProvider mcpTools) {
    return chatClient.prompt()
        .user(req.question())
        .tools(mcpTools)            // MCP-provided tools
        .tools(new CalculatorTool()) // built-in tools
        .stream()
        .chatResponse()
        .map(cr -> /* SSE mapping */);
}
```

## 版本兼容性

| 组件 | 版本 | 兼容于 | 备注 |
|-----------|---------|-----------------|-------|
| Spring AI BOM | 2.0.0 | Spring Boot 4.0.x, 4.1.x | 已验证："Spring AI 2.0.x is compatible with Spring Boot versions 4.0.x and 4.1.x"（Spring AI 参考文档，getting-started）。 |
| Spring AI BOM | 1.1.8（旧版） | Spring Boot 3.4.x, 3.5.x | 最后的 1.x 版本线。仅在锁定 Boot 3.x 时使用。 |
| Spring AI BOM | 1.0.9（旧版） | Spring Boot 3.4.x | 最旧的维护中 1.0.x。新项目避免使用。 |
| Spring Boot | 4.1.0 | Java 17+（我们使用 21） | 通过 `spring-boot-starter-parent-4.1.0.pom` 验证：`<java.version>17</java.version>`。 |
| Spring Boot | 3.5.x（旧版） | Java 17+ | 仍在 OSS 支持窗口内，但不推荐用于新的 2026 年项目。 |
| `spring-ai-starter-model-deepseek` | 2.0.0 | Spring AI BOM 2.0.0 | 自 1.0.0-RC1 起可用；2.0.0 中 GA。 |
| `spring-ai-starter-mcp-client` | 2.0.0 | Spring AI BOM 2.0.0 | MCP 注解在 2.0 中迁移到 `org.springframework.ai.mcp.annotation.*`。 |
| Vue | 3.5.40 | Element Plus ^2.x（peer `vue ^3.3.7`） | 满足 Element Plus peer 依赖。 |
| Element Plus | 2.14.3 | Vue ^3.3.7 | 通过 `npm view element-plus peerDependencies` 验证。 |
| Vite | 8.2.0 | `@vitejs/plugin-vue` ^5 \|\| ^6 \|\| ^7 \|\| ^8 | 通过 `npm view @vitejs/plugin-vue peerDependencies` 验证。 |
| `@vitejs/plugin-vue` | 6.0.8 | Vite ^5 \|\| ^6 \|\| ^7 \|\| ^8, Vue ^3.2.25 | 同一来源。 |
| Node.js | 22 LTS | Vite 8.x | Node 20 也可用（维护阶段）。Node 18 于 2025 年 4 月 EOL。 |
| DeepSeek API | `https://api.deepseek.com/v1` | OpenAI 兼容的 chat completions 端点 | 验证可达：`POST /v1/chat/completions` 在认证失败时返回标准 OpenAI 错误格式。确认端点和 OpenAI 兼容形状。模型：`deepseek-chat`（V3）、`deepseek-reasoner`（R1）。Spring AI 2.0 文档还引用了较新模型的 `deepseek-v4-pro` / `deepseek-v4-flash` 枚举——请在您的 DeepSeek 账户中验证可用性。 |

## 配置片段

### `application.yml`（后端）

```yaml
spring:
  application:
    name: agents
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        model: deepseek-chat          # V3, default for CoT/Self-Ask/Plan-Execute/Role-play
        temperature: 0.7
        max-tokens: 2048
      # For Reflexion/ToT, override per-request with deepseek-reasoner
    mcp:
      client:
        stdio:
          servers-configuration: classpath:mcp-servers.json
        enabled: true

server:
  port: 8080

logging:
  level:
    org.springframework.ai: INFO
```

### 前端 SSE 消费（Vue 3 + 原生 EventSource 替代方案）

对于基于 `GET` 的 SSE，使用原生 `EventSource`：

```ts
const es = new EventSource(`/api/agent/cot?question=${encodeURIComponent(q)}`)
es.addEventListener('token', (e) => { reasoning.value += e.data })
es.addEventListener('done', () => es.close())
es.onerror = () => es.close()
```

对于基于 `POST` 的 SSE（发送 JSON body），使用 `fetch` + ReadableStream（因为 `EventSource` 仅支持 GET）：

```ts
const resp = await fetch('/api/agent/react', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ question: q })
})
const reader = resp.body!.getReader()
const decoder = new TextDecoder()
// Parse SSE frames from chunks...
```

## 来源

### HIGH 置信度（通过 Maven Central / npm 仓库 + Context7 官方文档验证）

- **Maven Central `spring-ai-bom` maven-metadata.xml**——验证 latest = 2.0.0，release = 2.0.0，`lastUpdated` = 2026-06-12。确认所有 1.0.x / 1.1.x / 2.0.x 版本。
- **Maven Central `spring-boot-starter-parent` maven-metadata.xml**——验证 latest = 4.1.0。确认 Spring Boot 4.x 为 GA。
- **Maven Central `spring-ai-starter-model-deepseek` maven-metadata.xml**——验证 2.0.0 GA 可用。自 1.0.0-RC1 起可用。
- **Maven Central `spring-boot-starter-parent-4.1.0.pom`**——验证 `<java.version>17</java.version>`（Java 21 满足）。
- **npm 仓库 `vue`**——latest = 3.5.40。
- **npm 仓库 `element-plus`**——latest = 2.14.3；peer deps `{ vue: '^3.3.7' }`。
- **npm 仓库 `vite`**——latest = 8.2.0；`previous` = 7.3.6。
- **npm 仓库 `@vitejs/plugin-vue`**——latest = 6.0.8；peer deps `{ vite: '^5.0.0 || ^6.0.0 || ^7.0.0 || ^8.0.0', vue: '^3.2.25' }`。
- **npm 仓库 `@element-plus/icons-vue`**——latest = 2.3.2。
- **DeepSeek API 端点探测**——`https://api.deepseek.com/v1/chat/completions` 返回标准 OpenAI 兼容的认证错误格式。确认端点和 OpenAI 兼容形状。
- **Context7 `/websites/spring_io_spring-ai_reference`**——验证：
  - "Spring AI 2.0.x is compatible with Spring Boot versions 4.0.x and 4.1.x."
  - BOM 用法：`spring-ai-bom:2.0.0`。
  - DeepSeek starter artifact ID：`spring-ai-starter-model-deepseek`。
  - `DeepSeekChatModel` API：`call(Prompt)` -> `ChatResponse`，`stream(Prompt)` -> `Flux<ChatResponse>`。
  - DeepSeek 配置属性：`spring.ai.deepseek.api-key`、`spring.ai.deepseek.chat.model` 等。
  - DeepSeek 模型枚举：`DEEPSEEK_V4_PRO`、`DEEPSEEK_V4_FLASH`、`DEEPSEEK_CHAT`、`DEEPSEEK_REASONER`。
  - ChatClient stream API：`.stream().content()` -> `Flux<String>`，`.stream().chatResponse()` -> `Flux<ChatResponse>`，`.stream().chatClientResponse()` -> `Flux<ChatClientResponse>`。
  - 工具调用：方法上的 `@Tool` / `@ToolParam` 注解；编程式使用 `FunctionToolCallback.builder()`；`ToolCallingAdvisor` 处理循环。
  - MCP client starter：`spring-ai-starter-mcp-client`；`ToolCallbackProvider` 自动配置；`SyncMcpToolCallbackProvider` 用于直接使用。
  - `@McpTool` / `@McpToolParam` 注解用于 MCP 服务器端工具定义。
  - 记忆：`MessageChatMemoryAdvisor`（替换已移除的 `PromptChatMemoryAdvisor`）。
  - 结构化输出：`chatClient.prompt().user(...).call().entity(Class.class)`，可选 `useProviderStructuredOutput()` / `validateSchema()`。
  - 迁移：`spring-ai-openai-spring-boot-starter` -> `spring-ai-starter-model-openai`（artifact 重命名）。
  - 迁移：MCP 导入 `org.springaicommunity.mcp.annotation.*` -> `org.springframework.ai.mcp.annotation.*`。
  - 迁移：`mcp-spring-webflux` / `mcp-spring-webmvc` 从 `io.modelcontextprotocol.sdk` group 迁移到 `org.springframework.ai` group。

### MEDIUM 置信度（单一来源或较旧文档）

- **DeepSeek 模型名 `deepseek-v4-pro` / `deepseek-v4-flash`**——在 Spring AI 2.0 参考枚举中列出，但尚未在 DeepSeek 自己的营销中得到确认（由于抓取限制无法获取 api-docs.deepseek.com）。如果这些在您的 DeepSeek 账户中不可用，回退到 `deepseek-chat` / `deepseek-reasoner`。可用性置信度 LOW；`deepseek-chat` 和 `deepseek-reasoner` 可用的置信度 HIGH。

### LOW 置信度

- **DeepSeek 教学用途的定价层 / 速率限制**——未深入研究。假设免费层或按量付费足以用于演示。在课堂使用前请在 https://platform.deepseek.com/ 验证。
- **Spring Boot 4.0 -> 4.1 破坏性变更**——此处未枚举。如果迁移现有应用，请查看 https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide 。对于全新项目，无迁移顾虑。

---
*技术栈调研用于：Agent 设计模式教学案例库 (Spring AI + DeepSeek + Vue 3)*
*调研日期：2026-08-04*
