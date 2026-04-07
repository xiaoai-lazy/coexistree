#!/bin/bash
# CoExistree 部署脚本
# 用法: ./scripts/deploy.sh [start|stop|restart|logs]

set -e

cd "$(dirname "$0")/.."

check_env() {
    if [ ! -f ".env" ]; then
        echo "Error: .env not found. Copy from .env.example"
        exit 1
    fi
}

cmd_start() {
    check_env
    echo "Starting services..."
    docker-compose up -d --build
    echo "Done. http://$(hostname -I | awk '{print $1}')"
}

cmd_stop() {
    echo "Stopping services..."
    docker-compose down
}

cmd_restart() {
    cmd_stop
    cmd_start
}

cmd_logs() {
    docker-compose logs -f "$@"
}

case "${1:-start}" in
    start|up) cmd_start ;;
    stop|down) cmd_stop ;;
    restart) cmd_restart ;;
    logs) shift; cmd_logs "$@" ;;
    *)
        echo "Usage: $0 [start|stop|restart|logs]"
        exit 1
        ;;
esac
