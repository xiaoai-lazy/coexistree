# CoExistree Docker 快速构建脚本
# 使用 BuildKit 缓存挂载加速 Maven 和 npm 依赖下载

[CmdletBinding()]
param(
    [switch]$NoCache,
    [switch]$Push
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "CoExistree Docker 构建" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 确保使用 BuildKit
$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_DOCKER_CLI_BUILD = "1"

# 构建参数
$buildArgs = @("compose", "build")

if ($NoCache) {
    Write-Host "模式: 不使用缓存 (--no-cache)" -ForegroundColor Yellow
    $buildArgs += "--no-cache"
} else {
    Write-Host "模式: 使用缓存挂载加速" -ForegroundColor Green
    Write-Host "Maven 依赖和 npm 包将被缓存，后续构建会更快" -ForegroundColor Green
}

# 执行构建
Write-Host ""
Write-Host "开始构建镜像..." -ForegroundColor Cyan
& docker @buildArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "构建失败!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "构建完成!" -ForegroundColor Green

# 如果需要推送
if ($Push) {
    Write-Host ""
    Write-Host "推送镜像到仓库..." -ForegroundColor Cyan
    docker compose push
}

Write-Host ""
Write-Host "启动服务: docker compose up -d" -ForegroundColor Cyan
