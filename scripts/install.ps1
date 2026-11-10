param([string]$Python = 'python')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath (Join-Path $projectRoot 'agent-service')
if (!(Test-Path -LiteralPath '.venv\Scripts\python.exe')) {
    & $Python -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw '创建 Python 虚拟环境失败。' }
}
& '.\.venv\Scripts\python.exe' -m pip install -r requirements.lock.txt
if ($LASTEXITCODE -ne 0) { throw '安装 Python 依赖失败。' }
Set-Location -LiteralPath (Join-Path $projectRoot 'frontend')
& npm.cmd ci
if ($LASTEXITCODE -ne 0) { throw '安装前端依赖失败。' }
Set-Location -LiteralPath (Join-Path $projectRoot 'backend')
& mvn.cmd -DskipTests package
if ($LASTEXITCODE -ne 0) { throw '构建 Java 后端失败。' }
Write-Output '依赖安装和 Java 构建完成。请初始化独立数据库并配置 .env。'
