# CoExistree (CET) 🌳

**系统共生知识管理平台 - 让团队知识安全、有序地流动**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)](https://github.com/xiaoailazy/coexistree/releases)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Vue](https://img.shields.io/badge/Vue-3-green.svg)](https://vuejs.org/)

***

## 简介

CoExistree 是一个面向企业的知识管理与智能问答平台。它将文档从静态资料转变为系统的"成长记录"，实现知识随系统共同演进，并基于细粒度权限管控确保敏感信息安全。

***

## ✨ 核心亮点

- 🔐 **细粒度权限管控** - 系统级角色 + 文档五级安全等级，最小权限原则
- 🌲 **知识树模型** - 文档自动组织为树形结构，随系统演进版本化归档
- 💬 **可信 AI 问答** - 基于知识树精准回答，答案附带原文出处
- 👥 **优雅团队协作** - OWNER/MAINTAINER/SUBSCRIBER 三级角色，自助成员管理

***

## 🚀 快速开始

### Docker 部署（推荐）

```bash
git clone https://github.com/xiaoailazy/coexistree.git
cd coexistree
cp .env.example .env
# 编辑 .env，设置 LLM_API_KEY 和 ADMIN_INITIAL_PASSWORD
docker-compose up -d
```

访问 <http://localhost:8080>，使用配置的 admin 账号登录。

### 本地开发

```bash
# 后端
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 前端
cd frontend && npm install && npm run dev
```

访问 <http://localhost:5173>

***

## 📚 文档

- [用户指南](docs/user-guide/intro.md) - 了解 CoExistree 如何解决你的痛点
- [API 文档](http://localhost:8080/swagger-ui.html) - 启动后访问
- [开发指南](CONTRIBUTING.md) - 参与项目开发
- [变更日志](CHANGELOG.md) - 版本历史

***

## 🤝 贡献

欢迎提交 Issue 和 PR！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解详情。

***

## 📄 License

[MIT](LICENSE) © 2026 CoExistree Contributors

***

如果这个项目对你有帮助，请给我们一个 ⭐️ Star！
