#!/bin/bash
# CoExistree Docker 快速构建脚本
# 使用 BuildKit 缓存挂载加速 Maven 和 npm 依赖下载

set -e

# 颜色定义
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}CoExistree Docker 构建${NC}"
echo -e "${CYAN}========================================${NC}"

# 确保使用 BuildKit
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# 解析参数
NO_CACHE=false
PUSH=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-cache)
            NO_CACHE=true
            shift
            ;;
        --push)
            PUSH=true
            shift
            ;;
        *)
            shift
            ;;
    esac
done

# 构建参数
BUILD_ARGS=()

if [ "$NO_CACHE" = true ]; then
    echo -e "${YELLOW}模式: 不使用缓存 (--no-cache)${NC}"
    BUILD_ARGS+=("--no-cache")
else
    echo -e "${GREEN}模式: 使用缓存挂载加速${NC}"
    echo -e "${GREEN}Maven 依赖和 npm 包将被缓存，后续构建会更快${NC}"
fi

# 执行构建
echo ""
echo -e "${CYAN}开始构建镜像...${NC}"
docker compose build "${BUILD_ARGS[@]}"

echo ""
echo -e "${GREEN}构建完成!${NC}"

# 如果需要推送
if [ "$PUSH" = true ]; then
    echo ""
    echo -e "${CYAN}推送镜像到仓库...${NC}"
    docker compose push
fi

echo ""
echo -e "${CYAN}启动服务: docker compose up -d${NC}"
