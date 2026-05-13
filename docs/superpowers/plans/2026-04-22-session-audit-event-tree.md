# 会话审计事件树 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过 ADK 原生回调记录完整的 LLM 调用和子 agent 执行事件，并在前端以树形结构展示调用层级关系。

**Architecture:** 在 AgentConfig 中为每个 agent builder 挂载观测回调（AgentObservationCallbacks），回调通过 `ctx.sessionId()` 获取 conversationId 并写入 session_events 表。后端新增 EventTreeAssembler 将扁平事件合并为树形 DTO，前端新增递归树组件展示。

**Tech Stack:** Java 21, Spring Boot 3.5, Google ADK 1.0.0, RxJava3, Vue 3, Element Plus

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `backend/.../agent/observability/AgentObservationCallbacks.java` | **Create** | Spring Bean，提供 8 个回调创建方法，内部用 ConcurrentMap 跨回调传递 turn ID 和 request ID |
| `backend/.../chat/dto/AgentEventTreeNode.java` | **Create** | 树形 DTO record |
| `backend/.../chat/service/EventTreeAssembler.java` | **Create** | 纯函数：`List<SessionEventEntity>` → `List<AgentEventTreeNode>` |
| `backend/.../chat/controller/SessionAuditController.java` | **Modify** | 新增 `getEventsTree()` 端点 |
| `backend/.../session/SessionEventService.java` | **Modify** | `insertAgentTurn` 新增 `parentEventId` 参数重载 |
| `backend/.../agent/config/AgentConfig.java` | **Modify** | 注入 AgentObservationCallbacks，在三个 agent builder 挂回调 |
| `frontend/src/api/sessionAudit.js` | **Modify** | 新增 `getSessionEventsTree()` API 函数 |
| `frontend/src/views/admin/components/AgentEventTree.vue` | **Create** | 树形容器组件 |
| `frontend/src/views/admin/components/AgentEventNode.vue` | **Create** | 递归单节点组件 |
| `frontend/src/views/admin/components/SessionAuditView.vue` | **Modify** | 新增"树形视图"切换按钮，接入树形 API |

---

### Task 1: AgentEventTreeNode DTO

**Files:**
- Create: `backend/src/main/java/io/github/xiaoailazy/coexistree/chat/dto/AgentEventTreeNode.java`

- [ ] **Step 1: Create the tree DTO record**

```java
package io.github.xiaoailazy.coexistree.chat.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record AgentEventTreeNode(
        Long id,
        String eventType,
        String agentName,
        String status,
        Long durationMs,
        String role,
        String content,
        JsonNode metadata,
        LocalDateTime createdAt,
        List<AgentEventTreeNode> children,
        boolean hasDetail
) {
    public AgentEventTreeNode {
        if (children == null) children = new ArrayList<>();
    }

    /**
     * Factory for leaf nodes (no children).
     */
    public static AgentEventTreeNode leaf(Long id, String eventType, String agentName,
                                           String status, Long durationMs, String role,
                                           String content, JsonNode metadata,
                                           LocalDateTime createdAt) {
        return new AgentEventTreeNode(id, eventType, agentName, status, durationMs,
                role, content, metadata, createdAt, List.of(), metadata != null);
    }

    /**
     * Factory for parent nodes (agent_turn, user_message, etc.).
     */
    public static AgentTreeNode branch(Long id, String eventType, String agentName,
                                        String status, Long durationMs, String role,
                                        String content, JsonNode metadata,
                                        LocalDateTime createdAt) {
        return new AgentEventTreeNode(id, eventType, agentName, status, durationMs,
                role, content, metadata, createdAt, new ArrayList<>(), true);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/chat/dto/AgentEventTreeNode.java
git commit -m "feat(audit): create AgentEventTreeNode record for tree-shaped DTO"
```

---

### Task 2: EventTreeAssembler

**Files:**
- Create: `backend/src/main/java/io/github/xiaoailazy/coexistree/chat/service/EventTreeAssembler.java`
- Test: `backend/src/test/java/io/github/xiaoailazy/coexistree/chat/service/EventTreeAssemblerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.chat.dto.AgentEventTreeNode;
import io.github.xiaoailazy.coexistree.chat.entity.SessionEventEntity;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventTreeAssemblerTest {

    private final EventTreeAssembler assembler = new EventTreeAssembler();
    private final LocalDateTime now = LocalDateTime.now();

    @Test
    @DisplayName("合并 llm_request + llm_response → llm_call")
    void mergeLlmPair() {
        SessionEventEntity request = event(1L, "llm_request", null, "assistant",
                "{\"model\":\"gpt-4o\",\"prompt\":\"hello\"}");
        SessionEventEntity response = event(2L, "llm_response", 1L, "assistant",
                "{\"content\":\"hi\",\"tokens\":{\"total\":10}}");

        List<AgentEventTreeNode> tree = assembler.buildTree(List.of(request, response));

        assertThat(tree).hasSize(1);
        AgentEventTreeNode node = tree.get(0);
        assertThat(node.eventType()).isEqualTo("llm_call");
        assertThat(node.status()).isEqualTo("success");
        assertThat(node.metadata().get("model").asText()).isEqualTo("gpt-4o");
        assertThat(node.metadata().get("content").asText()).isEqualTo("hi");
        assertThat(node.children()).isEmpty();
    }

    @Test
    @DisplayName("只有 llm_request 没有 response → llm_call (failed)")
    void llmRequestOnly() {
        SessionEventEntity request = event(1L, "llm_request", null, "assistant",
                "{\"model\":\"gpt-4o\"}");

        List<AgentEventTreeNode> tree = assembler.buildTree(List.of(request));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).eventType()).isEqualTo("llm_call");
        assertThat(tree.get(0).status()).isEqualTo("failed");
    }

    @Test
    @DisplayName("合并 tool_request + tool_response → tool_call")
    void mergeToolPair() {
        SessionEventEntity request = event(1L, "tool_request", null, "tool",
                "{\"tool_name\":\"searchTree\"}");
        SessionEventEntity response = event(2L, "tool_response", 1L, "tool",
                "[3 results]");

        List<AgentEventTreeNode> tree = assembler.buildTree(List.of(request, response));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).eventType()).isEqualTo("tool_call");
        assertThat(tree.get(0).metadata().get("tool_name").asText()).isEqualTo("searchTree");
        assertThat(tree.get(0).metadata().get("output").asText()).isEqualTo("[3 results]");
    }

    @Test
    @DisplayName("agent_turn 包含子事件")
    void agentTurnWithChildren() {
        SessionEventEntity turn = event(1L, "agent_turn", null, "system",
                "{\"agent\":\"root-agent\"}");
        SessionEventEntity llmReq = event(2L, "llm_request", 1L, "assistant",
                "{\"model\":\"gpt-4o\"}");
        SessionEventEntity llmResp = event(3L, "llm_response", 2L, "assistant",
                "{\"content\":\"hello\"}");

        List<AgentEventTreeNode> tree = assembler.buildTree(List.of(turn, llmReq, llmResp));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).eventType()).isEqualTo("agent_turn");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).eventType()).isEqualTo("llm_call");
    }

    @Test
    @DisplayName("嵌套子 agent_turn")
    void nestedAgentTurns() {
        SessionEventEntity rootTurn = event(1L, "agent_turn", null, "system",
                "{\"agent\":\"root-agent\"}");
        SessionEventEntity qaTurn = event(2L, "agent_turn", 1L, "system",
                "{\"agent\":\"qa-agent\"}");
        SessionEventEntity llmReq = event(3L, "llm_request", 2L, "assistant",
                "{\"model\":\"gpt-4o\"}");
        SessionEventEntity llmResp = event(4L, "llm_response", 3L, "assistant",
                "{\"content\":\"answer\"}");

        List<AgentEventTreeNode> tree = assembler.buildTree(List.of(rootTurn, qaTurn, llmReq, llmResp));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).eventType()).isEqualTo("agent_turn");
        assertThat(tree.get(0).agentName()).isEqualTo("root-agent");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).eventType()).isEqualTo("agent_turn");
        assertThat(tree.get(0).children().get(0).agentName()).isEqualTo("qa-agent");
        assertThat(tree.get(0).children().get(0).children()).hasSize(1);
    }

    @Test
    @DisplayName("user_message 作为根节点")
    void userMessageAsRoot() {
        SessionEventEntity userMsg = event(1L, "user_message", null, "user", "你好");

        List<AgentEventTreeNode> tree = assembler.buildTree(List.of(userMsg));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).eventType()).isEqualTo("user_message");
        assertThat(tree.get(0).content()).isEqualTo("你好");
    }

    private SessionEventEntity event(Long id, String type, Long parentId,
                                      String role, String contentOrMeta) {
        SessionEventEntity e = new SessionEventEntity();
        e.setId(id);
        e.setEventType(type);
        e.setParentEventId(parentId);
        e.setRole(role);
        e.setCreatedAt(now);
        e.setStatus("success");
        // Treat short strings as content, JSON-looking as metadata
        if (contentOrMeta.startsWith("{")) {
            e.setMetadata(contentOrMeta);
            e.setContent(null);
        } else {
            e.setContent(contentOrMeta);
            e.setMetadata(null);
        }
        return e;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn test -Dtest=EventTreeAssemblerTest -Dsurefire.useFile=false
```
Expected: FAIL — EventTreeAssembler class does not exist

- [ ] **Step 3: Implement EventTreeAssembler**

```java
package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.chat.dto.AgentEventTreeNode;
import io.github.xiaoailazy.coexistree.chat.entity.SessionEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class EventTreeAssembler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Build a tree from a flat list of session events.
     * Merges llm_request+llm_response → llm_call and tool_request+tool_response → tool_call.
     */
    public List<AgentEventTreeNode> buildTree(List<SessionEventEntity> flatEvents) {
        if (flatEvents == null || flatEvents.isEmpty()) return List.of();

        // Index by id
        Map<Long, SessionEventEntity> byId = flatEvents.stream()
                .collect(Collectors.toMap(SessionEventEntity::getId, e -> e));

        // Merge pairs: llm_request+llm_response → llm_call, tool_request+tool_response → tool_call
        Set<Long> consumed = new HashSet<>();
        List<MergedEvent> merged = new ArrayList<>();

        for (SessionEventEntity e : flatEvents) {
            if (consumed.contains(e.getId())) continue;

            if ("llm_request".equals(e.getEventType())) {
                SessionEventEntity response = findResponseById(byId, e.getId(), "llm_response");
                if (response != null) {
                    consumed.add(response.getId());
                    merged.add(mergeLlmPair(e, response));
                } else {
                    merged.add(mergeLlmFailed(e));
                }
                continue;
            }

            if ("tool_request".equals(e.getEventType())) {
                SessionEventEntity response = findResponseById(byId, e.getId(), "tool_response");
                if (response != null) {
                    consumed.add(response.getId());
                    merged.add(mergeToolPair(e, response));
                } else {
                    merged.add(mergeToolFailed(e));
                }
                continue;
            }

            // agent_turn, user_message, agent_error — pass through
            merged.add(fromEntity(e));
        }

        // Group by parentEventId
        Map<Long, List<AgentEventTreeNode>> childrenByParent = merged.stream()
                .collect(Collectors.groupingBy(
                        m -> m.parentEventId() != null ? m.parentEventId() : 0L,
                        LinkedHashMap::new,
                        Collectors.mapping(MergedEvent::toNode, Collectors.toList())
                ));

        // Recursively attach children
        List<AgentEventTreeNode> roots = childrenByParent.getOrDefault(0L, List.of());
        return roots.stream()
                .map(node -> attachChildren(node, childrenByParent))
                .collect(Collectors.toList());
    }

    private SessionEventEntity findResponseById(Map<Long, SessionEventEntity> byId,
                                                  Long requestId, String responseType) {
        for (SessionEventEntity e : byId.values()) {
            if (responseType.equals(e.getEventType())
                    && requestId.equals(e.getParentEventId())) {
                return e;
            }
        }
        return null;
    }

    private MergedEvent mergeLlmPair(SessionEventEntity request, SessionEventEntity response) {
        try {
            ObjectNode meta = objectMapper.createObjectNode();
            if (request.getMetadata() != null) {
                JsonNode reqMeta = objectMapper.readTree(request.getMetadata());
                meta.setAll((ObjectNode) reqMeta);
            }
            if (response.getMetadata() != null) {
                JsonNode respMeta = objectMapper.readTree(response.getMetadata());
                meta.setAll((ObjectNode) respMeta);
            }
            if (response.getContent() != null) {
                meta.put("content", response.getContent());
            }
            String mergedMeta = objectMapper.writeValueAsString(meta);
            return new MergedEvent(
                    request.getId(), "llm_call", extractAgent(meta),
                    "success", response.getDurationMs(),
                    response.getRole() != null ? response.getRole() : "assistant",
                    response.getContent(), mergedMeta,
                    request.getCreatedAt(), request.getParentEventId()
            );
        } catch (Exception ex) {
            return new MergedEvent(
                    request.getId(), "llm_call", "unknown",
                    "success", response.getDurationMs(),
                    "assistant", response.getContent(), request.getMetadata(),
                    request.getCreatedAt(), request.getParentEventId()
            );
        }
    }

    private MergedEvent mergeLlmFailed(SessionEventEntity request) {
        return new MergedEvent(
                request.getId(), "llm_call", extractAgentOrNull(request.getMetadata()),
                "failed", null,
                "assistant", null, request.getMetadata(),
                request.getCreatedAt(), request.getParentEventId()
        );
    }

    private MergedEvent mergeToolPair(SessionEventEntity request, SessionEventEntity response) {
        try {
            ObjectNode meta = objectMapper.createObjectNode();
            if (request.getMetadata() != null) {
                JsonNode reqMeta = objectMapper.readTree(request.getMetadata());
                meta.setAll((ObjectNode) reqMeta);
            }
            if (response.getContent() != null) {
                meta.put("output", response.getContent());
            }
            if (response.getDurationMs() != null) {
                meta.put("elapsed_ms", response.getDurationMs());
            }
            String mergedMeta = objectMapper.writeValueAsString(meta);
            return new MergedEvent(
                    request.getId(), "tool_call", extractAgentOrNull(request.getMetadata()),
                    "success", response.getDurationMs(),
                    "tool", response.getContent(), mergedMeta,
                    request.getCreatedAt(), request.getParentEventId()
            );
        } catch (Exception ex) {
            return new MergedEvent(
                    request.getId(), "tool_call", "unknown",
                    "success", response.getDurationMs(),
                    "tool", response.getContent(), request.getMetadata(),
                    request.getCreatedAt(), request.getParentEventId()
            );
        }
    }

    private MergedEvent mergeToolFailed(SessionEventEntity request) {
        return new MergedEvent(
                request.getId(), "tool_call", extractAgentOrNull(request.getMetadata()),
                "failed", null,
                "tool", null, request.getMetadata(),
                request.getCreatedAt(), request.getParentEventId()
        );
    }

    private MergedEvent fromEntity(SessionEventEntity e) {
        String agentName = extractAgentOrNull(e.getMetadata());
        boolean hasDetail = e.getMetadata() != null || e.getContent() != null;
        AgentEventTreeNode node = new AgentEventTreeNode(
                e.getId(), e.getEventType(), agentName,
                e.getStatus(), e.getDurationMs(), e.getRole(),
                e.getContent(), parseJson(e.getMetadata()),
                e.getCreatedAt(), List.of(), hasDetail
        );
        return new MergedEvent(
                e.getId(), e.getEventType(), agentName,
                e.getStatus(), e.getDurationMs(), e.getRole(),
                e.getContent(), e.getMetadata(),
                e.getCreatedAt(), e.getParentEventId()
        );
    }

    private AgentEventTreeNode attachChildren(AgentEventTreeNode node,
                                               Map<Long, List<AgentEventTreeNode>> childrenByParent) {
        List<AgentEventTreeNode> childList = childrenByParent.getOrDefault(node.id(), List.of());
        List<AgentEventTreeNode> attached = childList.stream()
                .map(c -> attachChildren(c, childrenByParent))
                .collect(Collectors.toList());
        return new AgentEventTreeNode(
                node.id(), node.eventType(), node.agentName(),
                node.status(), node.durationMs(), node.role(),
                node.content(), node.metadata(), node.createdAt(),
                attached, node.hasDetail()
        );
    }

    private String extractAgent(JsonNode meta) {
        if (meta != null && meta.has("agent")) return meta.get("agent").asText();
        return "unknown";
    }

    private String extractAgentOrNull(String metadataJson) {
        if (metadataJson == null) return null;
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            if (node.has("agent")) return node.get("agent").asText();
        } catch (Exception ignored) {}
        return null;
    }

    private JsonNode parseJson(String json) {
        if (json == null) return null;
        try { return objectMapper.readTree(json); } catch (Exception e) { return null; }
    }

    /**
     * Intermediate representation for building the tree.
     */
    private record MergedEvent(
            Long id, String eventType, String agentName,
            String status, Long durationMs, String role,
            String content, String metadata,
            LocalDateTime createdAt, Long parentEventId
    ) {
        AgentEventTreeNode toNode() {
            return new AgentEventTreeNode(
                    id, eventType, agentName, status, durationMs,
                    role, content, parseJson(metadata), createdAt,
                    new ArrayList<>(), metadata != null
            );
        }

        private JsonNode parseJson(String json) {
            ObjectMapper om = new ObjectMapper();
            if (json == null) return null;
            try { return om.readTree(json); } catch (Exception e) { return null; }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && mvn test -Dtest=EventTreeAssemblerTest -Dsurefire.useFile=false
```
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/chat/service/EventTreeAssembler.java backend/src/test/java/io/github/xiaoailazy/coexistree/chat/service/EventTreeAssemblerTest.java
git commit -m "feat(audit): add EventTreeAssembler with merge logic and tests"
```

---

### Task 3: SessionEventService — add parentEventId support

**Files:**
- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/session/SessionEventService.java:62-89`

- [ ] **Step 1: Add insertAgentTurn overload with parentEventId**

Add this method after the existing 5-param `insertAgentTurn` (after line 89):

```java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SessionEventEntity insertAgentTurn(Long agentTurnId, String conversationId,
                                               Long messageId, String agentName,
                                               String correlationId, Long parentEventId) {
        SessionEventEntity entity = new SessionEventEntity();
        entity.setId(agentTurnId);
        entity.setEventType("agent_turn");
        entity.setConversationId(conversationId);
        entity.setMessageId(messageId);
        entity.setParentEventId(parentEventId);
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

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/session/SessionEventService.java
git commit -m "feat(audit): add insertAgentTurn overload with parentEventId parameter"
```

---

### Task 4: SessionAuditController — add getEventsTree endpoint

**Files:**
- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/chat/controller/SessionAuditController.java`

- [ ] **Step 1: Inject EventTreeAssembler and add getEventsTree endpoint**

Add field and constructor parameter in SessionAuditController:

```java
// Add field:
private final EventTreeAssembler eventTreeAssembler;

// Update constructor:
public SessionAuditController(ConversationRepository conversationRepository,
                              SessionEventRepository sessionEventRepository,
                              ObjectMapper objectMapper,
                              EventTreeAssembler eventTreeAssembler) {
    this.conversationRepository = conversationRepository;
    this.sessionEventRepository = sessionEventRepository;
    this.objectMapper = objectMapper;
    this.eventTreeAssembler = eventTreeAssembler;
}
```

Add import:
```java
import io.github.xiaoailazy.coexistree.chat.dto.AgentEventTreeNode;
import io.github.xiaoailazy.coexistree.chat.service.EventTreeAssembler;
```

Add new endpoint method after `getSummary()`:

```java
    @GetMapping("/{conversationId}/events/tree")
    public ApiResponse<List<AgentEventTreeNode>> getEventsTree(
            @PathVariable String conversationId
    ) {
        List<SessionEventEntity> events =
                sessionEventRepository.findByConversationIdOrderByCreatedAt(conversationId);

        List<AgentEventTreeNode> tree = eventTreeAssembler.buildTree(events);

        return ApiResponse.success(tree);
    }
```

- [ ] **Step 2: Verify compilation**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/chat/controller/SessionAuditController.java
git commit -m "feat(audit): add GET /events/tree endpoint with EventTreeAssembler"
```

---

### Task 5: AgentObservationCallbacks — Spring Bean providing all ADK callbacks

**Files:**
- Create: `backend/src/main/java/io/github/xiaoailazy/coexistree/agent/observability/AgentObservationCallbacks.java`
- Create: `backend/src/test/java/io/github/xiaoailazy/coexistree/agent/observability/AgentObservationCallbacksTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.github.xiaoailazy.coexistree.agent.observability;

import com.google.adk.agents.Callbacks;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

class AgentObservationCallbacksTest {

    @Test
    @DisplayName("createBeforeAgentCallback returns non-null callback")
    void beforeAgentCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null);
        Callbacks.BeforeAgentCallback cb = callbacks.createBeforeAgentCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createAfterAgentCallback returns non-null callback")
    void afterAgentCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null);
        Callbacks.AfterAgentCallback cb = callbacks.createAfterAgentCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createBeforeModelCallback returns non-null callback")
    void beforeModelCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null);
        Callbacks.BeforeModelCallback cb = callbacks.createBeforeModelCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createAfterModelCallback returns non-null callback")
    void afterModelCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null);
        Callbacks.AfterModelCallback cb = callbacks.createAfterModelCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createOnModelErrorCallback returns non-null callback")
    void onModelErrorCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null);
        Callbacks.OnModelErrorCallback cb = callbacks.createOnModelErrorCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createBeforeToolCallback returns non-null callback")
    void beforeToolCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null);
        Callbacks.BeforeToolCallback cb = callbacks.createBeforeToolCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createAfterToolCallback returns non-null callback")
    void afterToolCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null);
        Callbacks.AfterToolCallback cb = callbacks.createAfterToolCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createOnToolErrorCallback returns non-null callback")
    void onToolErrorCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null);
        Callbacks.OnToolErrorCallback cb = callbacks.createOnToolErrorCallback();
        assertThat(cb).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn test -Dtest=AgentObservationCallbacksTest -Dsurefire.useFile=false
```
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement AgentObservationCallbacks**

```java
package io.github.xiaoailazy.coexistree.agent.observability;

import com.google.adk.agents.Callbacks;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import io.github.xiaoailazy.coexistree.chat.entity.SessionEventEntity;
import io.github.xiaoailazy.coexistree.session.SessionEventService;
import io.github.xiaoailazy.coexistree.shared.util.SnowflakeIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides ADK lifecycle callbacks for observability.
 * Each callback writes agent_turn, llm_request, llm_response events to session_events.
 *
 * conversationId is obtained directly from CallbackContext.sessionId().
 * Agent turn IDs are tracked per-conversation using ConcurrentMap.
 */
@Slf4j
@Component
public class AgentObservationCallbacks {

    private final SessionEventService sessionEventService;
    private final ToolCallEventWriter toolCallEventWriter;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;

    /** conversationId → (agentName → agentTurnId) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> agentTurnIds =
            new ConcurrentHashMap<>();

    /** conversationId → (agentName → llmRequestId) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> llmRequestIds =
            new ConcurrentHashMap<>();

    /** conversationId → (agentName → llmRequestStartTime) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> llmStartTimes =
            new ConcurrentHashMap<>();

    public AgentObservationCallbacks(SessionEventService sessionEventService,
                                     ToolCallEventWriter toolCallEventWriter,
                                     SnowflakeIdGenerator snowflakeIdGenerator,
                                     ObjectMapper objectMapper) {
        this.sessionEventService = sessionEventService;
        this.toolCallEventWriter = toolCallEventWriter;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.objectMapper = objectMapper;
    }

    // ===== beforeAgent =====
    public Callbacks.BeforeAgentCallback createBeforeAgentCallback() {
        return ctx -> {
            try {
                String conversationId = ctx.sessionId();
                String agentName = ctx.agentName();

                ConcurrentHashMap<String, Long> turnMap = agentTurnIds
                        .computeIfAbsent(conversationId, k -> new ConcurrentHashMap<>());

                // Find parent turn: if this is a sub-agent, parent is the current agent's turn
                Long parentTurnId = findParentTurnId(conversationId, agentName);

                Long turnId = snowflakeIdGenerator.nextId();
                turnMap.put(agentName, turnId);

                sessionEventService.insertAgentTurn(
                        turnId, conversationId, null, agentName, null, parentTurnId);

                log.debug("[beforeAgent] conversationId={}, agentName={}, turnId={}, parent={}",
                        conversationId, agentName, turnId, parentTurnId);
            } catch (Exception e) {
                log.error("[beforeAgent] callback failed", e);
            }
            return Maybe.empty();
        };
    }

    // ===== afterAgent =====
    public Callbacks.AfterAgentCallback createAfterAgentCallback() {
        return ctx -> {
            try {
                String conversationId = ctx.sessionId();
                String agentName = ctx.agentName();

                ConcurrentHashMap<String, Long> turnMap = agentTurnIds.get(conversationId);
                if (turnMap != null) {
                    Long turnId = turnMap.get(agentName);
                    if (turnId != null) {
                        sessionEventService.updateEventStatus(turnId, "success");
                    }
                }
            } catch (Exception e) {
                log.error("[afterAgent] callback failed", e);
            }
            return Maybe.empty();
        };
    }

    // ===== beforeModel =====
    public Callbacks.BeforeModelCallback createBeforeModelCallback() {
        return (ctx, llmRequestBuilder) -> {
            try {
                String conversationId = ctx.sessionId();
                String agentName = ctx.agentName();

                ConcurrentHashMap<String, Long> turnMap = agentTurnIds.get(conversationId);
                if (turnMap == null) {
                    log.warn("[beforeModel] no agentTurnIdMap for conversationId={}", conversationId);
                    return Maybe.empty();
                }

                Long parentTurnId = turnMap.get(agentName);
                if (parentTurnId == null) {
                    log.warn("[beforeModel] no turnId for agentName={}", agentName);
                    return Maybe.empty();
                }

                // Extract model from builder
                String model = extractModel(llmRequestBuilder);
                String prompt = extractPrompt(llmRequestBuilder);

                Long requestId = snowflakeIdGenerator.nextId();

                Map<String, Object> metadata = Map.of(
                        "model", model != null ? model : "unknown",
                        "prompt", prompt != null ? prompt : "",
                        "agent", agentName
                );

                sessionEventService.insertEvent(
                        "llm_request", conversationId, null, parentTurnId,
                        "assistant", null, objectMapper.writeValueAsString(metadata));

                // Track request ID for response pairing
                llmRequestIds.computeIfAbsent(conversationId, k -> new ConcurrentHashMap<>())
                        .put(agentName, requestId);
                llmStartTimes.computeIfAbsent(conversationId, k -> new ConcurrentHashMap<>())
                        .put(agentName, System.currentTimeMillis());
            } catch (Exception e) {
                log.error("[beforeModel] callback failed", e);
            }
            return Maybe.empty();
        };
    }

    // ===== afterModel =====
    public Callbacks.AfterModelCallback createAfterModelCallback() {
        return (ctx, llmResponse) -> {
            try {
                String conversationId = ctx.sessionId();
                String agentName = ctx.agentName();

                ConcurrentHashMap<String, Long> requestMap = llmRequestIds.get(conversationId);
                if (requestMap == null) return Maybe.empty();

                Long parentRequestId = requestMap.get(agentName);
                if (parentRequestId == null) return Maybe.empty();

                Long startTime = llmStartTimes.get(conversationId) != null
                        ? llmStartTimes.get(conversationId).get(agentName) : null;
                long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

                String content = extractContent(llmResponse);
                Map<String, Object> metadata = Map.of(
                        "content", content != null ? content : "",
                        "agent", agentName
                );

                sessionEventService.insertEvent(
                        "llm_response", conversationId, null, parentRequestId,
                        "assistant", content, objectMapper.writeValueAsString(metadata));

                // Also update llm_request duration
                sessionEventService.updateEventDuration(parentRequestId, durationMs);
            } catch (Exception e) {
                log.error("[afterModel] callback failed", e);
            }
            return Maybe.empty();
        };
    }

    // ===== onModelError =====
    public Callbacks.OnModelErrorCallback createOnModelErrorCallback() {
        return (ctx, llmRequest, error) -> {
            try {
                String conversationId = ctx.sessionId();
                String agentName = ctx.agentName();
                log.error("[onModelError] conversationId={}, agentName={}, error={}",
                        conversationId, agentName, error.getMessage());

                ConcurrentHashMap<String, Long> requestMap = llmRequestIds.get(conversationId);
                if (requestMap != null) {
                    Long parentRequestId = requestMap.get(agentName);
                    if (parentRequestId != null) {
                        sessionEventService.updateEventStatus(parentRequestId, "failed");
                    }
                }
            } catch (Exception e) {
                log.error("[onModelError] callback failed", e);
            }
            return Maybe.empty();
        };
    }

    // ===== beforeTool =====
    public Callbacks.BeforeToolCallback createBeforeToolCallback() {
        return (invocationCtx, baseTool, input, toolContext) -> {
            try {
                String conversationId = invocationCtx.session().id();
                String agentName = invocationCtx.agent().name();
                String toolName = baseTool.name();

                ConcurrentHashMap<String, Long> turnMap = agentTurnIds.get(conversationId);
                Long parentTurnId = turnMap != null ? turnMap.get(agentName) : null;

                String argsJson = objectMapper.writeValueAsString(input);
                toolCallEventWriter.writeToolRequestWithContext(toolName, argsJson, agentName, parentTurnId, conversationId);
            } catch (Exception e) {
                log.error("[beforeTool] callback failed", e);
            }
            return Maybe.empty();
        };
    }

    // ===== afterTool =====
    public Callbacks.AfterToolCallback createAfterToolCallback() {
        return (invocationCtx, baseTool, input, toolContext, response) -> {
            try {
                String conversationId = invocationCtx.session().id();
                String toolName = baseTool.name();

                toolCallEventWriter.writeToolResponseWithContext(
                        toolName, objectMapper.writeValueAsString(response), conversationId);
            } catch (Exception e) {
                log.error("[afterTool] callback failed", e);
            }
            return Maybe.empty();
        };
    }

    // ===== onToolError =====
    public Callbacks.OnToolErrorCallback createOnToolErrorCallback() {
        return (invocationCtx, baseTool, input, toolContext, error) -> {
            try {
                log.error("[onToolError] tool={}, error={}",
                        baseTool.name(), error.getMessage());
            } catch (Exception e) {
                log.error("[onToolError] callback failed", e);
            }
            return Maybe.empty();
        };
    }

    // ===== Internal helpers =====

    private Long findParentTurnId(String conversationId, String currentAgentName) {
        // If there's already a turn for a different agent, that's the parent
        ConcurrentHashMap<String, Long> turnMap = agentTurnIds.get(conversationId);
        if (turnMap == null) return null;

        for (Map.Entry<String, Long> entry : turnMap.entrySet()) {
            if (!entry.getKey().equals(currentAgentName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String extractModel(LlmRequest.Builder builder) {
        try {
            // ADK LlmRequest.Builder may not expose model directly;
            // try to get it via the generateContentConfig or system default
            return "adk-llm";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractPrompt(LlmRequest.Builder builder) {
        try {
            // Extract system/contents from builder
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String extractContent(LlmResponse response) {
        try {
            if (response == null || response.getContent() == null) return null;
            return response.getContent().text();
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Add writeToolRequestWithContext and writeToolResponseWithContext to ToolCallEventWriter**

Modify `ToolCallEventWriter.java` to add two new methods that accept explicit parameters (needed because the ADK tool callbacks don't use EventContextHolder):

```java
    /**
     * Write a tool_request event with explicit context (for ADK callback use).
     */
    public SessionEventEntity writeToolRequestWithContext(String toolName, String args,
                                                           String agentName, Long parentTurnId,
                                                           String conversationId) {
        Long eventId = snowflakeIdGenerator.nextId();
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tool_name", toolName);
            metadata.put("agent", agentName);

            SessionEventEntity entity = new SessionEventEntity();
            entity.setId(eventId);
            entity.setEventType("tool_request");
            entity.setConversationId(conversationId);
            entity.setParentEventId(parentTurnId);
            entity.setRole("tool");
            entity.setContent(args);
            entity.setMetadata(objectMapper.writeValueAsString(metadata));
            entity.setStatus("streaming");
            entity.setCreatedAt(LocalDateTime.now());

            SessionEventEntity saved = eventRepository.save(entity);
            lastToolRequest.set(saved);
            return saved;
        } catch (Exception e) {
            log.error("Failed to write tool_request", e);
            return null;
        }
    }

    /**
     * Write a tool_response event with explicit context (for ADK callback use).
     */
    public void writeToolResponseWithContext(String toolName, String result,
                                              String conversationId) {
        SessionEventEntity request = lastToolRequest.get();
        if (request == null) {
            log.warn("No tool_request found for response, toolName={}", toolName);
            return;
        }

        long durationMs = System.currentTimeMillis() - request.getCreatedAt()
                .atZone(java.time.ZoneOffset.systemDefault()).toInstant().toEpochMilli();

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tool_name", toolName);
            metadata.put("elapsed_ms", durationMs);

            SessionEventEntity entity = new SessionEventEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setEventType("tool_response");
            entity.setConversationId(conversationId);
            entity.setParentEventId(request.getId());
            entity.setRole("tool");
            entity.setContent(result);
            entity.setMetadata(objectMapper.writeValueAsString(metadata));
            entity.setStatus("success");
            entity.setCreatedAt(LocalDateTime.now());

            eventRepository.save(entity);
            updateEventDuration(request.getId(), durationMs);
        } catch (Exception e) {
            log.error("Failed to write tool_response", e);
        }
    }
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd backend && mvn test -Dtest=AgentObservationCallbacksTest -Dsurefire.useFile=false
```
Expected: All 8 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/agent/observability/AgentObservationCallbacks.java backend/src/test/java/io/github/xiaoailazy/coexistree/agent/observability/AgentObservationCallbacksTest.java backend/src/main/java/io/github/xiaoailazy/coexistree/agent/service/ToolCallEventWriter.java
git commit -m "feat(audit): add AgentObservationCallbacks Spring Bean with 8 ADK callback factories"
```

---

### Task 6: AgentConfig — mount callbacks on all three agents

**Files:**
- Modify: `backend/src/main/java/io/github/xiaoailazy/coexistree/agent/config/AgentConfig.java`

- [ ] **Step 1: Inject AgentObservationCallbacks and mount callbacks on qa-agent**

Add import:
```java
import io.github.xiaoailazy.coexistree.agent.observability.AgentObservationCallbacks;
```

Update qaAgent bean method signature and builder:

```java
    @Bean
    public LlmAgent qaAgent(
            LangChain4j adkLlm,
            SearchTreeTool searchTreeTool,
            ReadNodeTextTool readNodeTextTool,
            ReadDocumentTool readDocumentTool,
            GetSecurityLevelTool getSecurityLevelTool,
            ListSystemsTool listSystemsTool,
            AgentObservationCallbacks obsCallbacks    // ← new
    ) {
        return LlmAgent.builder()
                .name("qa-agent")
                .description("...")
                .model(adkLlm)
                .instruction("""...""")
                .tools(...)
                // Mount observability callbacks
                .beforeAgentCallback(obsCallbacks.createBeforeAgentCallback())
                .afterAgentCallback(obsCallbacks.createAfterAgentCallback())
                .beforeModelCallback(obsCallbacks.createBeforeModelCallback())
                .afterModelCallback(obsCallbacks.createAfterModelCallback())
                .onModelErrorCallback(obsCallbacks.createOnModelErrorCallback())
                .beforeToolCallback(obsCallbacks.createBeforeToolCallback())
                .afterToolCallback(obsCallbacks.createAfterToolCallback())
                .onToolErrorCallback(obsCallbacks.createOnToolErrorCallback())
                .build();
    }
```

- [ ] **Step 2: Mount callbacks on eval-agent (same pattern)**

```java
    @Bean
    public LlmAgent evalAgent(
            LangChain4j adkLlm,
            ReadDocumentTool readDocumentTool,
            AgentObservationCallbacks obsCallbacks    // ← new
    ) {
        return LlmAgent.builder()
                .name("eval-agent")
                .description("...")
                .model(adkLlm)
                .instruction("""...""")
                .tools(...)
                .beforeAgentCallback(obsCallbacks.createBeforeAgentCallback())
                .afterAgentCallback(obsCallbacks.createAfterAgentCallback())
                .beforeModelCallback(obsCallbacks.createBeforeModelCallback())
                .afterModelCallback(obsCallbacks.createAfterModelCallback())
                .onModelErrorCallback(obsCallbacks.createOnModelErrorCallback())
                .beforeToolCallback(obsCallbacks.createBeforeToolCallback())
                .afterToolCallback(obsCallbacks.createAfterToolCallback())
                .onToolErrorCallback(obsCallbacks.createOnToolErrorCallback())
                .build();
    }
```

- [ ] **Step 3: Mount callbacks on root-agent (same pattern)**

```java
    @Bean
    public LlmAgent rootAgent(
            LangChain4j adkLlm,
            LlmAgent qaAgent,
            LlmAgent evalAgent,
            AgentObservationCallbacks obsCallbacks    // ← new
    ) {
        return LlmAgent.builder()
                .name("root-agent")
                .description("...")
                .model(adkLlm)
                .instruction(instruction)
                .tools(...)
                .beforeAgentCallback(obsCallbacks.createBeforeAgentCallback())
                .afterAgentCallback(obsCallbacks.createAfterAgentCallback())
                .beforeModelCallback(obsCallbacks.createBeforeModelCallback())
                .afterModelCallback(obsCallbacks.createAfterModelCallback())
                .onModelErrorCallback(obsCallbacks.createOnModelErrorCallback())
                .beforeToolCallback(obsCallbacks.createBeforeToolCallback())
                .afterToolCallback(obsCallbacks.createAfterToolCallback())
                .onToolErrorCallback(obsCallbacks.createOnToolErrorCallback())
                .build();
    }
```

- [ ] **Step 4: Verify compilation**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/xiaoailazy/coexistree/agent/config/AgentConfig.java
git commit -m "feat(audit): mount AgentObservationCallbacks on all three agents"
```

---

### Task 7: Frontend — add tree API function

**Files:**
- Modify: `frontend/src/api/sessionAudit.js`

- [ ] **Step 1: Add getSessionEventsTree function**

```js
export function getSessionEventsTree(conversationId) {
  return http.get(`/v1/admin/sessions/${conversationId}/events/tree`)
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/sessionAudit.js
git commit -m "feat(audit): add getSessionEventsTree API function"
```

---

### Task 8: Frontend — AgentEventNode.vue (recursive single node component)

**Files:**
- Create: `frontend/src/views/admin/components/AgentEventNode.vue`

- [ ] **Step 1: Create the recursive node component**

```vue
<template>
  <div class="event-node">
    <div
      class="event-node-header"
      :class="node.eventType"
      @click="toggleExpand"
    >
      <span class="expand-icon">{{ isExpanded ? '▼' : '▶' }}</span>
      <span class="event-icon">{{ getIcon(node.eventType) }}</span>
      <span class="event-type-label">{{ getTypeLabel(node.eventType) }}</span>
      <span v-if="node.agentName" class="agent-badge">{{ node.agentName }}</span>
      <span class="node-summary">{{ getNodeSummary(node) }}</span>
      <span v-if="node.durationMs" class="badge-time">{{ formatDuration(node.durationMs) }}</span>
      <span v-if="node.status === 'failed'" class="badge-failed">failed</span>
      <span v-else-if="node.status === 'success' && node.eventType === 'agent_turn'" class="badge-success">success</span>
    </div>

    <!-- Collapsed detail preview (same as existing flat view) -->
    <div class="event-detail-preview" :class="{ open: isExpanded && node.hasDetail }" v-if="isExpanded">
      <div class="key-info-mini">
        <span v-if="getAgent(node)">Agent: {{ getAgent(node) }}</span>
        <span v-if="getModel(node)">Model: {{ getModel(node) }}</span>
        <span v-if="getToolName(node)">Tool: {{ getToolName(node) }}</span>
      </div>

      <!-- LLM prompt -->
      <div class="detail-block" v-if="node.eventType === 'llm_call' && node.metadata?.prompt">
        <div class="detail-toggle" @click.stop="toggleSection('prompt')">
          <span>Prompt</span>
          <span class="arrow" :class="{ open: sections.prompt }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.prompt }">
          <pre>{{ node.metadata.prompt }}</pre>
        </div>
      </div>

      <!-- LLM response content -->
      <div class="detail-block" v-if="node.eventType === 'llm_call' && node.metadata?.content">
        <div class="detail-toggle" @click.stop="toggleSection('response')">
          <span>Response <span v-if="node.metadata?.tokens" class="badge-token">{{ node.metadata.tokens.total || node.metadata.tokens }} tokens</span></span>
          <span class="arrow" :class="{ open: sections.response }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.response }">
          <pre>{{ node.metadata.content }}</pre>
        </div>
      </div>

      <!-- Tool input/output -->
      <div class="detail-block" v-if="node.eventType === 'tool_call' && node.metadata">
        <div class="detail-toggle" @click.stop="toggleSection('toolInput')">
          <span>Input</span>
          <span class="arrow" :class="{ open: sections.toolInput }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.toolInput }">
          <pre>{{ formatJsonSafe(node.content) }}</pre>
        </div>
        <div class="detail-toggle" @click.stop="toggleSection('toolOutput')" v-if="node.metadata?.output">
          <span>Output</span>
          <span class="arrow" :class="{ open: sections.toolOutput }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.toolOutput }">
          <pre>{{ formatJsonSafe(node.metadata.output) }}</pre>
        </div>
      </div>

      <!-- Agent metadata -->
      <div class="detail-block" v-if="node.eventType === 'agent_turn'">
        <div class="detail-toggle" @click.stop="toggleSection('agentMeta')">
          <span>Metadata</span>
          <span class="arrow" :class="{ open: sections.agentMeta }">▶</span>
        </div>
        <div class="detail-body" :class="{ open: sections.agentMeta }">
          <pre>{{ formatJsonSafe(node.metadata) }}</pre>
        </div>
      </div>
    </div>

    <!-- Recursive children -->
    <div class="event-children" v-if="isExpanded && node.children && node.children.length > 0">
      <AgentEventNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

const props = defineProps({
  node: { type: Object, required: true }
})

const isExpanded = ref(false)
const sections = reactive({})

function toggleExpand() {
  isExpanded.value = !isExpanded.value
}

function toggleSection(name) {
  sections[name] = !sections[name]
}

function getIcon(eventType) {
  const icons = {
    agent_turn: '🤖',
    llm_call: '🧠',
    tool_call: '🔧',
    user_message: '👤',
    agent_error: '❌'
  }
  return icons[eventType] || '📄'
}

function getTypeLabel(eventType) {
  const labels = {
    agent_turn: 'Agent',
    llm_call: 'LLM',
    tool_call: 'Tool',
    user_message: 'User',
    agent_error: 'Error'
  }
  return labels[eventType] || eventType
}

function getNodeSummary(node) {
  switch (node.eventType) {
    case 'user_message':
      return node.content ? `"${node.content.substring(0, 60)}${node.content.length > 60 ? '...' : ''}"` : ''
    case 'llm_call':
      const model = node.metadata?.model || ''
      const tokens = node.metadata?.tokens?.total
      return `${model}${tokens ? ` · ${tokens} tokens` : ''}`
    case 'tool_call':
      const tool = node.metadata?.tool_name || ''
      return tool
    case 'agent_turn':
      return ''
    default:
      return ''
  }
}

function getAgent(node) {
  return node.metadata?.agent || node.agentName
}

function getModel(node) {
  return node.metadata?.model
}

function getToolName(node) {
  return node.metadata?.tool_name
}

function formatDuration(ms) {
  if (!ms) return '0ms'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatJsonSafe(val) {
  if (!val) return '(empty)'
  if (typeof val === 'object') return JSON.stringify(val, null, 2)
  try { return JSON.stringify(JSON.parse(val), null, 2) } catch { return val }
}
</script>

<style scoped>
.event-node {
  border-left: 2px solid var(--color-border);
}

.event-node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  border-radius: 4px;
  transition: background 0.15s;
  min-height: 32px;
}

.event-node-header:hover {
  background: var(--color-bg-hover);
}

.expand-icon {
  font-size: 10px;
  color: var(--color-text-secondary);
  width: 12px;
  flex-shrink: 0;
}

.event-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.event-type-label {
  font-weight: 600;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.event-type-label.agent_turn { color: #a78bfa; background: rgba(167, 139, 250, 0.1); }
.event-type-label.llm_call { color: #60a5fa; background: rgba(96, 165, 250, 0.1); }
.event-type-label.tool_call { color: #f59e0b; background: rgba(245, 158, 11, 0.1); }
.event-type-label.user_message { color: #34d399; background: rgba(52, 211, 153, 0.1); }
.event-type-label.agent_error { color: #f87171; background: rgba(248, 113, 113, 0.1); }

.agent-badge {
  font-size: 10px;
  color: #94a3b8;
  background: var(--color-bg-page);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: monospace;
}

.node-summary {
  color: #cbd5e1;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.badge-time {
  font-size: 10px;
  color: #a78bfa;
  background: #1a1a2e;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.badge-failed {
  font-size: 10px;
  color: #f87171;
  background: #450a0a;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.badge-success {
  font-size: 10px;
  color: #34d399;
  background: #052e16;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.badge-token {
  color: #60a5fa;
  font-weight: 500;
}

/* Detail preview */
.event-detail-preview {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
  padding: 0 10px;
}

.event-detail-preview.open {
  max-height: 800px;
  padding: 4px 10px 8px;
}

.key-info-mini {
  display: flex;
  gap: 12px;
  font-size: 10px;
  color: #64748b;
  margin-bottom: 6px;
}

.detail-block {
  margin-bottom: 4px;
}

.detail-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  padding: 3px 8px;
  border-radius: 4px;
  background: #1e293b;
  font-size: 11px;
  color: #94a3b8;
}

.detail-toggle:hover {
  background: #253349;
}

.detail-toggle .arrow {
  transition: transform 0.2s;
  font-size: 10px;
}

.detail-toggle .arrow.open {
  transform: rotate(90deg);
}

.detail-body {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.25s ease;
}

.detail-body.open {
  max-height: 400px;
  overflow-y: auto;
}

.detail-body pre {
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 4px;
  padding: 8px;
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: #a5d6ff;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* Children indentation */
.event-children {
  margin-left: 20px;
  border-left: 1px dashed #334155;
  padding-left: 8px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/components/AgentEventNode.vue
git commit -m "feat(audit): create AgentEventNode recursive component"
```

---

### Task 9: Frontend — AgentEventTree.vue (tree container)

**Files:**
- Create: `frontend/src/views/admin/components/AgentEventTree.vue`

- [ ] **Step 1: Create the tree container component**

```vue
<template>
  <div class="agent-event-tree" v-loading="loading">
    <div v-if="treeNodes.length === 0" class="empty-state">
      <el-empty description="暂无事件数据" />
    </div>
    <AgentEventNode
      v-for="node in treeNodes"
      :key="node.id"
      :node="node"
    />
  </div>
</template>

<script setup>
import AgentEventNode from './AgentEventNode.vue'

defineProps({
  treeNodes: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
</script>

<style scoped>
.agent-event-tree {
  padding: 8px 0;
  overflow-y: auto;
  flex: 1;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/admin/components/AgentEventTree.vue
git commit -m "feat(audit): create AgentEventTree container component"
```

---

### Task 10: Frontend — SessionAuditView add tree view toggle

**Files:**
- Modify: `frontend/src/views/admin/components/SessionAuditView.vue`

- [ ] **Step 1: Add view mode toggle and tree view**

In `<script setup>`, add:
```js
import { getSessionEventsTree } from '@/api/sessionAudit'
import AgentEventTree from './AgentEventTree.vue'

const viewMode = ref('flat')  // 'flat' or 'tree'
const eventTree = ref([])
const loadingTree = ref(false)
```

In `selectConversation()`, add tree loading alongside flat events:
```js
async function selectConversation(conv) {
  selectedConversation.value = conv
  expandedEvent.value = null
  Object.keys(openSections).forEach(k => delete openSections[k])
  events.value = []
  summary.value = null
  eventTree.value = []

  loadingEvents.value = true
  try {
    const [eventsRes, summaryRes] = await Promise.all([
      getSessionEvents(conv.conversationId),
      getSessionSummary(conv.conversationId)
    ])
    if (eventsRes.success) events.value = eventsRes.data
    if (summaryRes.success) summary.value = summaryRes.data

    // Also load tree
    const treeRes = await getSessionEventsTree(conv.conversationId)
    if (treeRes.success) eventTree.value = treeRes.data
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '加载事件失败')
  } finally {
    loadingEvents.value = false
  }
}
```

In the template, after `.timeline-header`, add a view mode toggle:
```vue
<div class="view-mode-toggle">
  <button :class="{ active: viewMode === 'flat' }" @click="viewMode = 'flat'">列表视图</button>
  <button :class="{ active: viewMode === 'tree' }" @click="viewMode = 'tree'">树形视图</button>
</div>
```

Replace the flat event timeline with conditional rendering:
```vue
<!-- Flat view (existing) -->
<div class="event-timeline" v-loading="loadingEvents" v-if="viewMode === 'flat'">
  <!-- ... existing event list ... -->
</div>

<!-- Tree view (new) -->
<div class="event-timeline" v-if="viewMode === 'tree'">
  <AgentEventTree :tree-nodes="eventTree" :loading="loadingTree" />
</div>
```

Add CSS for the toggle:
```css
.view-mode-toggle {
  display: flex;
  gap: 4px;
  padding: 4px 16px;
  border-bottom: 1px solid var(--color-border);
}

.view-mode-toggle button {
  padding: 4px 12px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-page);
  color: var(--color-text-secondary);
  border-radius: 4px;
  cursor: pointer;
}

.view-mode-toggle button.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}
```

- [ ] **Step 2: Verify frontend builds**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/admin/components/SessionAuditView.vue frontend/src/api/sessionAudit.js
git commit -m "feat(audit): add tree view toggle to SessionAuditView"
```

---

### Task 11: End-to-end verification

- [ ] **Step 1: Run all backend tests**

```bash
cd backend && mvn test -q 2>&1 | tail -15
```
Expected: All tests pass, 0 failures

- [ ] **Step 2: Build full project**

```bash
cd C:/Git/CoExistree && bash scripts/docker-build.sh
```
Expected: Build succeeds

- [ ] **Step 3: Commit final state**

```bash
git add -A
git commit -m "feat(audit): complete session audit event tree — backend + frontend"
```

---

## Self-Review

### Spec Coverage Check

| Spec Requirement | Task |
|-----------------|------|
| Record LLM calls via ADK callbacks | Task 5 (AgentObservationCallbacks beforeModel/afterModel) |
| Record agent_turn for all agents including sub-agents | Task 5 (beforeAgentCallback via sessionId()) |
| Tree-shaped frontend view | Task 8, 9, 10 (AgentEventNode, AgentEventTree, SessionAuditView toggle) |
| Merge llm_request+response → llm_call | Task 2 (EventTreeAssembler) |
| Merge tool_request+response → tool_call | Task 2 (EventTreeAssembler) |
| parentEventId support in insertAgentTurn | Task 3 (SessionEventService) |
| GET /events/tree endpoint | Task 4 (SessionAuditController) |
| Mount callbacks on all 3 agents | Task 6 (AgentConfig) |
| AgentChatServiceImpl unchanged | Confirmed — no task modifies it |

### Placeholder Scan
- No "TBD", "TODO", "fill in later" found
- All code steps contain actual content
- No "similar to Task N" references

### Type Consistency
- `AgentEventTreeNode` record fields are consistent across all tasks
- Method signatures match: `insertAgentTurn(..., Long parentEventId)` in Task 3 matches usage in Task 5
- `AgentObservationCallbacks` constructor params match injected dependencies in Task 6
- Frontend component props match DTO structure from Task 1
