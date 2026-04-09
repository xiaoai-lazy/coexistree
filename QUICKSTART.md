# CoExistree 快速部署指南

## 快速部署（5分钟）

### 1. 下载项目

```bash
# 创建目录
mkdir -p /opt/coexistree
cd /opt/coexistree

# 下载代码
git clone <your-repo-url> .
```

### 2. 配置文件

```bash
# 1. 复制环境变量配置
cp .env.example .env
nano .env

# 2. 复制 nginx 配置模板
cp nginx/nginx.conf.example nginx/nginx.conf
```

**最小配置（必须修改）:**
```env
# LLM API Key (从 OpenAI 或其他服务商获取)
LLM_API_KEY=sk-your-api-key-here

# 管理员密码 (至少8位，包含大小写字母和数字)
ADMIN_INITIAL_PASSWORD=Admin@123456

# JWT 密钥 (32位以上随机字符串)
JWT_SECRET=ChangeMeToARandomStringAtLeast32Characters
```

### 3. 启动服务

```bash
chmod +x scripts/deploy-prod.sh
./scripts/deploy-prod.sh start
```

### 4. 验证部署

```bash
# 检查服务状态
./scripts/deploy-prod.sh status

# 测试登录
curl -X POST http://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456"}'
```

### 5. 访问系统

- 前端界面: http://服务器IP/
- API 文档: http://服务器IP/swagger-ui.html
- 默认账号: `admin` / `Admin@123456` (你在 .env 中设置的密码)

---

## 常用运维命令

| 命令 | 说明 |
|------|------|
| `./scripts/deploy-prod.sh start` | 启动服务 |
| `./scripts/deploy-prod.sh stop` | 停止服务 |
| `./scripts/deploy-prod.sh restart` | 重启服务 |
| `./scripts/deploy-prod.sh status` | 查看状态 |
| `./scripts/deploy-prod.sh logs -f` | 实时查看日志 |
| `./scripts/deploy-prod.sh backup` | 备份数据 |
| `./scripts/deploy-prod.sh update` | 更新部署 |
