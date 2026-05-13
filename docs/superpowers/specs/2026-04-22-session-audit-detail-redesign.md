# Session Audit 事件详情面板重构设计

**日期**: 2026-04-22

## 背景

Session Audit 页面（`SessionAuditView.vue`）用于审计会话中的事件。当前展开事件详情后，所有内容都以折叠块形式呈现，用户需要逐一点开才能看到信息（如 Agent 名称、工具名、耗时等关键字段），效率低。

## 问题

1. **关键信息不可见** — Agent 名称、工具名称、耗时等关键字段藏在折叠块内部，需要点击才能看到
2. **metadata 双重转义** — 前端展示的 metadata 出现双重转义（`"{\\\"tool_name\\\":...}"`），需要确认是 MySQL JSON 列的问题还是前端展示问题
3. **agent 归属不清** — `tool_request`/`tool_response`/`llm_request`/`llm_response` 事件没有记录触发它的 agent 名称
4. **writeToolResponse 的 tool_name 取错** — `writeToolResponse` 第 56 行把 `tool_name` 设成了 `requestEvent.getEventType()`（即 `"tool_request"`），而不是实际工具名

## 设计

### 1. 展开后详情面板分两个区域

#### 区域一：关键信息（直接平铺）

表格/表单式布局，左侧标签、右侧值。每行根据事件类型动态渲染适用的字段：

| 字段 | 来源 | 适用事件类型 |
|------|------|-------------|
| Agent | `metadata.agent` | 全部 agent 相关事件（agent_turn/start/complete/error, tool_request, tool_response, llm_request, llm_response） |
| 工具名 | `metadata.tool_name` | tool_request, tool_response |
| 模型 | `metadata.model` | llm_request, llm_response |
| 角色 | `entity.role` | 全部 |
| 耗时 | `entity.durationMs` | tool_response, agent_turn（完成时）, llm_response |
| 状态 | `entity.status` | 全部 |
| 关联 ID | `entity.correlationId` | 全部 |
| 事件时间 | `entity.createdAt` | 全部 |
| 消息 ID | `entity.messageId` | user_message, llm_request, llm_response |

规则：
- 空值的行不显示
- 状态用图标 + 颜色标识（success 绿色，failed 红色）
- 耗时格式化为 `144.6s` 或 `3m 24s`

#### 区域二：内容详情（保留折叠）

关键信息下方保留折叠块，用于查看长内容：
- `user_message` → 用户输入
- `llm_request` → System Prompt
- `llm_response` → LLM 响应 + tokens
- `tool_request` → 工具输入参数
- `tool_response` → 工具输出结果
- `agent_turn` → Agent 元数据
- `metadata` 始终存在时 → 原始元数据 JSON

### 2. metadata 存储问题排查与修复

**已确认的 bug**：`ToolCallEventWriter.writeToolResponse` 第 56 行把 `tool_name` 设成了 `requestEvent.getEventType()`（即 `"tool_request"`），而不是实际的工具名。应该从 requestEvent 的 metadata 中提取原始 tool_name。

**待排查**：前端展示的 metadata 出现双重转义，需要确认是 MySQL JSON 列的问题还是前端展示问题。如果是数据库层面导致的，需要在 `SessionEventEntity` 添加 `@JdbcTypeCode(SqlTypes.JSON)` 或在写入/读取时做适配。

### 3. 所有 agent 事件展示 agent 名称

当前只有 `agent_turn` 事件在 metadata 中记录了 agent 名称（通过 `insertAgentTurn` 的字符串拼接），而 `tool_request`/`tool_response`/`llm_request`/`llm_response` 事件没有 agent 名称。

修复方案：
1. **`EventContext` 增加 `agentName` 字段** — 在 `AgentChatServiceImpl` 创建 EventContext 时传入（当前硬编码为 `"root-agent"`）
2. **`ToolCallEventWriter` 写入时从 EventContext 取 agentName** — 一并写入 metadata
3. **`LlmCallTrackingAspect` 写入时从 EventContext 取 agentName** — 一并写入 metadata
4. **`insertAgentTurn` 改用 ObjectMapper** — 替换当前的字符串拼接，metadata 统一为 `{"agent": "root-agent"}`
5. 前端关键信息区域读取 `metadata.agent` 展示 Agent 名称

### 4. writeToolResponse 的 tool_name 取错

当前第 56 行：`meta.put("tool_name", requestEvent.getEventType())` 写入的是 `"tool_request"` 而不是实际工具名。修复：从 `requestEvent.getMetadata()` 中解析原始 tool_name，或直接复用 requestEvent 的 metadata。

## 涉及文件

| 文件 | 改动 |
|------|------|
| `frontend/src/views/admin/components/SessionAuditView.vue` | 展开详情改为分栏布局：关键信息平铺 + 长内容折叠 |
| `backend/src/main/java/io/github/xiaoailazy/coexistree/agent/service/ToolCallEventWriter.java` | 修复 tool_name 取错，增加 agent 名称写入 |
| `backend/src/main/java/io/github/xiaoailazy/coexistree/session/SessionEventService.java` | insertAgentTurn 改用 ObjectMapper 生成 metadata |
| `backend/src/main/java/io/github/xiaoailazy/coexistree/session/EventContext.java` | 增加 agentName 字段 |
| `backend/src/main/java/io/github/xiaoailazy/coexistree/agent/service/AgentChatServiceImpl.java` | 创建 EventContext 时传入 agentName |
| `backend/src/main/java/io/github/xiaoailazy/coexistree/shared/aspect/LlmCallTrackingAspect.java` | 从 EventContext 获取 agentName，写入 metadata |
| `backend/src/main/java/io/github/xiaoailazy/coexistree/chat/entity/SessionEventEntity.java` | metadata 字段可能需要 `@JdbcTypeCode` 修复 MySQL JSON 列双重转义 |

## 不需要改动的部分

- API 接口（Controller、DTO）保持不变
- 数据库 schema 不变（metadata 字段仍是 JSON 类型）
- 事件写入逻辑不变，只是 metadata 内容格式修正
