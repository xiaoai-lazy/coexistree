# CoExistree 开发环境配置

## Java 和 Maven 配置

本项目使用固定的 Java 和 Maven 版本，通过 PowerShell 脚本配置环境变量。

### 环境路径

| 工具 | 路径 |
|------|------|
| Java 21 | `C:\Users\zhangjian\.jdks\ms-21.0.9` |
| Maven 3.9.11 | `D:\Maven\apache-maven-3.9.11` |

### 启动脚本

位于 `backend/scripts/` 目录：

#### 1. 编译项目
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\mvn-local.ps1 -q -DskipTests compile
```

#### 2. 运行测试
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\mvn-local.ps1 test
```

#### 3. 启动应用
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-local.ps1
```

### 手动配置环境变量（Bash）

如果使用 Git Bash 或其他终端：

```bash
export JAVA_HOME="/c/Users/zhangjian/.jdks/ms-21.0.9"
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_HOME="/d/Maven/apache-maven-3.9.11"
export PATH="$MAVEN_HOME/bin:$PATH"
```

### Maven 配置

- 使用项目级配置：`.mvn/local-settings.xml`
- Maven Wrapper: `./mvnw` (推荐，会自动使用项目配置)

### 技术栈版本

- Java: 21
- Spring Boot: 3.5.11
- Maven: 3.9.11

## API 约定

1. **先文档后代码** - 修改 API 前先更新 `docs/public/api/openapi.yaml`
2. **响应格式统一** - 所有返回 `{success, code, message, data}`
3. **错误码定义** - 后端 `ErrorCode.java` 和前端 `constants/` 保持同步
4. **查看文档** - http://localhost:8080/swagger-ui.html

## 测试约定

### LLM 调用规范

`llmClient.chat()` 调用在所有测试中都默认 mock，不调用真实接口。

```java
@BeforeEach
void setUp() {
    // 默认 mock LLM 调用
    when(llmClient.chat(anyString(), any(), anyDouble()))
        .thenReturn(new LlmClient.LlmResponse(null, "mock-response"));
}
```

只有在配置中显式打开开关时才调用真实接口。这意味着：
- 单元测试始终使用 mock
- 真实 LLM 调用仅在集成测试中启用，且需要显式配置

## 测试数据管理

使用 `@Sql` 注解管理测试数据，避免在测试代码中手动 `save()` 创建数据。

### 基本用法

```java
// 测试前导入数据
@Sql(scripts = "/sql/base-test-data.sql", executionPhase = BEFORE_TEST_METHOD)

// 测试后清理数据
@Sql(scripts = "/sql/cleanup.sql", executionPhase = AFTER_TEST_METHOD)
```

### 预置数据说明

`base-test-data.sql` 提供：
- 用户: testuser(id=1), otheruser(id=2), admin(id=99)
- 系统: order-service(id=1), user-service(id=2)
- 文档: 每个系统有对应文档

### 快捷基类

继承 `BaseDataIntegrationTest` 自动获得基础数据：

```java
class MyTest extends BaseDataIntegrationTest {
    // 直接测试，数据已准备好
}

---

## 重要安全提醒

### npm Registry 配置

**⚠️ 严禁使用内部私服地址提交到公网仓库**

前端项目必须使用官方 npm 源：
```bash
# 检查当前 registry
npm config get registry

# 设置为官方源
npm config set registry https://registry.npmjs.org/
```

项目已配置 `.npmrc` 强制使用官方源，**不要覆盖此配置**。

**如果已经使用了私服：**
1. 删除 `node_modules` 和 `package-lock.json`
2. 切换到官方源: `npm config set registry https://registry.npmjs.org/`
3. 重新安装: `npm install`
4. 提交干净的 `package-lock.json`

---

## 版本发布流程

### 前置条件

1. 所有功能开发完成并通过本地测试
2. 已更新 CHANGELOG.md
3. 已确定版本号（遵循语义化版本）

### 发布步骤

#### 1. 本地验证（必须）

**Windows:**
```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-ci.ps1
```

**Mac/Linux:**
```bash
bash scripts/verify-ci.sh
```

本地验证通过后，确保 CI 必定通过。

#### 2. 推送代码到远程

```bash
git add -A
git commit -m "release: prepare for vX.X.X"
git push origin main
```

#### 3. 等待 CI 通过

访问 GitHub Actions 页面，确认所有检查通过：
- Backend Build & Test ✅
- Frontend Build ✅

#### 4. 创建并推送 Tag

```bash
# 创建带注释的 tag
git tag -a vX.X.X -m "release vX.X.X - 简短描述"

# 推送 tag 到远程
git push origin vX.X.X
```

#### 5. 创建 GitHub Release

- 访问 GitHub Releases 页面
- 选择刚推送的 tag
- 填写 Release Title 和 Notes
- 发布

### 热修复流程

如需紧急修复：

```bash
# 1. 从 main 创建修复分支
git checkout -b hotfix/vX.X.X main

# 2. 修复并提交
git add -A
git commit -m "fix: 修复描述"

# 3. 本地验证
powershell -File scripts/verify-ci.ps1

# 4. 推送并创建 PR
git push origin hotfix/vX.X.X

# 5. 合并后打 tag
git checkout main
git pull origin main
git tag -a vX.X.X+1 -m "hotfix vX.X.X+1"
git push origin vX.X.X+1
```

### 注意事项

1. **必须先本地验证再推送** - 避免 CI 失败污染 commit 历史
2. **版本号规则** - 遵循 SemVer: MAJOR.MINOR.PATCH
3. **Tag 必须使用 `v` 前缀** - 如 `v2.1.0`
4. **Release Notes 必须包含** - 变更摘要和迁移指南
```
