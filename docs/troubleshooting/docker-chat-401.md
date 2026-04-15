# Docker 部署对话报错排查记录

> 日期: 2026-04-15
> 状态: 已解决

## 问题描述

服务部署到 Docker 后，前端发起对话请求（`POST /api/v1/conversations/{id}/smart-chat`）报错，返回：

```json
{"success":false,"code":401,"message":"Unauthorized"}
```

## 已解决的问题

### 1. Agent NPE 导致对话失败 ✅

**现象:** 后端日志显示 `Agent 错误: null`，随后 `AuthorizationDeniedException: Access Denied`。

**根因:** `AgentConfig.java` 中创建的 `qa-agent`、`eval-agent`、`root-agent` 没有设置 `.description()` 属性。Google ADK 的 `AgentTool.declaration()` 在构建 `FunctionDeclaration` 时调用 `agent.description()` 返回 `null`，传入 `Objects.requireNonNull()` 导致 NPE。

**修复:** 在 `AgentConfig.java` 中为三个 Agent 都添加了 `.description()`。

### 2. Smart-Chat 端点返回 401 Unauthorized ✅ 已解决

**根因:** Spring Security 的 `SecurityContextHolder` 默认使用 `MODE_THREADLOCAL` 策略。当 SSE 端点（`text/event-stream`）通过异步请求处理时，Spring MVC 会将请求分发到不同的线程。`SecurityContextHolderFilter` 在异步分派的 filter chain 重新执行时清除了 `SecurityContext`，导致 `AuthorizationFilter` 认为请求未认证。

**修复:** 在 `SecurityConfig` 中通过 `@PostConstruct` 将 `SecurityContextHolder` 策略改为 `MODE_INHERITABLETHREADLOCAL`：

```java
@PostConstruct
public void init() {
    SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
}
```

这样 JWT filter 设置的认证信息会在异步分派线程中可见。

**清理:** 同时移除了 `JwtAuthenticationFilter` 中的调试日志。

## 修改过的文件

| 文件 | 修改内容 |
|------|----------|
| `agent/config/AgentConfig.java` | 为 qa-agent、eval-agent、root-agent 添加 `.description()` |
| `security/config/SecurityConfig.java` | 添加 `MODE_INHERITABLETHREADLOCAL` 策略配置 |
| `security/filter/JwtAuthenticationFilter.java` | 清理调试日志 |
| `test-sse.js` | 已删除 |
