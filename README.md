# CoExistree (CET) 🌳

**系统共生知识管理平台 - 让文档与系统共同成长**

[!\[License: MIT\](https://img.shields.io/badge/License-MIT-yellow.svg null)](https://opensource.org/licenses/MIT)
[!\[Version\](https://img.shields.io/badge/version-1.0.0-blue.svg null)](https://github.com/xiaoailazy/coexistree/releases)
[!\[Java\](https://img.shields.io/badge/Java-21-orange.svg null)](https://openjdk.org/projects/jdk/21/)
[!\[Vue\](https://img.shields.io/badge/Vue-3-green.svg null)](https://vuejs.org/)

***

## 简介

CoExistree 是一个系统全生命周期知识管理与智能问答平台。它通过创新的"系统知识树"模型，将文档从静态资料转变为系统的"成长记录"，实现文档与系统的共生进化。

不同于传统的文档管理系统，CoExistree 将每一次文档更新视为系统的"变更记录"，自动维护结构化的知识树，并支持基于知识树的智能问答。

***

## 核心特性

### 🌲 系统知识树

- 以树形结构组织系统知识，每个系统拥有独立的知识空间
- 支持 Markdown 文档导入，自动解析标题层级构建知识树
- 可视化知识树浏览与导航

### 🔄 变更合并 (Change Merge)

- 智能识别文档变更，自动合并到现有知识树
- 支持增量更新，保留历史版本
- LLM 驱动的变更冲突检测与解决建议

### 💬 智能问答

- 基于知识树的精准问答，答案附带引用来源
- 两阶段 LLM 流程：先定位知识节点，再生成答案
- 支持多轮对话，上下文感知

### 📸 快照版本

- 每次合并自动生成版本快照
- 支持历史版本回溯与对比
- 变更历史可视化

***

## 技术栈

| 层级 | 技术                                        |
| -- | ----------------------------------------- |
| 后端 | Java 21, Spring Boot 3.x, Virtual Threads |
| 前端 | Vue 3, Element Plus, TypeScript           |
| AI | Volcengine Ark SDK (字节跳动)                 |
| 存储 | H2 Database, Local Filesystem             |
| 构建 | Maven, Vite                               |
| 部署 | Docker, Docker Compose                    |

***

## 快速开始

### 环境要求

- JDK 21+
- Node.js 20+
- Maven 3.8+ (或使用 Maven Wrapper)
- Docker (可选)

### 方式一：本地开发

**1. 启动后端**

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**2. 启动前端**

```bash
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173> 开始使用。

### 方式二：Docker 部署（推荐生产环境）

**使用 Docker Compose 一键启动：**

```bash
# 1. 克隆仓库
git clone https://github.com/xiaoailazy/coexistree.git
cd coexistree

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，设置 LLM_API_KEY

# 3. 启动服务
docker-compose up -d

# 4. 访问应用
# Web 界面: http://localhost:8080
```

**环境变量说明：**

| 变量             | 说明               | 默认值                        |
| -------------- | ---------------- | -------------------------- |
| `LLM_API_KEY`  | 字节跳动 Ark API Key | 必填                         |
| `LLM_MODEL`    | 使用的模型            | doubao-seed-2-0-pro-260215 |
| `STORAGE_PATH` | 数据存储路径           | ./data                     |
| `JAVA_OPTS`    | JVM 参数           | -Xms512m -Xmx2g            |

***

## 项目结构

```
CoExistree/
├── backend/              # Spring Boot 后端
│   ├── src/main/java/    # Java 源码
│   ├── src/main/resources/
│   │   ├── db/migration/ # 数据库迁移脚本
│   │   ├── application-dev.yml   # 开发配置
│   │   └── application-prod.yml  # 生产配置
│   └── pom.xml
├── frontend/             # Vue 3 前端
│   ├── src/
│   ├── package.json
│   └── vite.config.js
├── docs/                 # 项目文档
│   ├── public/           # 公开文档
│   └── private/          # 内部文档
├── Dockerfile            # Docker 构建文件
├── docker-compose.yml    # Docker Compose 配置
├── LICENSE               # MIT 许可证
└── README.md
```

***

## 配置说明

### 开发环境

使用 H2 内存数据库，无需额外配置：

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 生产环境

使用 H2 文件数据库，配置 LLM API Key：

```bash
export LLM_API_KEY=your_api_key

cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

数据存储在 `./data/` 目录（可通过 `STORAGE_PATH` 环境变量修改）。

***

## 贡献指南

我们欢迎所有形式的贡献！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解如何参与项目开发。

### 快速参与

1. Fork 本仓库
2. 创建分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

***

## 文档

- [贡献指南](CONTRIBUTING.md)
- [安全政策](SECURITY.md)
- [API 文档](http://localhost:8080/swagger-ui.html) (启动后访问)

***

## 许可证

[MIT](LICENSE) © 2026 CoExistree Contributors

***

## 致谢

感谢所有为 CoExistree 做出贡献的开发者！

如果这个项目对您有帮助，请给我们一个 ⭐️ Star！
