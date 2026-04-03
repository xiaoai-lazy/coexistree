# 贡献指南

感谢您对 CoExistree 项目的关注！本文档将指导您如何参与到项目的开发中。

## 开发环境准备

### 环境要求

- JDK 21+
- Node.js 20+
- Maven 3.9+
- Git

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.x, Spring Security, JWT |
| 前端 | Vue 3, Pinia, Axios, Element Plus |
| 数据库 | H2 (开发), MySQL/PostgreSQL (生产) |
| AI | 字节跳动 Ark SDK |
| 构建 | Maven, Vite |

### 本地启动

1. **克隆仓库**
   ```bash
   git clone https://github.com/xiaoailazy/coexistree.git
   cd coexistree
   ```
2. **启动后端**
   ```bash
   cd backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```
3. **启动前端**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
4. **访问应用**
   - 前端: <http://localhost:5173>
   - 后端 API: <http://localhost:8080>
   - H2 控制台: <http://localhost:8080/h2-console>

## 提交规范

### 分支命名

- `feature/xxx` - 新功能
- `fix/xxx` - 问题修复
- `docs/xxx` - 文档更新
- `refactor/xxx` - 代码重构

### Commit Message 规范

使用语义化提交格式：

```
<type>: <subject>

<body>

<footer>
```

**类型 (type):**

- `feat` - 新功能
- `fix` - 修复 bug
- `docs` - 文档变更
- `style` - 代码格式（不影响功能的修改）
- `refactor` - 代码重构
- `perf` - 性能优化
- `test` - 测试相关
- `chore` - 构建/工具相关

**示例:**

```
feat: 添加系统知识树导出功能

支持将知识树导出为 JSON 格式，便于备份和迁移。

Closes #123
```

## 代码规范

### Java 后端

- 遵循 Google Java Style Guide
- 使用 4 空格缩进
- 类名使用 PascalCase，方法名和变量名使用 camelCase
- 常量使用 UPPER\_SNAKE\_CASE
- 所有公共 API 添加 JavaDoc 注释

### Vue/TypeScript 前端

- 使用 ESLint 和 Prettier 进行代码格式化
- 组件名使用 PascalCase
- Props 必须定义类型
- 使用 Composition API 风格

## 提交 Pull Request

1. **Fork 仓库** 并克隆到本地
2. **创建分支** `git checkout -b feature/your-feature`
3. **提交更改** `git commit -m "feat: add something"`
4. **推送到远程** `git push origin feature/your-feature`
5. **创建 PR** 到 `main` 分支

### PR 要求

- 确保所有测试通过
- 更新相关文档（如需要）
- 添加变更说明到 CHANGELOG.md
- 关联相关 Issue（如适用）

## 测试

### 后端测试

```bash
cd backend
./mvnw test
```

### 前端测试

```bash
cd frontend
npm run test
```

## 报告问题

如果您发现了 Bug 或有新功能建议，请通过 [GitHub Issues](https://github.com/xiaoailazy/coexistree/issues) 提交。

提交问题时请包含：

- 问题描述
- 复现步骤
- 期望行为
- 实际行为
- 环境信息（OS、JDK/Node 版本等）
- 相关日志或截图

## 许可证

通过提交代码，您同意您的贡献将在 [MIT 许可证](LICENSE) 下发布。

***

再次感谢您的贡献！🌳
