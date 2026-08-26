# 陷阱研究

**领域:** Spring AI + DeepSeek Agent 设计模式教学案例库（7 种 agent 设计模式教学项目）
**研究日期:** 2026-08-04
**置信度:** 高（已通过 Context7 对照 Spring AI 官方文档、直接抓取 DeepSeek API 文档、以及 WHATWG SSE 规范进行验证）

## 关键陷阱

### 陷阱 1: Spring AI 版本碎片化与 Breaking Changes

**出了什么问题:**
Spring AI 是一个高速演进的框架（1.0.x、1.1.x、2.0.0 三个并行分支同时维护，加上 SNAPSHOT），不同版本的 API 不兼容。开发者按博客教程抄的代码（可能基于 1.0.0-M5）在自己项目里（2.0.0）编译不过或行为异常。最常见的是：
- `internalToolExecutionEnabled` 字段被移除（1.0.x -> 1.1.x）
- `.functions()` 改名为 `.tools()`（1.0.x -> 1.1.x）
- `ChatClient.tools(t -> t.callbacks(...).context(...))` 改成 `.tools(myCallback).toolContext(...)`（1.1.x -> 2.0.0）
- `.options()` 现在要求传 `ChatOptions.Builder` 而不是已构建的 `ChatOptions` 实例（编译期强制）
- `ChatOptions.builder()` 的方法名从 `withModel()` 变成 `model()`（旧方法可能仍存在但已弃用）
- 1.0 M7 -> M8 升级时，旧的 `tools()` 注册方式会**静默失败**（tool 不被调用，没异常）
- MCP 相关 artifact 的 groupId 从 `io.modelcontextprotocol.sdk` 改成 `org.springframework.ai`（2.0.0）

**原因:**
Spring AI 还在快速迭代，1.0 GA 之前积累的 API 债务在 1.1 / 2.0 集中清理。中文技术博客和教程往往基于 M 版本或 SNAPSHOT，更新跟不上。教学项目开发者最容易照着过时教程抄。

**如何避免:**
1. **锁定一个稳定版本**：推荐 `spring-ai-bom:2.0.0`（已 GA）或 `1.1.8`（如果需要 1.x 兼容）。在 `pom.xml` 用 BOM 管理所有 Spring AI 依赖，**禁止混用版本**。
2. **以官方 upgrade-notes 为准**：https://docs.spring.io/spring-ai/reference/upgrade-notes.html 是迁移的权威来源。所有 API 调用都基于当前锁定版本通过 Context7 查询，**不照抄任何早于当前版本 6 个月的博客代码**。
3. **CI 中加 enforcer 规则**：强制 Spring AI 版本一致性，禁止 SNAPSHOT（除非有特殊原因）。
4. **在 README 中明确写出**：本项目基于 Spring AI X.Y.Z，其他版本的代码示例可能不兼容。
5. **遇到 `withXxx()` 方法**：通常是旧 API，优先找不带 `with` 前缀的新方法。

**警告信号:**
- IDE 提示方法 deprecated
- 编译通过但 tool 调用从未发生（典型 1.0 M7 -> M8 升级症状）
- `IllegalStateException` 关于 ChatOptions builder 类型
- 启动时 Bean 注入失败，提示找不到 `org.springframework.ai.xxx` 包

**应对阶段:**
Phase 1（架构骨架阶段）必须先确定版本，并在 pom.xml 锁定 BOM。后续每个 agent 模式实现阶段都要验证 API 没走偏。

---

### 陷阱 2: DeepSeek 模型名与 Spring AI 配置陷阱

**出了什么问题:**
1. DeepSeek 在 2026 年初推出了 V4 系列模型，模型 ID 是 `deepseek-v4-pro` 和 `deepseek-v4-flash`（V4-Flash-0731）。但很多旧教程和博客还在写 `deepseek-chat`（V3）和 `deepseek-reasoner`（R1）。混用导致行为不一致（reasoning_content 字段时有时无、token 限制不同）。
2. Spring AI 提供两种集成方式：
   - **专用 starter**：`spring-ai-starter-model-deepseek`（推荐，支持 `DeepSeekAssistantMessage.getReasoningContent()`）
   - **OpenAI 兼容模式**：`spring.ai.openai.base-url=https://api.deepseek.com` + `spring.ai.openai.chat.model=deepseek-v4-pro`（不暴露 reasoning_content，丢失思考链）
   教学项目若误用 OpenAI 兼容模式，CoT/Reflexion 等模式的核心"展示推理过程"功能就废了。
3. 配置 key 写错：`spring.ai.deepseek.api-key` vs `DEEPSEEK_API_KEY` 环境变量混用，本地能跑但部署失败。
4. 用 `DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.getValue()` 硬编码枚举，DeepSeek 后续推出新模型时需要改代码。

**原因:**
DeepSeek 自身模型迭代快（V3 -> R1 -> V4），Spring AI 文档里同时出现 `deepseek-chat`、`deepseek-reasoner`、`deepseek-v4-pro`、`deepseek-v4-flash` 多个模型名，但没明确说"V4 是当前推荐"。OpenAI 兼容模式因为通用性强，被很多教程默认采用。

**如何避免:**
1. **统一用专用 starter**：`spring-ai-starter-model-deepseek`，配置 `spring.ai.deepseek.chat.model=deepseek-v4-pro`（教学项目用 pro，效果更直观；省钱场景用 flash）。
2. **模型名集中到配置文件**：不在代码里硬编码 `DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.getValue()`，全部走 `application.yml`。后续切换模型只改配置。
3. **必须验证 reasoning_content 字段**：在 Phase 1 跑通 DeepSeek 时，写一个测试断言 `DeepSeekAssistantMessage.getReasoningContent()` 不为空（针对支持思考的模型），确认走的是专用 starter 而非 OpenAI 兼容模式。
4. **API key 用环境变量**：`spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY}`，README 明确说明需要先 export。
5. **在 README 列出当前可用模型名**：写明"本项目使用 deepseek-v4-pro，若 DeepSeek 后续推出新模型需自行验证兼容性"。

**警告信号:**
- `getReasoningContent()` 永远返回 null 或空字符串（说明走的是 OpenAI 兼容模式）
- 启动报 401 / model not found（模型名拼错或用了已下线的模型 ID）
- 同样 prompt 在不同环境返回不同结构（模型版本漂移）
- 前端展示思考链为空，但最终答案正常

**应对阶段:**
Phase 1（架构骨架阶段）必须解决。后续每个使用思考链的模式（CoT、Reflexion、ReAct）都要验证 reasoning_content 可获取。

---

### 陷阱 3: SSE 流式响应在浏览器被缓冲

**出了什么问题:**
后端 `chatModel.stream(prompt)` 返回 `Flux<ChatResponse>`，前端期待逐 token 看到"打字机效果"。但实际表现是：
- 整段答案最后一次性出现（不是流式）
- 或者每 N 秒跳一次（明显分块）
- 或者本地开发正常，部署到 Nginx 后变卡

**原因:**
1. Spring MVC（非 WebFlux）默认会缓冲响应。`@GetMapping` 返回 `Flux<String>` 但用 servlet 容器，需要 `SseEmitter` 或显式 `text/event-stream` + 响应头。
2. 反向代理（Nginx）默认 `proxy_buffering on`，会把整个响应攒齐再发给浏览器。
3. 浏览器 fetch + ReadableStream 默认等一定字节才触发 onmessage。
4. Java 客户端（WebClient）和 DeepSeek 之间的连接，DeepSeek 会发 `: keep-alive` 注释行保持连接，但 Spring AI 默认未把它们 flush 给前端。
5. 没显式设置 `Content-Type: text/event-stream`，浏览器当普通 HTTP 响应处理。
6. HTTP/2 下某些代理会合并小帧。

**如何避免:**
1. **Controller 显式声明 produces**：
   ```java
   @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
   public Flux<ServerSentEvent<String>> stream(@RequestParam String prompt) { ... }
   ```
2. **Spring Boot 配置**：`server.compression.enabled=false`（SSE 不要压缩），`spring.mvc.async.request-timeout=-1`（不超时）。
3. **Nginx 配置**（如部署）：`proxy_buffering off; proxy_cache off; chunked_transfer_encoding on;` 加响应头 `X-Accel-Buffering: no`。
4. **手动 flush**：用 `SseEmitter` 时在每个 event 后 `emitter.send(...)`，Spring 会自动 flush。
5. **Phase 1 验证标准**：在浏览器 DevTools Network 面板，看到 EventStream 标签下逐条 event 进来（不是一次性 200KB 响应）。
6. **跨层联调**：Phase 1 就要把"前端 -> Nginx（如有）-> 后端 -> DeepSeek"整链路流式验证通过，不能拖到加 agent 模式时才发现。

**警告信号:**
- Network 面板里请求状态长期 pending，最后才完成
- 浏览器收到完整响应而不是 EventStream
- 本地 OK，部署后卡顿
- 后端日志能看到 Flux 逐条 emit，但前端收不到

**应对阶段:**
Phase 1（架构骨架阶段）——这是"打通 SSE 流式返回"的核心验收标准。

---

### 陷阱 4: 前端 EventSource 的限制坑

**出了什么问题:**
1. 用 `EventSource` 发起请求，发现没法带 `Authorization` header（EventSource 不支持自定义 header，只能带 cookie）。
2. EventSource 只支持 GET 请求，但 agent 模式需要 POST 请求体传复杂的 prompt + 历史 + 模式参数。
3. EventSource 自动重连，agent 执行到一半断网恢复后会重头开始，导致重复输出。
4. CORS 配置错误：`withCredentials: true` 时后端必须 `Access-Control-Allow-Origin: <具体域名>`（不能是 `*`）+ `Access-Control-Allow-Credentials: true`，少一个都失败。
5. Vue 3 响应式陷阱：直接 `ref.value += chunk` 在快速流式下性能差（每次触发重新渲染）；用 `reactive` 对象 push 数组也可能丢失响应性。

**原因:**
EventSource 是为"服务器推送新闻订阅"设计的简单 API，不适合需要 POST body 和 auth header 的 agent 场景。Vue 3 响应式系统对高频更新有性能上限。

**如何避免:**
1. **改用 `fetch` + `ReadableStream`**（推荐）：
   ```js
   const resp = await fetch('/api/chat/stream', {
     method: 'POST',
     headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
     body: JSON.stringify({ prompt, mode })
   });
   const reader = resp.body.getReader();
   const decoder = new TextDecoder();
   while (true) {
     const { done, value } = await reader.read();
     if (done) break;
     // 解析 SSE 格式
   }
   ```
2. **手动解析 SSE 格式**：按 `\n\n` 分割 event，按 `data:`、`event:`、`id:` 解析字段。
3. **断线不自动重连**：agent 执行是 stateful 的，重连会重复。在 fetch 错误时提示用户"连接断开，请重新发起"。
4. **CORS 配置**：后端 `@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")`，明写前端域名。
5. **Vue 响应式优化**：
   - 用 `shallowRef` 持有累积字符串，避免深度响应式开销
   - 或者用 `ref<string[]>` 数组 push chunk，渲染时 `join('')`
   - 用 `v-once` 或 `markRaw` 避免不必要的响应式追踪
6. **组件卸载时关闭连接**：`onBeforeUnmount(() => controller.abort())`，否则 SSE 连接泄漏。

**警告信号:**
- 浏览器报错 "Refused to set unsafe header 'Authorization'" 或 CORS 错误
- POST 请求被 EventSource 强制变成 GET
- 网络断开恢复后页面出现重复输出
- 切换 agent 模式时旧连接没关闭，多个流同时往同一个 ref 写
- 累积字符串越长，渲染越卡

**应对阶段:**
Phase 1（架构骨架）建立 fetch + ReadableStream 的 SSE 客户端封装，所有后续 agent 模式复用。

---

### 陷阱 5: ReAct 模式无限循环

**出了什么问题:**
ReAct 模式实现后，agent 在某些问题上陷入死循环：
- 重复调用同一个工具（如反复查同一个城市天气）
- Thought 写得很好，但 Action 一直没收敛到 Final Answer
- 工具返回错误后，agent 不知道换个思路，反复重试同样的调用
- 工具调用参数是 malformed JSON，但 agent 不修正，循环报错

**原因:**
1. 没有 max iterations 硬上限。
2. system prompt 没明确告诉模型"什么时候必须给出 Final Answer"。
3. 工具错误信息没正确反馈给模型（异常被吞，模型只收到空 observation）。
4. 模型把"应该结束"的信号当成"继续思考"。
5. Temperature 太高，模型每次都想出新工具调用方式。
6. 工具描述模糊，模型误以为还有别的可调用工具。

**如何避免:**
1. **硬性 max iterations**（建议 8-10 次）：超过后强制终止，返回"agent 未能收敛"。
   ```java
   for (int i = 0; i < MAX_ITERATIONS; i++) {
       // 一次 ReAct 循环
       if (response.contains("Final Answer:")) break;
   }
   ```
2. **System prompt 明确收敛条件**：写明"当你有足够信息回答用户问题时，必须直接输出 `Final Answer: <答案>`，不要再调用工具"。
3. **工具异常包装成 observation**：`Observation: Tool 'weather' failed with error: <message>. Consider trying a different approach.`
4. **去重保护**：维护已调用工具+参数集合，重复调用直接返回 "Observation: You already called this with the same args. Use the previous result."。
5. **降低 temperature**（0.3-0.5）：ReAct 推理要确定性，不需要发散。
6. **流式展示 Thought/Action/Observation**：教学价值在于让学生看到循环过程，每个 step 都通过 SSE emit 一个结构化事件。
7. **展示失败案例**：教学项目故意准备一个会让 agent 循环的 prompt，让学生看到 max iterations 触发的效果。

**警告信号:**
- 单次请求耗时超过 30 秒
- 日志中同一个工具被调用 3+ 次且参数相同
- Token 消耗异常高
- 最终答案包含 "I kept trying but couldn't..." 类似话术

**应对阶段:**
ReAct 模式实现阶段（Phase 3 左右，CoT 和 Self-Ask 之后）。

---

### 陷阱 6: Plan-and-Execute 计划漂移

**出了什么问题:**
1. Planner 生成的计划太模糊（"Step 1: Research, Step 2: Write"），Executor 不知道具体做什么。
2. 计划太详细（10+ 步骤），中间某步失败后整体崩溃。
3. Executor 执行时偏离原计划（如 Step 2 调用了 Step 4 才该用的工具），但系统没检测。
4. 单步失败没有 replan 机制，整个流程卡死。
5. 步骤间隐式依赖没建模（Step 3 需要 Step 1 的输出，但 Executor 顺序错了）。
6. 计划生成时模型把多步合并成一步（"Step 1: Do everything"），后续无法执行。

**原因:**
Planner 和 Executor 是两次独立 LLM 调用，上下文不共享。Planner 不知道执行难度，Executor 不知道整体计划意图。没有"计划验证"环节，畸形计划直接进入执行。

**如何避免:**
1. **结构化计划格式**：强制 JSON 输出，每步包含 `step_id`、`description`、`expected_output`、`depends_on`。
   ```json
   {"steps": [
     {"id": 1, "description": "...", "expected_output": "...", "depends_on": []}
   ]}
   ```
2. **计划验证**：生成后用一个简短 prompt 让 LLM 检查计划是否合理（"这些步骤是否覆盖了任务？是否有遗漏依赖？"）。
3. **步骤数限制**：3-7 步最佳，超过 7 步要求 Planner 重新拆分。
4. **Replan 机制**：单步失败时，把失败原因 + 已完成步骤反馈给 Planner，让它生成"修订计划"。
5. **展示漂移**：教学项目故意构造一个会导致漂移的 prompt，让学生看到 replan 过程（SSE emit "replan" event）。
6. **执行顺序显式化**：教学界面要可视化 step 依赖图（DAG），不是简单列表。

**警告信号:**
- 计划生成耗时短（< 1 秒）且步骤数 ≤ 2（多半是 Planner 偷懒）
- Executor 频繁调用 Planner 没提到的工具
- 单步失败后整个流程停止，没有 replan
- 计划步骤描述里有"etc."、"and so on"模糊词

**应对阶段:**
Plan-and-Execute 模式实现阶段（建议在 ReAct 之后，因为依赖类似的工具调用基础设施）。

---

### 陷阱 7: Reflexion 卡在自我纠正循环

**出了什么问题:**
1. Agent 反思后给的反馈太泛（"try again with more detail"），下次输出基本一样，无限循环。
2. Self-evaluator 永远打低分（"not good enough"），永远不通过。
3. Self-evaluator 永远打高分，第一次就通过（反射机制形同虚设）。
4. 反思 memory 无限增长，context window 爆炸。
5. 反思内容与原问题无关（模型反思"我应该用不同语言"但用户问的是数学题）。

**原因:**
Self-evaluation 是个本质上不稳定的模式——同一个模型既当选手又当裁判，倾向性难以避免。没有外部 ground truth 时，反思容易陷入"自我感觉"循环。

**如何避免:**
1. **硬性 max reflections**（建议 2-3 次）：教学项目里 3 次反思还没改进就返回当前最佳答案。
2. **结构化反思输出**：要求 JSON 格式：
   ```json
   {"issue": "specific issue", "improvement": "concrete change", "confidence": 0.8}
   ```
3. **反思必须 specific**：system prompt 要求"指出具体哪句话/哪个推理步骤有问题，不能说'整体不够好'"。
4. **Memory 滚动窗口**：只保留最近 2 次反思，旧的丢弃。
5. **分数阈值 + 改进幅度**：通过条件不只是"分数 > 阈值"，还要"本次分数 - 上次分数 < epsilon 时停止"（避免微小改进死循环）。
6. **教学展示**：界面要同时展示"原答案"、"反思"、"修订答案"的对比，让学生看到改进过程。
7. **失败案例**：故意准备一个 Reflexion 改不了的题（如模型知识盲区），展示 max reflections 触发。

**警告信号:**
- 反思次数达到 max_iterations
- 反思内容重复（"need more detail" 出现 3+ 次）
- 分数在 60-65 之间徘徊，每次 +1 分
- context 使用量快速增长

**应对阶段:**
Reflexion 模式实现阶段（建议靠后，需要先有 CoT、ReAct 基础）。

---

### 陷阱 8: Tree of Thoughts 分支爆炸

**出了什么问题:**
1. 分支因子（branching factor）设太大（如 5），3 层就是 125 个 LLM 调用，成本和时间爆炸。
2. Evaluator 评分不稳定，无法可靠剪枝，所有分支都保留。
3. 没有早停条件，即使已找到明显最优解也继续搜索。
4. 状态管理混乱：分支 ID、父节点关系、累积上下文丢失。
5. 教学展示时，前端无法可视化树结构，学生看不出"为什么选这条路径"。
6. 上下文累积：每深一层，prompt 越长（包含所有祖先节点），token 成本二次方增长。

**原因:**
ToT 本质是指数级搜索，没有良好剪枝就必然爆炸。教学项目容易把它实现成"广度优先全展开"，忽略 cost-aware 搜索。

**如何避免:**
1. **小规模参数**：教学项目 branching factor = 2-3，max depth = 3（共 ≤ 27 个节点）。
2. **剪枝显式化**：每层只保留 top-K（K=2），其他剪枝。SSE emit "prune" 事件，前端可视化高亮被剪的分支。
3. **早停**：找到 confidence > 阈值的解立即返回，不再搜索。
4. **共享上下文**：子节点只增量附加"我从这里继续思考"，不重传父节点完整内容。
5. **可视化是教学核心**：用树形图（如 D3.js 或 Element Plus Tree）展示思考过程，每个节点显示 thought + score。
6. **成本提示**：界面显示"本次 ToT 共 N 个 LLM 调用，消耗 M tokens"，让学生直观感受搜索成本。
7. **简单评估器**：用规则评估器（如"答案是否包含数字"）替代 LLM 评估器，降低不确定性。

**警告信号:**
- 单次请求超过 1 分钟
- Token 消耗超过 10k
- 树深度 < 3 但节点数 > 30（剪枝失效）
- 同一父节点的子分支评分完全相同（评估器失效）

**应对阶段:**
ToT 模式实现阶段（建议最后或靠后实现，复杂度高，需要前面模式的可视化基础）。

---

### 陷阱 9: 教学项目的过度工程化

**出了什么问题:**
1. 一上来就抽象 `Agent`、`AgentFactory`、`AgentRegistry`、`ToolManager`、`MemoryStore` 等大量接口，但只有 2 个具体实现。抽象层比业务代码还多。
2. 用配置文件 / 注解 / SPI 机制动态加载 agent 模式，但学生看代码完全不知道哪个类是干什么的。
3. 把 7 种模式硬塞进一个统一框架（"所有模式都走 Planner -> Executor -> Reflector"），但 CoT 根本不需要这一套，强行套用反而失真。
4. 追求"生产级"特性（重试、限流、缓存、监控），但教学项目核心是"看懂原理"，不是"健壮性"。
5. 隐藏太多魔法：`@Agent` 注解 + AOP，学生看不到 agent 实际怎么跑的，违背教学初衷。
6. 一次性实现所有 7 种模式，每种都半成品。

**原因:**
开发者（尤其 Java/Spring 背景）本能地先抽象后实现，担心"以后扩展不方便"。教学项目却恰恰相反——清晰胜过通用，可见胜过优雅。

**如何避免:**
1. **一个模式一个 package**：`com.atguigu.gulimall.agents.cot`、`...react`，每个 package 内部独立，不共享抽象。等 3+ 个模式实现后再提取公共代码。
2. **拒绝过早抽象**：除非有 3 个以上具体实现，不抽接口。CoT 直接调 `chatModel.call(prompt)`，包一层 `CoTAgent` 类就够了。
3. **代码即教程**：每个 agent 模式的核心逻辑（prompt 构造、循环控制、结果解析）必须在一个文件里能读完（< 200 行）。`AgentRunner.java` 这种"上帝类"是反模式。
4. **优先具体后通用**：先实现 ReAct 自己的循环，再实现 Plan-and-Execute 自己的循环，最后看是否真有公共逻辑可抽。
5. **每个模式独立交付**：一个 phase 完成一个模式，能跑、能展示、能让学生看懂，再开下一个。
6. **少用注解/AOP 魔法**：显式调用 `reactAgent.run(prompt)` 比 `@Agent("react")` + 反射更教学友好。
7. **限制"生产级"特性**：除非教学目的就是讲这些（如"Reflexion 重试机制"），否则不加重试、缓存、监控。

**警告信号:**
- 一个 agent 模式的实现涉及 5+ 个类
- 接口只有一个实现
- 学生问"这个 agent 在哪里跑的"，需要看 3 层调用栈才能找到
- 加一个新模式要改 5 个文件
- "Agent" 接口的方法签名很通用（如 `Object execute(Object input)`），但实际调用都靠 cast

**应对阶段:**
所有 phase 都要警惕。Phase 1（架构骨架）尤其重要——不要在骨架阶段就抽 agent 基类，只搭前后端 + DeepSeek 通信。

---

### 陷阱 10: MCP 集成的版本与配置陷阱

**出了什么问题:**
1. Maven groupId 漂移：Spring AI 2.0 把 MCP 相关 artifact 从 `io.modelcontextprotocol.sdk` 改到 `org.springframework.ai`，老教程的依赖找不到。
2. 传输协议选择混乱：STDIO、SSE、Streamable HTTP 三种，不知道选哪个。SSE 又分 WebMVC 和 WebFlux 两种 starter。
3. SSE endpoint 配置错误：URL 拆分不当（base-url vs sse-endpoint），导致 404。`sse-endpoint` 必须以 `/` 开头。
4. MCP Server 启动但工具发现失败：Spring AI 自动配置未启用，或 `spring.ai.mcp.client.enabled=false` 被误设。
5. MCP Server 版本与 Client 版本不匹配：MCP spec 还在演进，旧 server 不支持新 client 的某些 capability。
6. 教学场景下，学生不知道怎么起一个 MCP Server 来测试。
7. 工具调用超时但没明确错误：MCP Server 跑得慢，Spring AI 默认超时太短。

**原因:**
MCP 是个新协议（2024 末发布），spec 和 SDK 都在快速演进。Spring AI 的 MCP 集成在 1.1.x 才稳定。三种传输协议各有适用场景，但教学项目容易选错。

**如何避免:**
1. **用 Spring AI 2.0.0**：MCP 集成已稳定，groupId 统一在 `org.springframework.ai`。
2. **教学项目选 Streamable HTTP**：跨网络友好，比 SSE 配置简单，比 STDIO 更直观（STDIO 需要起子进程）。
3. **配置示例化**：README 给出最小可用配置：
   ```yaml
   spring:
     ai:
       mcp:
         client:
           streamable-http:
             connections:
               weather-server:
                 url: http://localhost:8080
   ```
4. **工具发现验证**：Phase 9 启动后，写一个 `/api/mcp/tools` 端点列出所有已注册 MCP 工具，前端能查看。
5. **超时显式配置**：`spring.ai.mcp.client.request-timeout=30s`（默认可能太短）。
6. **教学场景**：内置一个简易 MCP Server（如返回固定天气数据），让学生能本地跑通，再换成真实 MCP Server。
7. **错误处理**：MCP Server 不可达时，agent 应能降级到"内置工具"，而不是整个请求失败。

**警告信号:**
- 启动时 `McpClient` bean 注入失败
- 工具列表为空但配置看似正确
- SSE endpoint 404
- 工具调用偶发超时
- 学生反馈"按教程配了但不工作"

**应对阶段:**
MCP 集成阶段（建议靠后，ReAct 模式实现之后，因为 ReAct 才需要外部工具）。

---

### 陷阱 11: 流式响应中工具调用的拼接

**出了什么问题:**
1. Spring AI 流式响应中，一次工具调用的 arguments JSON 会被拆成多个 chunk 发过来，前端解析时遇到不完整 JSON 就报错。
2. 工具调用的 name 和 arguments 在不同 chunk，前端误以为有两个工具调用。
3. Tool call 与文本内容混在一起，前端分不清哪段是 thought 哪段是 tool call。
4. 多个工具调用并行时，流式 chunk 交错，前端误关联。
5. 工具执行结果（observation）也需要流式 emit，但 Spring AI 默认 `chatModel.stream()` 只流式 LLM 输出，工具执行是同步的。

**原因:**
OpenAI / DeepSeek 的 streaming protocol 设计上就是 chunked，每个 chunk 只有一小部分信息。Spring AI 的 `Flux<ChatResponse>` 保留了原始 chunk 边界，没有自动聚合。教学项目如果不处理，前端体验极差。

**如何避免:**
1. **用 `ChatClientMessageAggregator`**（Spring AI 官方推荐）：
   ```java
   new ChatClientMessageAggregator().aggregateChatClientResponse(
       chatClient.prompt().stream().chatClientResponse(),
       aggregatedResponse -> { /* 完整的 tool call */ }
   );
   ```
2. **自定义 SSE 事件类型**：定义清晰的事件 schema：
   ```typescript
   type AgentEvent =
     | { type: 'thought', content: string }
     | { type: 'tool_call', name: string, args: object }
     | { type: 'observation', content: string }
     | { type: 'final_answer', content: string }
   ```
3. **前端按事件类型路由**：不要把所有 chunk 当文本追加，根据 `event.type` 分别更新不同 UI 区域。
4. **工具执行结果也 emit**：在 ReAct 循环中，工具调用前后都 emit 事件，让学生看到完整流程。
5. **后端聚合 + 前端细粒度**：后端可以聚合 tool call 参数，但 emit 时仍按"开始调用 -> 参数 -> 结果"分事件，前端既能正确解析又能看到流程。

**警告信号:**
- 前端 JSON.parse 失败率高
- 工具参数显示为 `[object Object]` 或 `undefined`
- 同一个工具调用显示成两条
- 工具调用和最终答案混在一个区域

**应对阶段:**
Phase 1（架构骨架）就要定义好 SSE 事件 schema。ReAct 模式实现阶段真正用到。

---

### 陷阱 12: DeepSeek 速率限制与 keep-alive 处理

**出了什么问题:**
1. 教学演示时多人同时用，触发 DeepSeek 并发限制（v4-pro 每用户 500 并发，v4-flash 2500），返回 HTTP 429。
2. 长时间推理（如 ToT 多层搜索）期间，DeepSeek 发 `: keep-alive` SSE 注释行，前端没正确忽略，导致 JSON.parse 失败。
3. 非 streaming 请求等待时，DeepSeek 返回空行保持连接，Spring AI 的 WebClient 可能误判为响应结束。
4. 北京时间 9-12、14-18 是 DeepSeek 高峰期，即将实行 2x 计费，教学演示成本翻倍。
5. 单次请求推理超过 10 分钟，DeepSeek 主动关闭连接。
6. 没传 `user_id`，多个用户共享配额，互相影响。

**原因:**
DeepSeek API 有具体的速率限制和 keep-alive 机制，但 Spring AI 的封装层没有完全屏蔽这些细节。教学项目通常不考虑并发，但在课堂演示场景下会暴露。

**如何避免:**
1. **配置 retry**：`spring.ai.retry.max-attempts=3`、`spring.ai.retry.backoff-interval=2000ms`，遇到 429 自动退避重试。
2. **前端解析 SSE 时跳过注释**：行首是 `:` 的是注释（如 `: keep-alive`），直接忽略。
3. **设置 `user_id`**：每个浏览器 session 用一个 user_id（如 `student-{uuid}`），避免共享配额。通过 `extra_body` 传给 DeepSeek。
4. **教学演示前预热**：高峰期演示前先跑几个简单请求确认 API 可用。
5. **超时配置**：`spring.ai.deepseek.timeout=600000`（10 分钟），匹配 DeepSeek 上限。
6. **429 用户友好提示**：前端收到 429 时，显示"DeepSeek 当前繁忙，请稍后再试"，而不是堆栈错误。
7. **成本监控**：教学项目里加一个简单的 token 计数器，每次请求 emit token 消耗，让学生有成本意识。

**警告信号:**
- 偶发 HTTP 429
- 前端报 "Unexpected token : in JSON"
- 课堂演示时随机请求失败
- Token 消耗异常高（keep-alive 被计入）

**应对阶段:**
Phase 1 配置好 retry 和超时。后续每个 agent 模式都要正确处理 keep-alive（在 SSE 解析层统一处理，不每个模式重复）。

## 技术债务模式

| 捷径 | 即时收益 | 长期成本 | 何时可接受 |
|----------|-------------------|----------------|-----------------|
| 用 OpenAI 兼容模式接 DeepSeek（不引入 spring-ai-starter-model-deepseek） | 少一个依赖，配置简单 | 丢失 reasoning_content 字段，CoT/Reflexion 模式废 | 永不（教学项目核心就是展示推理过程） |
| 硬编码 DeepSeek API key 在 application.yml | 本地能跑 | 提交到 git 泄露，学生抄作业时用自己的 key 还改不回来 | 永不 |
| 前端用 EventSource 而非 fetch+ReadableStream | 代码短几行 | 无法 POST、无法带 auth header、自动重连导致重复 | 仅 Phase 1 极短期的 spike，正式实现必须换 |
| agent 模式不抽公共接口，每个模式独立实现 | 短期看似重复 | 看似重复，实则教学清晰 | 完全 acceptable，本项目的正确选择 |
| 工具调用结果不流式 emit，只在最后返回 | 实现简单 | 学生看不到工具调用过程，教学价值减半 | 永不（教学核心是可见性） |
| 把 7 种模式塞进一个 `Agent` 抽象基类 | 看似优雅 | 强行套用导致 CoT 也要走 Planner/Executor 假流程 | 永不 |
| 不写测试，靠手动 curl 验证 | 快 | Spring AI 版本升级时静默失败 | Phase 1 spike 可接受，正式实现必须有 smoke test |
| Vue 组件里直接写 SSE 解析逻辑 | 不用抽工具函数 | 7 种模式组件各写一份，bug 漫延 | Phase 1 可接受，第二个模式开始时抽 composable |

## 集成陷阱

| 集成 | 常见错误 | 正确做法 |
|-------------|----------------|------------------|
| DeepSeek API | 用 `deepseek-chat` 模型名（V3 时代），错过 V4 能力 | 用 `deepseek-v4-pro` 或 `deepseek-v4-flash`；旧名仍可用但能力弱 |
| DeepSeek API | 非 streaming 请求不处理空行 keep-alive，误判响应结束 | 解析时跳过空行，或用 OpenAI SDK 已处理 |
| DeepSeek API | 忘记 `user_id` 导致多人共享配额 | 前端生成 session id，通过 extra_body 传给 DeepSeek |
| Spring AI DeepSeek starter | 用 OpenAI starter + base-url 改写（兼容模式） | 用 `spring-ai-starter-model-deepseek`，访问 `reasoning_content` |
| Spring AI ChatClient | 用 `.functions()` 注册工具（旧 API） | 用 `.tools(FunctionToolCallback.builder()...)` |
| Spring AI ChatClient | `.options(builtChatOptions)` 编译失败 | `.options(chatOptionsBuilder)` 不调用 `.build()` |
| Spring AI streaming | 直接 `Flux<ChatResponse>` 给前端，遇到 tool call chunk 解析失败 | 用 `ChatClientMessageAggregator` 聚合 |
| MCP Client | `sse-endpoint` 不以 `/` 开头，404 | 始终以 `/` 开头，且与 base-url 分离 |
| MCP Maven | 用 `io.modelcontextprotocol.sdk` groupId（旧） | Spring AI 2.0 用 `org.springframework.ai` |
| Vue + SSE | 用 `EventSource` 想发 POST 请求 | 用 `fetch` + `ReadableStream`，手动解析 SSE 格式 |
| Vue + SSE | 切换模式时未关闭旧 SSE 连接，多个流并行写同一 ref | `onBeforeUnmount` + 模式切换时 `controller.abort()` |
| Nginx | 默认 `proxy_buffering on`，SSE 被缓冲 | `proxy_buffering off` + 响应头 `X-Accel-Buffering: no` |

## 性能陷阱

| 陷阱 | 症状 | 预防 | 何时会出问题 |
|------|----------|------------|----------------|
| Vue 3 用 `ref<string>` 累积流式字符串，每次 `+= chunk` | 字符串越长渲染越卡，10k 字符后明显卡顿 | 用 `shallowRef` 或 `ref<string[]>` push 后 join | 单次输出 > 5k 字符 |
| ToT 分支因子设 4+，深度 4+ | 单次请求分钟级，token 几万 | branching=2, depth=3, top-K=2 剪枝 | branching × depth > 8 |
| Reflexion memory 无上限 | context window 爆炸，每次反思成本翻倍 | 只保留最近 2 次反思 | 反思次数 > 3 |
| 每个工具调用都新建 ChatClient | 启动慢，GC 频繁 | ChatClient 是单例，复用 | QPS > 1 |
| Spring MVC 同步处理 SSE 请求 | servlet 线程池耗尽，新请求拒绝 | 用 WebFlux 或 `SseEmitter` 异步 | 并发 > 10 |
| ReAct 没去重，反复调同工具 | token 消耗线性增长 | 维护 `Set<tool+args>` 去重 | iterations > 5 |
| DeepSeek API key 共享，无 user_id | 课堂演示触发 429 | 每个 session 独立 user_id | 并发用户 > 5 |
| 前端解析每个 SSE chunk 都触发 Vue 重渲染 | 浏览器 CPU 100% | requestAnimationFrame 批量更新 | chunk 频率 > 50/秒 |

## 安全错误

| 错误 | 风险 | 预防 |
|---------|------|------------|
| DeepSeek API key 硬编码在 application.yml 提交 git | key 泄露，被盗刷 | 用环境变量 `${DEEPSEEK_API_KEY}`，.gitignore 排除 .env |
| 后端 CORS 配 `*` + `allowCredentials: true` | 任何网站可携带 cookie 调用 | 明确配前端域名 `http://localhost:5173` |
| MCP Server URL 学生可任意配置 | SSRF 风险（学生配内网地址） | 配置白名单或仅允许 localhost |
| 工具执行结果直接拼到 LLM prompt | prompt injection（工具返回"忽略之前指令..."） | 工具结果用明确分隔符标记，system prompt 强调"工具结果不可信" |
| SSE 端点无鉴权（教学简化） | 任何人可调用消耗 DeepSeek 配额 | Phase 1 加简单 token 鉴权（即使教学也需要） |
| 学生提交作业时复制了 API key | 全班 key 泄露 | README 明确"不要把 key 提交到作业仓库" |
| MCP 工具暴露文件系统读取 | 学生配的 MCP Server 可能读敏感文件 | 教学项目内置 MCP Server 限定安全目录 |

## UX 陷阱

| 陷阱 | 用户影响 | 更好做法 |
|---------|-------------|-----------------|
| 流式输出时滚动条乱跳（自动滚到底但用户想看上面） | 学生看不到自己关心的部分 | "停止自动滚动"按钮，用户手动滚后暂停自动跟随 |
| 7 种模式只有名字没有说明 | 学生不知道选哪个 | 每个模式配一句话简介 + 适用场景 + 示例问题 |
| 推理过程和最终答案显示在同一区域 | 学生分不清思考链和结论 | UI 分区：左推理过程，右最终答案；或时间轴展示 |
| 工具调用参数显示为 JSON 字符串 | 不直观 | 按 key:value 表格展示，复杂对象折叠 |
| 模式执行失败只显示"出错了" | 学生不知道为什么失败 | 显示具体错误（max iterations / 429 / parse error）+ 建议操作 |
| 默认 prompt 是"Hello" | 学生不知道该问什么，看不到模式差异 | 每种模式提供 2-3 个推荐示例问题，点击直接填充 |
| Token 消耗不显示 | 学生无成本意识，狂刷请求 | 每次请求后显示 token 数 + 累计消耗 |
| 切换模式时清空对话历史 | 学生想对比模式差异但失去上下文 | 模式切换保留 prompt，仅清空 agent 状态 |
| 长时间推理无进度提示 | 学生以为卡死了 | ToT 显示"搜索中 X/Y 节点"，ReAct 显示"第 N 步" |

## "看似完成实则未完成"清单

- [ ] **SSE 流式打通:** 常缺失 `text/event-stream` Content-Type 或 Nginx buffering 未关 - 验证浏览器 DevTools 看到 EventStream 逐 event 进来
- [ ] **DeepSeek 集成:** 常缺失 `reasoning_content` 字段（用错 OpenAI 兼容模式） - 验证 `getReasoningContent()` 返回非空
- [ ] **CoT 模式:** 常缺失推理过程展示（只显示最终答案） - 验证前端能分别显示思考链和答案
- [ ] **ReAct 模式:** 常缺失 max iterations 限制 - 验证测试一个会让 agent 循环的 prompt，能在 10 次内停止
- [ ] **Plan-and-Execute:** 常缺失 replan 机制 - 验证故意让某步失败，能看到 replan 事件
- [ ] **ToT 模式:** 常缺失剪枝可视化 - 验证前端能看到被剪掉的分支
- [ ] **Reflexion:** 常缺失反思 memory 上限 - 验证反思 3 次后停止，不会无限循环
- [ ] **Role-playing:** 常缺失终止条件 - 验证多 agent 对话能在 N 轮内结束
- [ ] **MCP 集成:** 常缺失工具列表展示 - 验证 `/api/mcp/tools` 能列出所有已注册工具
- [ ] **错误处理:** 常缺失 429 重试 - 验证在 DeepSeek 限流时能自动退避重试
- [ ] **Vue 响应式:** 常缺失流式字符串性能优化 - 验证 5k+ 字符输出不卡顿
- [ ] **连接管理:** 常缺失组件卸载时关闭 SSE - 验证切换页面后 Network 面板无残留连接
- [ ] **API key 安全:** 常缺失 .gitignore 排除 - 验证 `git status` 不显示 .env / application-local.yml

## 恢复策略

| 陷阱 | 恢复成本 | 恢复步骤 |
|---------|---------------|----------------|
| Spring AI 版本升级 breaking change | MEDIUM | 看 upgrade-notes，按迁移指南改 API 调用，跑 smoke test |
| 误用 OpenAI 兼容模式（丢失 reasoning_content） | LOW | 换 `spring-ai-starter-model-deepseek`，改配置 key 前缀 |
| 前端用了 EventSource 无法 POST | MEDIUM | 重写为 fetch + ReadableStream，封装 composable 复用 |
| ReAct 无限循环已上线 | LOW | 加 max iterations 硬上限 + 去重保护 |
| 过度抽象需要重构 | HIGH | 提取真正的公共代码，删掉只用一次的接口，每种模式回退到独立实现 |
| SSE 被缓冲（部署后才发现） | LOW | Nginx 加 `proxy_buffering off`，响应头加 `X-Accel-Buffering: no` |
| DeepSeek API key 泄露 | HIGH | 立即吊销 key，重新申请，全仓库扫描清理，加 .gitignore |
| MCP 配置错误 404 | LOW | 检查 sse-endpoint 是否以 `/` 开头，URL 拆分是否正确 |
| Vue 流式渲染卡顿 | MEDIUM | 改用 `shallowRef` 或 `ref<string[]>`，加 requestAnimationFrame 批量更新 |
| ToT 成本爆炸 | LOW | 减小 branching factor 和 depth，加剪枝 |

## 陷阱到阶段映射

| 陷阱 | 预防阶段 | 验证 |
|---------|------------------|--------------|
| Spring AI 版本碎片化 | Phase 1（架构骨架） | pom.xml 锁定 BOM 版本，CI enforcer 规则通过 |
| DeepSeek 模型名配置 | Phase 1 | 单测断言 `getReasoningContent()` 非空 |
| SSE 浏览器缓冲 | Phase 1 | DevTools 看到 EventStream 逐 event |
| EventSource 限制 | Phase 1 | fetch+ReadableStream 封装通过代码 review |
| ReAct 无限循环 | ReAct 模式 phase | 测试循环 prompt 在 max iterations 内停止 |
| Plan-and-Execute 漂移 | Plan-and-Execute phase | 测试失败步骤能触发 replan |
| Reflexion 卡循环 | Reflexion phase | 测试 max reflections 触发 |
| ToT 分支爆炸 | ToT phase | 测试 token 消耗 < 10k |
| 教学过度工程化 | 所有 phase | code review：每个模式核心逻辑 < 200 行单文件 |
| MCP 集成陷阱 | MCP phase | `/api/mcp/tools` 列出工具 |
| 流式 tool call 拼接 | Phase 1 + ReAct phase | 前端正确解析 tool_call 事件 |
| DeepSeek 速率限制 | Phase 1 | 429 自动重试测试通过 |

## 来源

- [Spring AI 参考文档 (Context7 /websites/spring_io_spring-ai_reference)](https://docs.spring.io/spring-ai/reference/) - 高置信度
- [Spring AI 升级说明](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) - 高置信度
- [Spring AI DeepSeek Chat 文档](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html) - 高置信度
- [Spring AI 工具迁移指南](https://docs.spring.io/spring-ai/reference/api/tools-migration.html) - 高置信度
- [Spring AI MCP Client 文档](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html) - 高置信度
- [DeepSeek API 文档 - 首次 API 调用](https://api-docs.deepseek.com/) - 高置信度（直接抓取，已确认 V4 模型）
- [DeepSeek 速率限制与隔离](https://api-docs.deepseek.com/quick_start/rate_limit) - 高置信度（直接抓取）
- [DeepSeek 定价](https://api-docs.deepseek.com/quick_start/pricing) - 高置信度（直接抓取，已确认并发限制: v4-pro 500，v4-flash 2500）
- [WHATWG Server-Sent Events 规范](https://html.spec.whatwg.org/multipage/server-sent-events.html) - 高置信度（直接抓取）
- [Spring AI GitHub 示例](https://github.com/spring-projects/spring-ai-examples) - 中置信度

---
*针对以下项目的陷阱研究: Spring AI + DeepSeek Agent 设计模式教学案例库*
*研究日期: 2026-08-04*
