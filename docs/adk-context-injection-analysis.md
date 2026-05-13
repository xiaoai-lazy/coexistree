# ADK Context Injection: Session/State 机制评估

## 问题背景

在 CoExistree 中，Agent 的工具（Tool）需要访问应用层的运行时上下文（用户是谁、在看哪个系统、有什么权限）。我们用一个 `AgentUserContext` 对象承载这些信息：

```java
public record AgentUserContext(
    Long userId,
    Long systemId,
    Integer viewLevel,
    String conversationId,
    UserRole role
) {}
```

## 根因：ADK FunctionTool 无法注入自定义类型

Google ADK 的 `FunctionTool.buildArguments()` 对工具方法参数的解析规则：

| 参数匹配方式 | 来源 |
|---|---|
| 参数名 == `"toolContext"` | ADK 自动注入 `ToolContext` 对象 |
| 参数名 == `"inputStream"` | ADK 自动注入流式请求队列 |
| 其他参数名 | 从 LLM 返回的 JSON 中取值 |

`AgentUserContext` 是自定义类型，不在 LLM JSON 中，也不是 ADK 识别的特殊参数名。所以 **工具方法中的 `AgentUserContext context` 参数始终为 null**。

## 当前方案：ThreadLocal

我们在 `AgentChatServiceImpl.runAgentWithSse()` 中设置 `AgentContextHolder`（ThreadLocal），工具执行时从中读取。

**优点**：实现简单，改动最小。
**缺点**：
- 依赖单线程执行模型，如果 ADK 将来引入异步调用链会断裂
- 不符合 ADK 的官方设计模式
- 每个工具方法都需要 `AgentContextHolder.get()` + fallback 逻辑

## 官方方案：Session + State

ADK 提供了 `Session` → `State` → `ToolContext.state()` 的完整数据流：

### 文档描述的数据流

```
┌─────────────────────────────────────────────────────────┐
│ 1. Session 创建阶段                                      │
│                                                         │
│ ConcurrentMap<String, Object> initialState =            │
│     new ConcurrentHashMap<>();                          │
│ initialState.put("app:agentUserContext", ctx);          │
│                                                         │
│ sessionService.createSession(                           │
│     appName, userId, initialState, Optional.of(sid))    │
│     .blockingGet();                                     │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ 2. Agent 执行阶段                                        │
│                                                         │
│ runner.runAsync(userId, sessionId, content, config)     │
│                                                         │
│ InMemoryRunner 内部通过 sessionService 获取 session     │
│ session.state() 中包含了 initialState 的副本            │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ 3. Tool 执行阶段                                         │
│                                                         │
│ public String searchTree(String query,                  │
│         @Schema(name="toolContext") ToolContext tc) {   │
│                                                         │
│     AgentUserContext ctx = (AgentUserContext)           │
│         tc.state().get("app:agentUserContext");         │
│     // 使用 ctx...                                      │
│ }                                                       │
└─────────────────────────────────────────────────────────┘
```

### State 前缀规则

| 前缀 | 作用域 | 生命周期 |
|---|---|---|
| `app:` | 全局 — 所有用户共享 | 持久 |
| `user:` | 用户级 — 同一用户跨会话 | 持久 |
| `temp:` | 单次请求 — 当前工具调用周期 | 请求结束销毁 |
| 无前缀 | 会话内 — 当前 session 内部 | 会话生命周期 |

我们的场景应使用 `app:` 前缀。

## 核心问题：InMemoryRunner 的 SessionService 隔离

### 现状

`AgentChatServiceImpl` 中 `InMemoryRunner` 的创建方式：

```java
runner = new InMemoryRunner(rootAgent, "coexistree");
```

构造函数签名：
```java
InMemoryRunner(BaseAgent agent, String appName)
```

`InMemoryRunner` 内部会**自己 new 一个 `InMemorySessionService` 实例**来管理 session。

### 冲突

如果采用官方模式，我们需要在 `runAsync` 之前创建带初始 state 的 session：

```java
// 我们想这样做：
sessionService.createSession("coexistree", "user-1", stateMap, Optional.of("conv-1"))
    .blockingGet();

// 然后调用：
runner.runAsync("user-1", "conv-1", content, config);
```

**但问题是**：`runner` 内部持有的是它自己创建的 `InMemorySessionService` 实例。我们手动创建的 session 存到了哪个 `SessionService` 上？如果两个不是同一个实例，`runAsync` 获取不到我们预置的 state。

### 三种可能的解决路径

#### 路径 A：InMemoryRunner 支持注入 SessionService

如果 `InMemoryRunner` 有构造函数或 builder 接受外部 `SessionService`：

```java
InMemorySessionService sessionService = new InMemorySessionService();
sessionService.createSession(...).blockingGet();

InMemoryRunner runner = new InMemoryRunner(rootAgent, "coexistree", sessionService);
```

**需要验证**：是否存在这样的构造函数。当前已知的构造函数签名：
- `InMemoryRunner(BaseAgent)`
- `InMemoryRunner(BaseAgent, String)`
- `InMemoryRunner(BaseAgent, String, List<Plugin>)`

**没有接受 `SessionService` 参数的构造函数。**

#### 路径 B：关闭 autoCreateSession，手动管理 session

如果 `InMemoryRunner` 在 `runAsync` 时发现 session 不存在就自动创建，那我们可以：

1. 确认 `InMemoryRunner` 内部 sessionService 的访问方式
2. 在调用 `runAsync` 前通过某种方式注入 state

**问题**：`InMemoryRunner` 的 sessionService 是私有字段，没有 getter。我们无法从外部拿到它来预创建 session。

#### 路径 C：用 ThreadLocal 绕过

就是当前的方案。

### 决策矩阵

| 路径 | 可行性 | 风险 | 工作量 |
|---|---|---|---|
| A: 注入 SessionService | ⚠️ 构造函数不支持 | 低（如果能支持） | 中（需包装或 fork） |
| B: 手动管理 session | ❓ 不确定 Runner 能否看到 | 中（可能 session 隔离） | 小（如果可行） |
| C: ThreadLocal（当前） | ✅ 已验证可用 | 低 | 已完成 |

## 待验证项

1. **`InMemoryRunner` 的 sessionService 是否可以外部注入？**
   - 反编译确认所有构造函数和 builder 模式
   - 确认 Runner 基类是否有可覆盖的方法

2. **如果 Runner 内部 sessionService 不可访问，是否可以通过 State 的前缀机制在 runAsync 之后、tool 执行之前设置？**
   - 通过 `ToolContext.state().put()` 在第一个 callback 中设置

3. **Callback 中设置 state 的时机**
   - `beforeAgentCallback` 或 `beforeModelCallback` 中可以通过 `CallbackContext.state()` 写入
   - Tool 执行时通过 `ToolContext.state()` 读取
   - 这是最轻量的官方模式适配路径

## 推荐方案：Callback + ToolContext（待验证）

如果路径 A/B 不可行，最接近官方模式的做法是：

```java
// 在 AgentConfig 中给 qa-agent 注册 beforeAgentCallback
@BeforeAgent
public void injectContext(CallbackContext ctx) {
    AgentUserContext appCtx = AgentContextHolder.get();
    if (appCtx != null) {
        ctx.state().put("app:agentUserContext", appCtx);
    }
}

// Tool 中通过 ToolContext 读取
public String searchTree(String query,
        @Schema(name = "toolContext") ToolContext toolContext) {
    AgentUserContext ctx = (AgentUserContext)
        toolContext.state().get("app:agentUserContext");
    // ...
}
```

**这仍然依赖 ThreadLocal 从 Service 层传递到 Callback**，但至少 Tool 层走的是官方 `ToolContext` 通道，不是直接读 ThreadLocal。
