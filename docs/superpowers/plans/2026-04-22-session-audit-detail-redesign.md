# Session Audit 事件详情面板重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 Session Audit 事件详情面板为分栏布局（关键信息平铺 + 长内容折叠），并修复后端 metadata 中 agent 名称缺失和 tool\_name 取错的 bug。

**Architecture:** 前端将展开的事件详情面板改为两个区域：上方关键信息用表格平铺展示（Agent、工具名、模型、耗时、状态等），下方保留折叠块用于长内容。后端在 `EventContext` 增加 `agentName` 字段，所有事件写入方从 `EventContextHolder` 统一获取并写入 metadata。

**Tech Stack:** Java 21, Spring Boot 3.5.11, Vue 3 (Composition API), Element Plus, H2 (test), MySQL (prod)

***

## File Structure

| 文件                                                            | 操作     | 职责                                          |
| ------------------------------------------------------------- | ------ | ------------------------------------------- |
| `backend/.../session/EventContext.java`                       | Modify | 增加 `agentName` 字段                           |
| `backend/.../session/SessionEventService.java`                | Modify | `insertAgentTurn` 改用 ObjectMapper           |
| `backend/.../agent/service/ToolCallEventWriter.java`          | Modify | 修复 tool\_name 取错，增加 agent 名称                |
| `backend/.../agent/service/AgentChatServiceImpl.java`         | Modify | 创建 EventContext 时传入 agentName               |
| `backend/.../shared/aspect/LlmCallTrackingAspect.java`        | Modify | 从 EventContext 获取 agentName 写入 metadata     |
| `backend/.../chat/entity/SessionEventEntity.java`             | Modify | metadata 字段加 `@JdbcTypeCode(SqlTypes.JSON)` |
| `backend/.../shared/aspect/LlmCallTrackingAspectTest.java`    | Modify | 更新 EventContext 构造函数调用                      |
| `backend/.../chat/controller/SessionAuditControllerTest.java` | Modify | 增加字段验证测试                                    |
| `frontend/src/views/admin/components/SessionAuditView.vue`    | Modify | 详情面板改为分栏布局                                  |

***

### Task 1: EventContext 增加 agentName 字段

**Files:**

- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/session/EventContext.java`
- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/agent/service/AgentChatServiceImpl.java:383-390`
- Modify: `backend/src/test/java/io/github/xiaoailazy/coexistree/shared/aspect/LlmCallTrackingAspectTest.java:39`
- [ ] **Step 1: 修改 EventContext record 增加 agentName**

```java
// backend/src/main/java/io/github/xiaoailazy/coexistree/session/EventContext.java
package io.github.xiaoailazy.coexistree.session;

public record EventContext(
    Long agentTurnId,
    Long triggerMessageId,
    String conversationId,
    String correlationId,
    String agentName
) {
}
```

- [ ] **Step 2: 更新 AgentChatServiceImpl 中 EventContext 创建**

在 `runAgentWithSse` 方法中，找到创建 EventContext 的代码（约第 383 行），增加 `"root-agent"` 参数：

```java
// Before (line ~383):
EventContext eventCtx = new EventContext(
        snowflakeIdGenerator.nextId(),
        triggerMessageId,
        conversationId,
        correlationId
);

// After:
EventContext eventCtx = new EventContext(
        snowflakeIdGenerator.nextId(),
        triggerMessageId,
        conversationId,
        correlationId,
        "root-agent"
);
```

- [ ] **Step 3: 更新 LlmCallTrackingAspectTest 中 EventContext 创建**

```java
// Before (line 39):
EventContextHolder.set(new EventContext(null, null, "conv-1", "corr-1"));

// After:
EventContextHolder.set(new EventContext(null, null, "conv-1", "corr-1", null));
```

- [ ] **Step 4: 编译验证**

Run:

```bash
cd /c/Git/CoExistree/backend && ./mvnw compile -q -DskipTests
```

Expected: BUILD SUCCESS, no compilation errors.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/session/EventContext.java
git add backend/src/main/java/io/github/xiaoailazy/coexistree/agent/service/AgentChatServiceImpl.java
git add backend/src/test/java/io/github/xiaoailazy/coexistree/shared/aspect/LlmCallTrackingAspectTest.java
git commit -m "refactor(audit): EventContext 增加 agentName 字段"
```

***

### Task 2: insertAgentTurn 改用 ObjectMapper 生成 metadata

**Files:**

- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/session/SessionEventService.java:61-77`
- [ ] **Step 1: 修改 insertAgentTurn 方法**

当前代码使用字符串拼接生成 metadata：

```java
entity.setMetadata("{\"agent\": \"" + escapeJson(agentName) + "\"}");
```

改为使用 ObjectMapper：

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public SessionEventEntity insertAgentTurn(Long agentTurnId, String conversationId,
                                           Long messageId, String agentName,
                                           String correlationId) {
    SessionEventEntity entity = new SessionEventEntity();
    entity.setId(agentTurnId);
    entity.setEventType("agent_turn");
    entity.setConversationId(conversationId);
    entity.setMessageId(messageId);
    entity.setParentEventId(null);
    entity.setRole("system");
    try {
        Map<String, Object> meta = new HashMap<>();
        meta.put("agent", agentName);
        entity.setMetadata(objectMapper.writeValueAsString(meta));
    } catch (Exception e) {
        entity.setMetadata("{\"agent\": \"unknown\"}");
    }
    entity.setStatus("streaming");
    entity.setCorrelationId(correlationId);
    entity.setCreatedAt(LocalDateTime.now());
    return eventRepository.save(entity);
}
```

- [ ] **Step 2: 在 SessionEventService 类顶部添加 import**

```java
// Add these imports at the top of the file:
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
```

- [ ] **Step 3: 在构造函数中注入 ObjectMapper**

```java
// Before constructor:
private final SessionEventRepository eventRepository;
private final SnowflakeIdGenerator snowflakeIdGenerator;
private final ObjectMapper objectMapper;

// Constructor:
public SessionEventService(SessionEventRepository eventRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           ObjectMapper objectMapper) {
    this.eventRepository = eventRepository;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
    this.objectMapper = objectMapper;
}
```

- [ ] **Step 4: 删除不再需要的 escapeJson 方法**

Remove the `escapeJson` method from the class (it was only used by insertAgentTurn).

- [ ] **Step 5: 编译验证**

Run:

```bash
cd /c/Git/CoExistree/backend && ./mvnw compile -q -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/session/SessionEventService.java
git commit -m "refactor(audit): insertAgentTurn 改用 ObjectMapper 生成 metadata"
```

***

### Task 3: 修复 ToolCallEventWriter — tool\_name 取错 + 增加 agent 名称

**Files:**

- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/agent/service/ToolCallEventWriter.java`
- [ ] **Step 1: 修复 writeToolResponse 的 tool\_name 取错**

当前第 56 行：

```java
meta.put("tool_name", requestEvent.getEventType());
```

应该从 requestEvent 的 metadata 中提取真实 tool\_name：

```java
public void writeToolResponse(SessionEventEntity requestEvent, String result, long durationMs) {
    var ctx = EventContextHolder.get();
    if (ctx == null || requestEvent == null) return;

    // Extract real tool_name from requestEvent metadata
    String realToolName = extractToolNameFromMetadata(requestEvent.getMetadata());

    String metadataJson;
    try {
        Map<String, Object> meta = new HashMap<>();
        meta.put("tool_name", realToolName);
        meta.put("elapsed_ms", durationMs);
        if (ctx.agentName() != null) {
            meta.put("agent", ctx.agentName());
        }
        metadataJson = objectMapper.writeValueAsString(meta);
    } catch (JsonProcessingException e) {
        metadataJson = "{}";
    }

    SessionEventEntity responseEvent = sessionEventService.insertEvent(
            "tool_response",
            ctx.conversationId(),
            ctx.triggerMessageId(),
            requestEvent.getId(),
            "tool",
            result,
            metadataJson,
            ctx.correlationId()
    );

    sessionEventService.updateEventDuration(requestEvent.getId(), durationMs);
}

private String extractToolNameFromMetadata(String metadataJson) {
    if (metadataJson == null || metadataJson.isBlank()) return "unknown";
    try {
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(metadataJson);
        JsonNode toolNameNode = node.get("tool_name");
        if (toolNameNode != null) return toolNameNode.asText();
    } catch (Exception ignored) {}
    return "unknown";
}
```

- [ ] **Step 2: 在 writeToolRequest 中增加 agent 名称**

```java
public SessionEventEntity writeToolRequest(String toolName, String args) {
    var ctx = EventContextHolder.get();
    if (ctx == null) return null;

    String metadataJson;
    try {
        Map<String, Object> meta = new HashMap<>();
        meta.put("tool_name", toolName);
        if (ctx.agentName() != null) {
            meta.put("agent", ctx.agentName());
        }
        metadataJson = objectMapper.writeValueAsString(meta);
    } catch (JsonProcessingException e) {
        metadataJson = "{}";
    }

    return sessionEventService.insertEvent(
            "tool_request",
            ctx.conversationId(),
            ctx.triggerMessageId(),
            ctx.agentTurnId(),
            "tool",
            args,
            metadataJson,
            ctx.correlationId()
    );
}
```

- [ ] **Step 3: 添加 JsonNode import**

Add at top of file:

```java
import com.fasterxml.jackson.databind.JsonNode;
```

- [ ] **Step 4: 编译验证**

Run:

```bash
cd /c/Git/CoExistree/backend && ./mvnw compile -q -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/agent/service/ToolCallEventWriter.java
git commit -m "fix(audit): 修复 tool_name 取错，tool 事件增加 agent 名称"
```

***

### Task 4: LlmCallTrackingAspect 增加 agent 名称到 metadata

**Files:**

- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/shared/aspect/LlmCallTrackingAspect.java`
- [ ] **Step 1: 在 trackLlmCall 方法中，向所有 metadata 添加 agent 名称**

找到方法中构建 metadata 的位置，在每次创建 metadata Map 后添加 agent 名称。

在 `trackLlmCall` 方法顶部，获取 agentName：

```java
EventContext ctx = EventContextHolder.get();
String conversationId = ctx != null ? ctx.conversationId() : null;
Long messageId = ctx != null ? ctx.triggerMessageId() : null;
Long parentEventId = ctx != null ? ctx.agentTurnId() : null;
String correlationId = ctx != null ? ctx.correlationId() : null;
String agentName = ctx != null ? ctx.agentName() : null;
```

在每次构建 metadata Map 后（共 3 处），添加：

```java
// After building any metadata Map, add:
if (agentName != null) {
    metadata.put("agent", agentName);
}
```

具体位置：

1. 第 55-59 行后的主 metadata map（用于异常时 fallback）
2. 第 80-84 行后的 requestMeta map（用于 llm\_request 正常事件）
3. 第 94-103 行后的 responseMeta map（用于 llm\_response 正常事件）
4. 第 119-122 行后的 errorMeta map（用于异常事件）

修改后的代码片段示例（以正常请求的 requestMeta 为例）：

```java
Map<String, Object> requestMeta = new HashMap<>();
requestMeta.put("model", resolveModel(model));
if (temperature != null) requestMeta.put("temperature", temperature);
requestMeta.put("scenario", scenario);
requestMeta.put("prompt", prompt);
if (agentName != null) requestMeta.put("agent", agentName);
```

同样对 responseMeta、errorMeta、以及顶层 metadata 做同样处理。

- [ ] **Step 2: 编译验证**

Run:

```bash
cd /c/Git/CoExistree/backend && ./mvnw compile -q -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/shared/aspect/LlmCallTrackingAspect.java
git commit -m "feat(audit): LLM 事件 metadata 增加 agent 名称"
```

***

### Task 5: SessionEventEntity metadata 字段加 @JdbcTypeCode

**Files:**

- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/chat/entity/SessionEventEntity.java`
- [ ] **Step 1: 添加 @JdbcTypeCode 注解到 metadata 字段**

```java
// Before:
@Column(name = "metadata", columnDefinition = "JSON")
private String metadata;

// After:
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Column(name = "metadata", columnDefinition = "JSON")
@JdbcTypeCode(SqlTypes.JSON)
private String metadata;
```

- [ ] **Step 2: 编译验证**

Run:

```bash
cd /c/Git/CoExistree/backend && ./mvnw compile -q -DskipTests
```

Expected: BUILD SUCCESS. If `@JdbcTypeCode` causes issues, revert this task — the double-escaping may be at the MySQL driver level and will be handled in the frontend instead.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/chat/entity/SessionEventEntity.java
git commit -m "fix(audit): metadata 字段添加 @JdbcTypeCode 修复 JSON 转义"
```

***

### Task 6: 更新后端测试

**Files:**

- Modify: `backend/src/test/java/io/github/xiaoailazy/coexistree/chat/controller/SessionAuditControllerTest.java`
- Modify: `backend/src/test/java/io/github/xiaoailazy/coexistree/shared/aspect/LlmCallTrackingAspectTest.java`
- Modify: `backend/src/test/resources/sql/session-audit-test-data.sql`
- [ ] **Step 1: 更新 SQL 测试数据 — tool\_response 使用正确的 tool\_name**

当前 session-audit-test-data.sql 第 31 行 tool\_response 的 metadata 已经是正确的 `{"tool_name": "search_knowledge_base", "elapsed_ms": 340}`，无需修改。

但 tool\_request 第 28 行的 metadata 缺少 agent 字段，添加：

```sql
-- Line 28: add agent to metadata
VALUES (5004, 'test-conv-audit-001', 'tool_request', 2001, 5001, 'tool', '{"query": "project architecture", "top_k": 5}', '{"tool_name": "search_knowledge_base", "agent": "root-agent"}', 340, 'success', CURRENT_TIMESTAMP);
```

- [ ] **Step 2: 在 SessionAuditControllerTest 中增加字段验证**

在 `getEvents_returnsEventsOrderedByTime` 测试中，增加对 agent 字段的验证：

```java
@Test
@WithMockUser(roles = "SUPER_ADMIN")
void getEvents_returnsEventsOrderedByTime() throws Exception {
    mockMvc.perform(get("/api/v1/admin/sessions/test-conv-audit-001/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].eventType").value("user_message"))
            .andExpect(jsonPath("$.data[2].metadata.agent").value("root-agent"))
            .andExpect(jsonPath("$.data[4].metadata.tool_name").value("search_knowledge_base"));
}
```

- [ ] **Step 3: 在 LlmCallTrackingAspectTest 中验证 agent 名称写入**

更新 `shouldTrackSuccessfulCall` 测试，验证 metadata 包含 agent 字段：

```java
@Test
void shouldTrackSuccessfulCall() throws Throwable {
    LlmClient.LlmResponse.Usage usage = new LlmClient.LlmResponse.Usage(
            100L, 200L, 300L, 50L);
    LlmClient.LlmResponse response = new LlmClient.LlmResponse(
            "resp_1", "hello", usage);

    setupMockCall(response, "test prompt", "gpt-4o", 0.5);

    SessionEventEntity mockEvent = new SessionEventEntity();
    mockEvent.setId(1L);
    when(sessionEventService.insertEvent(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockEvent);

    Object result = aspect.trackLlmCall(joinPoint);

    assertThat(result).isSameAs(response);
    verify(sessionEventService, atLeastOnce()).insertEvent(any(), any(), any(), any(), any(), any(), any(), any());

    // Verify agent name is included in metadata
    ArgumentCaptor<String> metaCaptor = ArgumentCaptor.forClass(String.class);
    verify(sessionEventService, atLeastOnce()).insertEvent(any(), any(), any(), any(), any(), any(), metaCaptor.capture(), any());
    String capturedMeta = metaCaptor.getValue();
    assertThat(capturedMeta).contains("\"agent\"");
}
```

注意：由于 test 中 EventContext 的 agentName 是 `null`，我们需要更新 setUp 传入一个非 null 值：

```java
@BeforeEach
void setUp() {
    aspect = new LlmCallTrackingAspect(sessionEventService, objectMapper);
    EventContextHolder.set(new EventContext(null, null, "conv-1", "corr-1", "root-agent"));
}
```

- [ ] **Step 4: 运行测试**

Run:

```bash
cd /c/Git/CoExistree/backend && powershell -ExecutionPolicy Bypass -File ./scripts/mvn-local.ps1 test -pl . -Dtest="SessionAuditControllerTest,LlmCallTrackingAspectTest"
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/
git commit -m "test(audit): 更新测试验证 agent 名称和 tool_name 修复"
```

***

### Task 7: 前端事件详情面板改为分栏布局

**Files:**

- Modify: `frontend/src/views/admin/components/SessionAuditView.vue`
- [ ] **Step 1: 替换展开后的详情面板模板**

找到当前 `.event-detail` 内的模板（约第 89-173 行），替换为以下分栏布局：

```vue
<!-- 展开的详情面板 -->
<div class="event-detail" :class="{ open: expandedEvent === evt.id }">
  <div class="detail-inner">
    <div class="detail-info">
      Event ID: {{ evt.id }}
      <template v-if="evt.correlationId">· Correlation ID: {{ evt.correlationId }}</template>
    </div>

    <!-- 区域一：关键信息（平铺表单） -->
    <div class="key-info-grid">
      <div v-for="(row, i) in getKeyInfoRows(evt)" :key="i" class="info-row">
        <span class="info-label">{{ row.label }}</span>
        <span class="info-value" :class="row.class">{{ row.value }}</span>
      </div>
    </div>

    <!-- 区域二：内容详情（保留折叠） -->
    <div class="detail-sections">
      <!-- System Prompt -->
      <div class="detail-section" v-if="isLlmRequest(evt) && hasContent(evt)">
        <div class="detail-toggle" @click.stop="toggleSection(evt.id, 'systemPrompt')">
          <span>System Prompt</span>
          <span class="arrow" :class="{ open: openSections[evt.id + '-systemPrompt'] }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: openSections[evt.id + '-systemPrompt'] }">
          <pre>{{ evt.content }}</pre>
        </div>
      </div>

      <!-- LLM Response -->
      <div class="detail-section" v-if="evt.eventType === 'llm_response'">
        <div class="detail-toggle" @click.stop="toggleSection(evt.id, 'response')">
          <span>LLM Response <span v-if="getTokens(evt)" class="badge-token">{{ getTokens(evt) }} tokens</span></span>
          <span class="arrow" :class="{ open: openSections[evt.id + '-response'] }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: openSections[evt.id + '-response'] }">
          <pre>{{ evt.content || '(无内容)' }}</pre>
        </div>
      </div>

      <!-- Tool Input -->
      <div class="detail-section" v-if="evt.eventType === 'tool_request'">
        <div class="detail-toggle" @click.stop="toggleSection(evt.id, 'toolInput')">
          <span>工具输入参数</span>
          <span class="arrow" :class="{ open: openSections[evt.id + '-toolInput'] }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: openSections[evt.id + '-toolInput'] }">
          <pre>{{ formatJson(evt.content) }}</pre>
        </div>
      </div>

      <!-- Tool Output -->
      <div class="detail-section" v-if="evt.eventType === 'tool_response'">
        <div class="detail-toggle" @click.stop="toggleSection(evt.id, 'toolOutput')">
          <span>工具输出结果</span>
          <span class="arrow" :class="{ open: openSections[evt.id + '-toolOutput'] }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: openSections[evt.id + '-toolOutput'] }">
          <pre>{{ formatJson(evt.content) }}</pre>
        </div>
      </div>

      <!-- User Message -->
      <div class="detail-section" v-if="evt.eventType === 'user_message'">
        <div class="detail-toggle" @click.stop="toggleSection(evt.id, 'userMessage')">
          <span>用户输入</span>
          <span class="arrow" :class="{ open: openSections[evt.id + '-userMessage'] }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: openSections[evt.id + '-userMessage'] }">
          <pre>{{ evt.content || '(无内容)' }}</pre>
        </div>
      </div>

      <!-- Agent Metadata -->
      <div class="detail-section" v-if="evt.eventType === 'agent_turn'">
        <div class="detail-toggle" @click.stop="toggleSection(evt.id, 'agentMeta')">
          <span>Agent 元数据</span>
          <span class="arrow" :class="{ open: openSections[evt.id + '-agentMeta'] }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: openSections[evt.id + '-agentMeta'] }">
          <pre>{{ formatJson(evt.metadata) }}</pre>
        </div>
      </div>

      <!-- Raw Metadata -->
      <div class="detail-section" v-if="evt.metadata">
        <div class="detail-toggle" @click.stop="toggleSection(evt.id, 'rawMeta')">
          <span>原始元数据 JSON</span>
          <span class="arrow" :class="{ open: openSections[evt.id + '-rawMeta'] }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: openSections[evt.id + '-rawMeta'] }">
          <pre>{{ formatJson(evt.metadata) }}</pre>
        </div>
      </div>
    </div>
  </div>
</div>
```

- [ ] **Step 2: 添加 getKeyInfoRows 函数**

在 `<script setup>` 中添加：

```javascript
function getKeyInfoRows(evt) {
  const meta = parseMeta(evt.metadata) || {}
  const rows = []

  // Agent — all agent-related events
  if (evt.eventType.startsWith('agent_') || evt.eventType.startsWith('tool_') || evt.eventType.startsWith('llm_')) {
    const agent = meta.agent
    if (agent) rows.push({ label: 'Agent', value: agent })
  }

  // Tool name
  if (evt.eventType === 'tool_request' || evt.eventType === 'tool_response') {
    const tool = meta.tool_name
    if (tool) rows.push({ label: '工具', value: tool })
  }

  // Model
  if (evt.eventType === 'llm_request' || evt.eventType === 'llm_response') {
    const model = meta.model
    if (model) rows.push({ label: '模型', value: model })
  }

  // Role
  if (evt.role) rows.push({ label: '角色', value: evt.role })

  // Duration
  if (evt.durationMs) {
    rows.push({ label: '耗时', value: formatDuration(evt.durationMs) })
  } else if (meta.elapsed_ms) {
    rows.push({ label: '耗时', value: formatDuration(meta.elapsed_ms) })
  }

  // Status
  if (evt.status) {
    rows.push({
      label: '状态',
      value: evt.status === 'success' ? '✓ success' : '✗ ' + evt.status,
      class: evt.status === 'success' ? 'status-success' : 'status-failed'
    })
  }

  // Event time
  if (evt.createdAt) rows.push({ label: '事件时间', value: formatTime(evt.createdAt) })

  // Message ID
  if (evt.messageId && (evt.eventType === 'user_message' || evt.eventType.startsWith('llm_'))) {
    rows.push({ label: '消息 ID', value: evt.messageId })
  }

  return rows
}
```

- [ ] **Step 3: 添加关键信息网格样式**

在 `<style scoped>` 中添加：

```css
/* 关键信息网格 */
.key-info-grid {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 6px 12px;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: #1a2332;
  border-radius: 6px;
  border: 1px solid #253349;
}

.info-row {
  display: contents;
}

.info-label {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.info-value {
  font-size: 11px;
  color: #cbd5e1;
}

.info-value.status-success {
  color: #34d399;
  font-weight: 500;
}

.info-value.status-failed {
  color: #f87171;
  font-weight: 500;
}

.detail-sections {
  margin-top: 8px;
}
```

- [ ] **Step 4: 前端验证**

确保开发服务器正在运行：

```bash
cd /c/Git/CoExistree/frontend && npm run dev
```

在浏览器中访问 Admin → Session Audit 页面，点击任意事件展开，验证：

1. 关键信息区域以表单形式直接展示（Agent、工具名、模型、耗时、状态等）
2. 长内容仍可通过折叠块查看
3. 空值的行不显示

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/admin/components/SessionAuditView.vue
git commit -m "feat(audit): 事件详情面板改为分栏布局 — 关键信息平铺 + 长内容折叠"
```

***

### Task 8: 最终验证与清理

- [ ] **Step 1: 运行全部后端测试**

Run:

```bash
cd /c/Git/CoExistree/backend && powershell -ExecutionPolicy Bypass -File ./scripts/mvn-local.ps1 test
```

Expected: All tests PASS.

- [ ] **Step 2: 前端构建检查**

Run:

```bash
cd /c/Git/CoExistree/frontend && npm run build
```

Expected: Build succeeds with no errors.

- [ ] **Step 3: 确认所有改动已提交**

```bash
git status
```

Expected: working tree clean.
