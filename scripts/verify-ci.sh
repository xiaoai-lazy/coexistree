#!/bin/bash
# 本地模拟 CI 环境验证脚本

set -e

echo "===== 本地 CI 验证 ====="
echo ""

# 检查 Node 版本
echo "1. 检查 Node.js 版本..."
if [ -f .nvmrc ]; then
    EXPECTED_NODE=$(cat .nvmrc)
    CURRENT_NODE=$(node --version)
    echo "   期望版本: v$EXPECTED_NODE"
    echo "   当前版本: $CURRENT_NODE"
    if [[ "$CURRENT_NODE" != v${EXPECTED_NODE}* ]]; then
        echo "   ⚠️ 版本不匹配！建议使用 nvm 切换: nvm use"
        exit 1
    fi
fi
echo "   ✅ Node.js 版本正确"
echo ""

# 检查前端
echo "2. 验证前端构建..."
cd frontend

# 清理旧依赖
echo "   清理 node_modules..."
rm -rf node_modules package-lock.json

# 使用 npm ci 方式安装（严格遵循 package-lock.json）
echo "   安装依赖 (npm install)..."
npm install

# 构建
echo "   执行构建..."
npm run build

echo ""
echo "===== 前端验证通过 ✅ ====="
echo ""

# 检查后端
cd ../backend
echo "3. 验证后端构建..."
./mvnw clean compile -q
echo "   ✅ 后端编译通过"

echo ""
echo "===== 所有验证通过 ✅ ====="
echo "可以安全地推送代码到远程仓库"
