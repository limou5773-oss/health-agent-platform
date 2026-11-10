param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('backend', 'agent', 'frontend')]
    [string]$Service
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (!(Test-Path -LiteralPath $envPath)) {
    throw '请先运行 scripts/init-env.ps1 并填写 .env。'
}
foreach ($line in Get-Content -LiteralPath $envPath -Encoding utf8) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $pair = $trimmed.Split('=', 2)
    if ($pair.Length -ne 2) { throw '.env 存在缺少等号的配置行。' }
    [Environment]::SetEnvironmentVariable($pair[0].Trim(), $pair[1].Trim(), 'Process')
}

switch ($Service) {
    'backend' {
        Set-Location -LiteralPath (Join-Path $projectRoot 'backend')
        & mvn.cmd spring-boot:run
    }
    'agent' {
        Set-Location -LiteralPath (Join-Path $projectRoot 'agent-service')
        & '.\.venv\Scripts\python.exe' -m uvicorn app.main:app --host 127.0.0.1 --port 8001
    }
    'frontend' {
        Set-Location -LiteralPath (Join-Path $projectRoot 'frontend')
        & npm.cmd run dev -- --host 127.0.0.1
    }
}
exit $LASTEXITCODE
