# 安全政策

## 支持版本

以下版本正在积极接收安全更新：

| 版本     | 支持状态   |
| ------ | ------ |
| 1.0.x  | ✅ 支持   |

## 安全最佳实践

### 部署安全

- **修改默认配置**：生产环境务必修改所有默认密码
- **API 密钥管理**：使用环境变量或密钥管理服务，不要硬编码
- **HTTPS**：生产环境强制使用 HTTPS
- **数据备份**：定期备份数据库和知识树文件

### 配置建议

```yaml
# application-prod.yml 关键安全设置

# 1. 数据存储安全
spring:
  datasource:
    # H2 数据库文件存储在指定目录，确保该目录有适当的权限控制
    url: jdbc:h2:file:${STORAGE_PATH}/h2/coexistree;MODE=MySQL;AUTO_SERVER=TRUE

# 2. 保护 LLM API Key
app:
  llm:
    api-key: ${LLM_API_KEY}   # 从环境变量读取

# 3. 限制日志级别（避免泄露敏感信息）
logging:
  level:
    root: WARN
```

### 依赖安全

我们会定期更新依赖以修复已知漏洞。您可以通过以下方式检查：

```bash
# 后端依赖检查
cd backend
./mvnw org.owasp:dependency-check-maven:check

# 前端依赖检查
cd frontend
npm audit
```

## 已知漏洞

暂无已知安全漏洞。

***

最后更新：2026-03-28
