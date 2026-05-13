# 会话审计增强：完整事件树与 LLM 调用记录

**状态:** draft
**日期:** 2026-04-22

## 问题

当前会话审计存在两个核心缺陷：

1. **LLM 调用记录缺失** — ADK（Google Agent Development Kit）内部调用 LLM，不走 `LlmClient.chat()`，所以现有 AOP 拦截器 `LlmCallTrackingAspect` 捕获不到。`session_events` 表中没有 ADK agent 的 LLM 请求/响应。
2. **子 agent 执行不可见** — root-agent 调用 qa-agent/eval-agent 时，`EventContext` 的 `agentName` 硬编码为 `"root-agent"`，子 agent 的执行在审计中完全不可见。
3. **前端视图为扁平列表** — 虽然数据库有 `parent_event_id` 字段构建树形结构，但前端没有利用它展示调用层级关系。

## 目标

1. 记录每次 LLM 调用（请求摘要 + 响应内容 + token 数），覆盖 root-agent、qa-agent、eval-agent 及所有未来子 agent。
2. 记录每个 agent 的执行生命周期（agent_turn），包括子 agent 被调用时的独立记录。
3. 前端将扁平事件列表重构为树形可展开视图，清晰展示调用层级和因果关系。

## 技术方案

### ADK Java 1.0.0 回调确认

ADK Java 提供以下回调机制（通过 `LlmAgent.Builder`）：

| 回调 | 签名 | 触发时机 |
|------|------|----------|
| `beforeAgentCallback` | `CallbackContext → Maybe<Content>` | agent 执行前 |
| `afterAgentCallback` | `CallbackContext → Maybe<Content>` | agent 执行后 |
| `beforeModelCallback` | `(CallbackContext, LlmRequest.Builder) → Maybe<LlmResponse>` | LLM 调用前 |
| `afterModelCallback` | `(CallbackContext, LlmResponse) → Maybe<LlmResponse>` | LLM 调用后 |
| `onModelErrorCallback` | `(CallbackContext, LlmRequest, Exception) → Maybe<LlmResponse>` | LLM 调用出错 |
| `beforeToolCallback` | `(InvocationContext, BaseTool, Map<String,Object>, ToolContext) → Maybe<Map<String,Object>>` | 工具调用前 |
| `afterToolCallback` | `(InvocationContext, BaseTool, Map<String,Object>, ToolContext, Object) → Maybe<Map<String,Object>>` | 工具调用后 |

**关键确认：**
- 回调挂在每个 agent 上，不是全局的。子 agent 的回调独立生效。
- Builder 的 callback 方法每次调用会替换而非追加（`ImmutableList.of()`）。
- 回调通过 RxJava `Flowable` 触发，可能在不同线程执行。

### 上下文传递策略

**ADK 回调中直接获取 sessionId()**

验证结果：`CallbackContext` 继承 `ReadonlyContext`，后者提供了 `sessionId()` 方法：

```java
public String sessionId() {
    return invocationContext.session().id();
}
```

因此在所有回调中可以直接通过 `ctx.sessionId()` 获取 conversationId，**不需要额外的上下文传递机制**。

| 数据 | 获取方式 | 说明 |
|------|----------|------|
| conversationId | `ctx.sessionId()` | 直接从 CallbackContext 获取 |
| agentName | `ctx.agentName()` | 直接从 CallbackContext 获取 |
| agentTurnIdMap | 回调实例字段 | 用 ConcurrentHashMap<conversationId, Map> 跨回调传递 turn ID |
| llmRequestEventIdMap | 回调实例字段 | 同上，关联 llm_request 和 llm_response |

### 运行时调用链

```
用户提问 → AgentChatServiceImpl.smartChatStream()
  ├── saveUserMessageEvent()              → INSERT user_message
  │
  └── runner.runAsync() → SSE 流
        │
        │ Plugin.beforeAgentCallback("root-agent")
        │   ├── ctx.sessionId() → conversationId
        │   ├── parentAgentTurnId = null
        │   ├── INSERT agent_turn(root-agent)
        │   │
        │ Plugin.beforeModelCallback()
        │   ├── INSERT llm_request (parent=root-agent turn)
        │ Plugin.afterModelCallback()
        │   └── INSERT llm_response (parent=llm_request)
        │
        │ [ADK 内部: root-agent 调用 qa-agent AgentTool]
        │   │ Plugin.beforeAgentCallback("qa-agent")
        │   │   ├── ctx.sessionId() → 同一个 conversationId
        │   │   ├── parentAgentTurnId = root-agent turn ID
        │   │   ├── INSERT agent_turn(qa-agent, parent=root-agent turn)
        │   │
        │   │ Plugin.beforeModelCallback()
        │   │   └── INSERT llm_request (parent=qa-agent turn)
        │   │ Plugin.afterModelCallback()
        │   │   └── INSERT llm_response
        │   │
        │   │ Plugin.beforeToolCallback()
        │   │   └── ToolCallEventWriter.writeToolRequest()
        │   │     → INSERT tool_request (parent=qa-agent turn)
        │   │ Plugin.afterToolCallback()
        │   │   └── ToolCallEventWriter.writeToolResponse()
        │   │     → INSERT tool_response (parent=tool_request)
        │   │
        │   │ Plugin.beforeModelCallback()
        │   │   └── INSERT llm_request (parent=qa-agent turn)
        │   │ Plugin.afterModelCallback()
        │   │   └── INSERT llm_response
        │   │
        │   │ Plugin.afterAgentCallback("qa-agent")
        │   │   └── 更新 qa-agent turn: status=success, duration
        │
        │ Plugin.beforeModelCallback()
        │   └── INSERT llm_request (parent=root-agent turn)
        │ Plugin.afterModelCallback()
        │   └── INSERT llm_response
        │
        │ Plugin.afterAgentCallback("root-agent")
        │   └── 更新 root-agent turn: status=success, duration
        │
        └── onComplete()
            └── 发送 SSE answer
```

## 文件变更

### 新建文件

| 文件 | 职责 |
|------|------|
| `AgentObservationCallbacks.java` | Spring Bean，提供 8 个回调创建方法，内部用 ConcurrentMap 跨回调传递 turn ID |
| `EventTreeAssembler.java` | 纯函数：扁平事件列表 → 树形 DTO |
| `AgentEventTreeNode.java` | 树形 DTO 的数据结构 |
| `AgentEventTree.vue` | 前端树形渲染组件 |
| `AgentEventNode.vue` | 前端单节点组件（递归） |

### 修改文件

| 文件 | 改动 |
|------|------|
| `AgentConfig.java` | 注入 `AgentObservationCallbacks`，在三个 agent builder 中挂回调 |
| `SessionEventService.java` | `insertAgentTurn` 新增 `parentEventId` 参数支持 |
| `SessionAuditController.java` | 新增 `getEventsTree()` 端点 |
| `SessionAuditView.vue` | 调用新 API，替换扁平视图为树形组件 |

### 不变文件

| 文件 | 原因 |
|------|------|
| `ToolCallEventWriter.java` | 继续由 `AgentObservationCallbacks` 调用，无需修改 |
| `AgentExecutionLogger.java` | 继续负责 console 日志和 agent_error 事件 |
| `LlmCallTrackingAspect.java` | 继续拦截 indexer/knowledge 子系统的 LlmClient.chat() |
| `EventContextHolder.java` | 保留现有职责，不修改 |
| `AgentChatServiceImpl.java` | 不需要修改 — 回调通过 agent builder 挂载，不依赖 service 层初始化 |

## 数据模型

### AgentEventTreeNode DTO

```java
public record AgentEventTreeNode(
    Long id,
    String eventType,        // agent_turn, llm_call, tool_call, user_message, agent_error
    String agentName,
    String status,           // streaming, success, failed
    Long durationMs,
    String role,
    String content,
    JsonNode metadata,
    LocalDateTime createdAt,
    List<AgentEventTreeNode> children,
    boolean hasDetail        // 前端判断是否可展开详情
) {}
```

### 合并规则

| 数据库事件对 | 合并后 eventType | 规则 |
|------------|-----------------|------|
| llm_request + llm_response | llm_call | 合并 metadata：prompt + content + tokens。parent 保持不变 |
| tool_request + tool_response | tool_call | 合并 metadata：input + output + elapsed_ms |
| 只有 llm_request 没有 response | llm_call (failed) | 不合并，status=failed |
| 只有 tool_request 没有 response | tool_call (failed) | 不合并，status=failed |
| agent_turn | agent_turn | 不合并，children 是合并后的节点 |
| user_message | user_message | 不合并，无 children |
| agent_error | agent_error | 不合并，无 children |

### 示例输出

```json
{
  "id": 123456,
  "eventType": "agent_turn",
  "agentName": "root-agent",
  "status": "success",
  "durationMs": 45000,
  "children": [
    {
      "id": 123457,
      "eventType": "llm_call",
      "agentName": "root-agent",
      "status": "success",
      "durationMs": 1200,
      "metadata": {
        "model": "gpt-4o",
        "prompt": "...",
        "content": "Routing to qa-agent...",
        "tokens": { "prompt": 300, "completion": 50 }
      },
      "children": [],
      "hasDetail": true
    },
    {
      "id": 123460,
      "eventType": "agent_turn",
      "agentName": "qa-agent",
      "parentEventId": 123456,
      "status": "success",
      "durationMs": 30000,
      "children": [
        { "eventType": "llm_call", ... },
        { "eventType": "tool_call", "agentName": "qa-agent", ... },
        { "eventType": "llm_call", ... }
      ]
    }
  ]
}
```

## 边界情况

| 情况 | 处理方式 |
|------|----------|
| LLM 调用失败（只有 request 没有 response） | 不合并，保持 llm_request 为独立节点，status="failed" |
| Tool 调用失败（只有 request 没有 response） | 同上 |
| 嵌套子 agent（qa-agent 内部再调用另一个 agent） | 递归支持，agentTurnIdMap 按 agentName 形成链式传递 |
| 并发请求（多线程） | CallbackContext 自带 conversationId，ConcurrentMap 隔离 |
| 旧数据（没有 agent_turn 的 event，parent_event_id 孤立） | 建树时作为根节点下的 orphan 节点显示 |
| 回调执行顺序异常 | agentTurnIdMap 的 get() 返回 null 时跳过写入，不写脏数据 |

## 前端交互

- agent_turn 默认折叠，点击展开子事件
- llm_call / tool_call 默认折叠，展开显示请求/响应详情
- user_message 直接显示，不折叠
- 点击行打开详情面板（保留现有行为）
- 详情面板复用现有 key-info + 可折叠长内容组件
