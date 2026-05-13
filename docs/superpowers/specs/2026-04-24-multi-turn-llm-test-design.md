# 多轮对话真实 LLM 集成测试设计

> 日期: 2026-04-24
> 状态: 已审批
> 触发: 需要验证 ADK 重构后的多轮对话能力与可观测数据记录是否正常

## 1. 目标

通过一次真实的大模型调用，验证：
1. 多轮对话上下文传递正常（第二轮回答依赖第一轮内容）
2. 可观测数据（conversation_runs / observation_spans / span_events）正确记录
3. SSE 事件流完整（有 answer/done 事件）

## 2. 测试策略

### 入口：HTTP 端点（MockMvc）
- `POST /api/v1/conversations/{conversationId}/smart-chat`
- Content-Type: `application/json`, Response: `text/event-stream`
- 两轮请求使用相同的 `conversationId`，验证 ADK 的 `DatabaseSessionService` 是否正确维护会话状态

### 环境
- `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- 通过 `LLM_TEST_ENABLED=true` 环境变量门控（与现有 Real LLM 测试一致）
- H2 内存数据库（测试 profile）

## 3. 测试场景

**ConversationId**: `multi-turn-test-<uuid>`（确保幂等性）

### Round 1
- **Question**: `"这个系统有几个功能？"`
- **Assert**: SSE 流中包含非空 `answer` 或 `done` 事件

### Round 2（相同 conversationId，~1s 后）
- **Follow-up**: `"那第二个功能是什么？"`
- **Assert**: SSE 流中包含非空回答内容（代词"那第二个"依赖第一轮上下文）

### 可观测数据验证
- `conversation_runs` by conversationId → **2 条记录**，`status = "SUCCESS"`，`duration_ms` 非空
- `observation_spans` by conversationId → 两轮都有 span 记录，包含 `root-agent` agent span
- `span_events` by conversationId → 两轮都有事件记录

## 4. 关键设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 入口方式 | MockMvc HTTP 端点 | 验证全链路（Security → Controller → Agent → SSE） |
| 单测还是多测 | 一个 test 方法 | 避免状态共享复杂性，两轮在一个方法内顺序执行 |
| 内容校验深度 | 只检查非空，不做语义分析 | 集成测试关注 pipeline 完整性，非 LLM 输出质量 |
| 会话隔离 | UUID conversationId | 避免与其他测试碰撞 |

## 5. 依赖组件

| 组件 | 用途 |
|------|------|
| `ConversationRunRepository` | 验证 conversation_runs 记录 |
| `ObservationSpanRepository` | 验证 observation_spans 记录 |
| `SpanEventRepository` | 验证 span_events 记录 |
| `DatabaseSessionService` | 验证 ADK 会话状态（可选） |

## 6. SSE 事件解析方式

通过 `SseEmitter` 的 `onCompletion` 回调和 `ResponseBodyEmitter` 接口收集事件数据。
解析 `data:` 行中的 JSON（`SseEvent` record），按 type 分类统计。

## 7. AI 答案质量评估（新增）

### 目标
替换原有的 `isNotBlank()` 弱断言，引入 LLM 对答案质量进行合理性判断。

### 机制
- 收集 Round 1 和 Round 2 的答案后，发起一次**直接 LLM 调用**（不经过 `/smart-chat` 端点）
- 使用测试上下文中已配置的 `LangChain4j` bean，复用相同的 LLM 端点和 API Key
- 阻塞调用，无需流式解析

### 评估 Prompt
```
You are evaluating a multi-turn conversation test. Judge whether the answers are reasonable.

Round 1 Question: "这个系统有几个功能？"
Round 1 Answer: [实际答案，截断至 500 字符]

Round 2 Question: "那第二个功能是什么？"
Round 2 Answer: [实际答案，截断至 500 字符]

Check:
1. Is Round 1 answer relevant to "how many features does this system have?"
2. Is Round 2 answer contextually connected to Round 1 (e.g., refers to "第二个" / second feature)?

Respond with exactly one word: PASS or FAIL
```

### 断言
- LLM 返回 `PASS` → 测试通过
- LLM 返回 `FAIL` 或无法解析 → 测试失败，明确报错

### 截断策略
- 答案截断至 500 字符，控制 prompt 大小，加快评估速度

### 影响
- 每次测试增加约 1 次 LLM 调用（~3-5 秒额外延迟）
- 比 `isNotBlank()` 强得多——能捕获幻觉、跑题、上下文断裂等问题
