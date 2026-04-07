# 本地模拟 CI 环境验证脚本 (Windows PowerShell)

Write-Host "===== 本地 CI 验证 =====" -ForegroundColor Cyan
Write-Host ""

# 检查 Node 版本
Write-Host "1. 检查 Node.js 版本..." -ForegroundColor Yellow
if (Test-Path .nvmrc) {
    $EXPECTED_NODE = Get-Content .nvmrc
    $CURRENT_NODE = node --version
    Write-Host "   期望版本: v$EXPECTED_NODE"
    Write-Host "   当前版本: $CURRENT_NODE"
    if (-not $CURRENT_NODE.StartsWith("v$EXPECTED_NODE")) {
        Write-Host "   ⚠️ 版本不匹配！请切换到正确版本" -ForegroundColor Red
        exit 1
    }
}
Write-Host "   ✅ Node.js 版本正确" -ForegroundColor Green
Write-Host ""

# 检查前端
Write-Host "2. 验证前端构建..." -ForegroundColor Yellow
Set-Location frontend

# 清理旧依赖
Write-Host "   清理 node_modules..."
Remove-Item -Recurse -Force node_modules -ErrorAction SilentlyContinue
Remove-Item package-lock.json -ErrorAction SilentlyContinue

# 安装依赖
Write-Host "   安装依赖 (npm install)..."
npm install

# 构建
Write-Host "   执行构建..."
npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Host "   ❌ 前端构建失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "===== 前端验证通过 ✅ =====" -ForegroundColor Green
Write-Host ""

# 检查后端
Set-Location ..\backend
Write-Host "3. 验证后端构建..." -ForegroundColor Yellow
.\mvnw.cmd clean compile -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "   ❌ 后端编译失败" -ForegroundColor Red
    exit 1
}
Write-Host "   ✅ 后端编译通过" -ForegroundColor Green

Write-Host ""
Write-Host "===== 所有验证通过 ✅ =====" -ForegroundColor Green
Write-Host "可以安全地推送代码到远程仓库"
Set-Location ..
