#!/bin/bash
# CoExistree 生产环境部署脚本
# 用法: ./scripts/deploy-prod.sh [start|stop|restart|logs|status|backup|update]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 项目配置
PROJECT_NAME="coexistree"
COMPOSE_FILE="docker-compose.yml"
ENV_FILE=".env"
BACKUP_DIR="./backups"
DATA_DIR="./data"

# 切换到项目根目录
cd "$(dirname "$0")/.."

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}========================================${NC}"
}

# 检查环境
check_env() {
    # 检查 .env 文件
    if [ ! -f "$ENV_FILE" ]; then
        print_error "未找到 .env 文件"
        print_info "请复制 .env.example 为 .env 并配置"
        print_info "  cp .env.example .env"
        exit 1
    fi

    # 检查必需的配置
    local required_vars=("JWT_SECRET" "ADMIN_INITIAL_PASSWORD")
    local missing=()

    for var in "${required_vars[@]}"; do
        if ! grep -q "^${var}=" "$ENV_FILE" || grep -q "^${var}=$" "$ENV_FILE"; then
            missing+=("$var")
        fi
    done

    if [ ${#missing[@]} -ne 0 ]; then
        print_error ".env 中缺少必需的配置项:"
        for var in "${missing[@]}"; do
            echo "  - $var"
        done
        exit 1
    fi

    print_success "环境配置检查通过"
}

# 检查 Nginx 配置
check_nginx_conf() {
    if [ ! -f "nginx/nginx.conf" ]; then
        print_warning "未找到 nginx/nginx.conf 文件"
        print_info "请复制 nginx/nginx.conf.example 为 nginx/nginx.conf"
        print_info "  cp nginx/nginx.conf.example nginx/nginx.conf"
        print_info ""
        print_info "提示: nginx.conf 已添加到 .gitignore"
        print_info "     您可以自由修改此文件而不会被 git 覆盖"
        exit 1
    fi

    # 检查配置文件语法（如果容器在运行）
    if docker ps -q --filter "name=coexistree-nginx" | grep -q .; then
        if ! docker exec coexistree-nginx nginx -t > /dev/null 2>&1; then
            print_warning "Nginx 配置语法可能有问题，建议检查"
        fi
    fi

    print_success "Nginx 配置检查通过"
}

# 检查 Docker
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker 未安装"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        print_error "Docker Compose 未安装"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        print_error "Docker 守护进程未运行"
        exit 1
    fi

    print_success "Docker 环境正常"
}

# 获取 docker compose 命令
docker_compose_cmd() {
    if docker compose version &> /dev/null; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

# 创建数据目录
init_data_dirs() {
    print_info "初始化数据目录..."
    mkdir -p "$DATA_DIR"/{docs,logs,trees,system-trees}
    print_success "数据目录初始化完成"
}

# 启动服务
cmd_start() {
    print_header "启动 CoExistree 服务"
    check_docker
    check_env
    check_nginx_conf
    init_data_dirs

    local compose_cmd=$(docker_compose_cmd)

    print_info "构建并启动服务..."
    $compose_cmd -f "$COMPOSE_FILE" up -d --build

    print_info "等待服务启动..."
    sleep 5

    # 检查健康状态
    local retries=0
    local max_retries=30
    while [ $retries -lt $max_retries ]; do
        if curl -sf http://localhost/api/v1/auth/login -X POST \
            -H "Content-Type: application/json" \
            -d '{"username":"admin","password":"test"}' &> /dev/null; then
            print_success "服务启动成功!"
            print_info "访问地址: http://localhost"
            print_info "API 文档: http://localhost/swagger-ui.html"
            return 0
        fi
        retries=$((retries + 1))
        echo -n "."
        sleep 2
    done

    print_warning "服务可能尚未完全启动，请稍后检查状态"
    cmd_status
}

# 停止服务
cmd_stop() {
    print_header "停止 CoExistree 服务"
    local compose_cmd=$(docker_compose_cmd)

    print_info "正在停止服务..."
    $compose_cmd -f "$COMPOSE_FILE" down

    print_success "服务已停止"
}

# 重启服务
cmd_restart() {
    print_header "重启 CoExistree 服务"
    cmd_stop
    cmd_start
}

# 查看状态
cmd_status() {
    print_header "服务状态"
    local compose_cmd=$(docker_compose_cmd)

    echo -e "${CYAN}容器状态:${NC}"
    $compose_cmd -f "$COMPOSE_FILE" ps

    echo ""
    echo -e "${CYAN}资源使用:${NC}"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.Status}}" 2>/dev/null || true
}

# 查看日志
cmd_logs() {
    local compose_cmd=$(docker_compose_cmd)
    local service=""
    local follow=false

    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--follow)
                follow=true
                shift
                ;;
            *)
                service="$1"
                shift
                ;;
        esac
    done

    local log_args=()
    if [ "$follow" = true ]; then
        log_args+=("-f")
    fi
    if [ -n "$service" ]; then
        log_args+=("$service")
    fi

    $compose_cmd -f "$COMPOSE_FILE" logs "${log_args[@]}"
}

# 备份数据
cmd_backup() {
    print_header "备份数据"

    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_name="coexistree_backup_${timestamp}"
    local backup_path="${BACKUP_DIR}/${backup_name}"

    mkdir -p "$backup_path"

    print_info "备份数据到: $backup_path"

    # 备份数据目录
    if [ -d "$DATA_DIR" ]; then
        cp -r "$DATA_DIR" "${backup_path}/"
        print_success "数据目录备份完成"
    fi

    # 备份环境配置
    if [ -f "$ENV_FILE" ]; then
        cp "$ENV_FILE" "${backup_path}/"
        print_success "环境配置备份完成"
    fi

    # 备份 Nginx 配置
    if [ -d "nginx" ]; then
        cp -r "nginx" "${backup_path}/"
        print_success "Nginx 配置备份完成"
    fi

    # 创建压缩包
    cd "$BACKUP_DIR"
    tar -czf "${backup_name}.tar.gz" "$backup_name"
    rm -rf "$backup_name"
    cd - > /dev/null

    print_success "备份完成: ${backup_path}.tar.gz"
}

# 更新部署
cmd_update() {
    print_header "更新 CoExistree"

    print_info "执行备份..."
    cmd_backup

    print_info "拉取最新代码..."
    git pull origin main || print_warning "拉取代码失败，继续本地构建"

    print_info "重新构建并启动..."
    cmd_start

    print_success "更新完成"
}

# 清理旧备份（保留最近7天）
cmd_cleanup() {
    print_header "清理旧备份"

    if [ -d "$BACKUP_DIR" ]; then
        find "$BACKUP_DIR" -name "coexistree_backup_*.tar.gz" -type f -mtime +7 -delete
        print_success "已清理7天前的备份"
    fi
}

# 清理日志
cmd_logs_cleanup() {
    local days=30
    local dry_run=""

    while [[ $# -gt 0 ]]; do
        case $1 in
            -d|--days)
                days="$2"
                shift 2
                ;;
            -n|--dry-run)
                dry_run="-n"
                shift
                ;;
            *)
                shift
                ;;
        esac
    done

    if [ -f "./scripts/log-cleanup.sh" ]; then
        ./scripts/log-cleanup.sh -d "$days" $dry_run
    else
        print_error "log-cleanup.sh 脚本不存在"
        exit 1
    fi
}

# 显示帮助
cmd_help() {
    cat << EOF
CoExistree 生产环境部署脚本

用法:
    $0 [命令] [选项]

命令:
    start      启动服务（首次部署或完整重启）
    stop       停止服务
    restart    重启服务
    status     查看服务状态
    logs       查看日志
               -f, --follow  实时跟踪日志
               [服务名]       查看指定服务日志 (nginx|backend)
    backup       备份数据和配置
    update       更新部署（自动备份+拉取代码+重启）
    cleanup      清理7天前的备份
    logs-cleanup 清理日志文件
                 -d, --days DAYS   保留天数 (默认: 30)
                 -n, --dry-run     预览模式
    help         显示帮助信息

示例:
    $0 start                    # 启动服务
    $0 logs -f                  # 实时查看所有日志
    $0 logs backend             # 查看后端日志
    $0 logs -f nginx            # 实时查看 Nginx 日志
    $0 backup                   # 备份数据
    $0 update                   # 更新部署

配置文件:
    .env        环境变量配置（必需）
                cp .env.example .env

    nginx.conf  Nginx 配置（可选，服务器本地配置）
                cp nginx/nginx.conf.example nginx/nginx.conf
                提示: nginx.conf 已添加到 .gitignore，可自由修改

    data/       数据存储目录（自动创建）

首次部署步骤:
    1. cp .env.example .env && nano .env
    2. cp nginx/nginx.conf.example nginx/nginx.conf
    3. ./scripts/deploy-prod.sh start

EOF
}

# 主入口
case "${1:-help}" in
    start|up)
        cmd_start
        ;;
    stop|down)
        cmd_stop
        ;;
    restart)
        cmd_restart
        ;;
    status|ps)
        cmd_status
        ;;
    logs)
        shift
        cmd_logs "$@"
        ;;
    backup)
        cmd_backup
        ;;
    update)
        cmd_update
        ;;
    cleanup)
        cmd_cleanup
        ;;
    logs-cleanup)
        shift
        cmd_logs_cleanup "$@"
        ;;
    help|--help|-h)
        cmd_help
        ;;
    *)
        print_error "未知命令: $1"
        cmd_help
        exit 1
        ;;
esac
