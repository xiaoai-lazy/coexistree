# 变更日志

所有重要的变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [1.1.0] - 2026-04-03

### 新增

#### 用户认证与授权
- JWT Token 认证体系，支持 24 小时过期
- 登录/登出/密码修改功能
- 全局角色：SUPER_ADMIN / USER

#### 系统成员权限管理
- 三级角色模型：OWNER / MAINTAINER / SUBSCRIBER
- OWNER 可添加 MAINTAINER 和 SUBSCRIBER
- MAINTAINER 只能添加 SUBSCRIBER
- 成员查看等级 (1-5)，控制可见文档范围

#### 文档安全等级
- 五级安全等级体系 (1-5)
- 用户只能查看等级 ≤ 自身查看等级的文档
- MAINTAINER 上传文档等级不能超过自身等级

#### 管理员功能
- SUPER_ADMIN 可创建/删除普通用户
- 管理员可重置用户密码
- 系统初始化时自动创建 admin 账号

#### 前端登录
- 登录页面
- 路由守卫：未登录自动跳转登录页
- Axios Token 拦截器：自动附加 JWT
- 401 响应拦截：Token 过期自动跳转登录

#### 测试
- 权限相关单元测试和集成测试
- BaseDataIntegrationTest 测试基类

### 技术栈变更
- 新增 Spring Security
- 新增 JWT (jjwt 0.12.5)
- BCrypt 密码加密

### 数据库迁移
- V3__add_users.sql - 用户表
- V4__add_system_user_mappings.sql - 系统用户映射表
- V5__add_document_security.sql - 文档安全等级字段
- V6__add_user_enabled.sql - 用户启用状态字段

---

## [1.0.0] - 2026-03-20

### 新增

#### 系统知识树
- 系统创建与管理
- Markdown 文档导入
- 自动解析标题层级构建知识树
- 可视化知识树浏览

#### 变更合并 (Change Merge)
- 智能识别文档变更
- 自动合并到现有知识树
- LLM 驱动的变更冲突检测

#### 智能问答
- 基于知识树的精准问答
- 两阶段 LLM 流程：定位知识节点 → 生成答案
- 答案附带引用来源
- 支持多轮对话

#### 快照版本
- 每次合并自动生成版本快照
- 历史版本回溯
- 变更历史可视化

#### 基础架构
- Java 21 + Spring Boot 3.x
- Vue 3 前端
- H2 数据库
- 字节跳动 Ark SDK 接入

---

## 迁移指南

### 从 v1.0.0 升级到 v1.1.0

1. **环境变量**：新增以下配置
   ```bash
   ADMIN_INITIAL_PASSWORD=your-secure-password
   JWT_SECRET=your-jwt-secret-at-least-32-characters
   ```

2. **数据库**：Flyway 会自动执行迁移脚本，无需手动操作

3. **首次登录**：使用 admin 账号和配置的密码登录

4. **权限配置**：
   - 为现有系统添加 OWNER（默认为创建者）
   - 为成员分配适当的角色和查看等级

---

[1.1.0]: https://github.com/xiaoailazy/coexistree/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/xiaoailazy/coexistree/releases/tag/v1.0.0
