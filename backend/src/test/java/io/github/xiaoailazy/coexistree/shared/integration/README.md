# OpsQA 集成测试

## 概述

集成测试使用 Spring Boot 测试框架和内存 H2 数据库，确保各组件协同工作。

## 测试配置

- **配置文件**: `application-test.yml`
- **数据库**: H2 内存数据库 (每次测试自动重置)
- **Web 环境**: 随机端口 (避免端口冲突)

## 测试类型

### 1. Repository 集成测试 (4个类, 25个测试)

使用 `@DataJpaTest` 测试数据库访问层：

| 测试类 | 测试数 | 说明 |
|--------|--------|------|
| `SystemRepositoryIntegrationTest` | 7 | 系统实体的 CRUD 和自定义查询 |
| `DocumentRepositoryIntegrationTest` | 6 | 文档实体的 CRUD 和查询 |
| `ConversationRepositoryIntegrationTest` | 6 | 会话实体的 CRUD 和排序查询 |
| `SystemKnowledgeTreeRepositoryIntegrationTest` | 6 | 知识树实体的 CRUD 和状态查询 |

### 2. Controller 端到端测试 (4个类, 22个测试)

使用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 测试完整 HTTP 请求链路：

| 测试类 | 测试数 | 说明 |
|--------|--------|------|
| `SystemControllerIntegrationTest` | 6 | 系统的创建、查询、更新、删除 |
| `DocumentControllerIntegrationTest` | 6 | 文档上传、查询、删除、类型验证 |
| `ConversationControllerIntegrationTest` | 6 | 会话创建、消息查询、删除 |
| `KnowledgeTreeControllerIntegrationTest` | 4 | 知识树状态查询 |

## 运行测试

### 运行所有集成测试
```bash
./mvnw test -Dtest="*IntegrationTest"
```

### 运行 Repository 集成测试
```bash
./mvnw test -Dtest="*RepositoryIntegrationTest"
```

### 运行 Controller 集成测试
```bash
./mvnw test -Dtest="*ControllerIntegrationTest"
```

### 运行单个测试类
```bash
./mvnw test -Dtest="SystemControllerIntegrationTest"
```

## 测试工具类

### AbstractIntegrationTest
所有 Controller 集成测试的基类，提供：
- Spring Boot 测试环境配置
- 测试 profile 激活

### AbstractRepositoryTest
所有 Repository 集成测试的基类，提供：
- JPA 测试配置
- 内存数据库支持

### TestDataFactory
测试数据工厂，提供流畅的 API 创建测试实体：

```java
// 创建系统实体
SystemEntity system = TestDataFactory.aSystem()
    .withSystemCode("OPS")
    .withSystemName("Operations System")
    .build();

// 创建文档实体
DocumentEntity doc = TestDataFactory.aDocument()
    .withSystemId(1L)
    .withParseStatus("SUCCESS")
    .build();
```

## 测试数据隔离

每个测试使用唯一的标识符（UUID）来避免数据冲突：

```java
String uniqueCode = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
```

## 注意事项

1. **H2 数据库**: 使用内存模式，测试结束后数据自动清理
2. **Flyway 禁用**: 测试使用 `ddl-auto: create-drop` 而非 Flyway 迁移
3. **文件存储**: 测试使用临时目录 `./target/test-data/`，测试后自动清理
4. **LLM 客户端**: 测试配置中使用模拟 API 密钥和端点
