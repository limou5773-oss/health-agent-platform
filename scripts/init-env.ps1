$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envPath) {
    throw '.env 已存在，请直接修改，不会覆盖现有配置。'
}
$jwtKey = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$internalKey = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$content = Get-Content -LiteralPath (Join-Path $projectRoot '.env.example') -Raw
$content = $content.Replace('JWT_SECRET=', 'JWT_SECRET=' + $jwtKey)
$content = $content.Replace('INTERNAL_SERVICE_KEY=', 'INTERNAL_SERVICE_KEY=' + $internalKey)
Set-Content -LiteralPath $envPath -Value $content -Encoding utf8
Write-Output '已生成本地 .env。请填写 MySQL 凭据和模型配置；密钥不输出到控制台。'
