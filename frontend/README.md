# CoExistree Frontend

CoExistree 前端应用 - 系统全生命周期知识管理与智能问答平台的前端界面。

## 技术栈

- **Vue 3** - 渐进式 JavaScript 框架
- **Element Plus** - 基于 Vue 3 的组件库
- **Vite** - 下一代前端构建工具
- **Pinia** - Vue 3 状态管理库
- **Vue Router** - Vue.js 官方路由管理器
- **Axios** - HTTP 客户端
- **Marked** - Markdown 解析器

## 项目结构

```
frontend/
├── src/
│   ├── api/           # API 接口定义
│   ├── assets/        # 静态资源（样式、图片等）
│   ├── layout/        # 布局组件
│   ├── router/        # 路由配置
│   ├── views/         # 页面视图组件
│   │   └── document/  # 文档管理相关页面
│   ├── App.vue        # 根组件
│   └── main.js        # 应用入口
├── index.html         # HTML 模板
├── package.json       # 项目依赖配置
└── vite.config.js     # Vite 构建配置
```

## 开发环境配置

### 前置要求

- Node.js 18.x 或更高版本
- npm 9.x 或更高版本

### 安装依赖

```bash
cd frontend
npm install
```

### 开发服务器

```bash
npm run dev
```

启动后，开发服务器将在 http://localhost:5173 运行。

### 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist/` 目录。

### 预览生产构建

```bash
npm run preview
```

## 开发命令

| 命令 | 说明 |
|------|------|
| `npm run dev` | 启动开发服务器（端口 5173） |
| `npm run build` | 构建生产版本 |
| `npm run preview` | 预览生产构建 |

## 代理配置

开发服务器已配置 API 代理，所有 `/api` 开头的请求将转发到 `http://localhost:8080`（后端服务）。

## 浏览器支持

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## 注意事项

- 本项目使用 Vue 3 Composition API
- 组件库使用 Element Plus，图标使用 `@element-plus/icons-vue`
- SSE (Server-Sent Events) 用于流式响应，通过 `@microsoft/fetch-event-source` 实现
