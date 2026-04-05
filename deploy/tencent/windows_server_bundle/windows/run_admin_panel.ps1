param(
    [Parameter(Mandatory = $true)]
    [string]$BundleRoot,
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BundleRoot)) {
    throw "BundleRoot not found: $BundleRoot"
}
$bundle = (Resolve-Path $BundleRoot).Path
$gatewayDir = Join-Path $bundle "mt5_gateway"
$startScript = Join-Path $gatewayDir "start_admin_panel.ps1"
if (-not (Test-Path $startScript)) {
    throw "start_admin_panel.ps1 not found: $startScript"
}

$logsDir = Join-Path $gatewayDir "logs"
New-Item -Path $logsDir -ItemType Directory -Force | Out-Null

while ($true) {
    $ts = Get-Date -Format "yyyyMMdd-HHmmss"
    $logFile = Join-Path $logsDir "admin-panel-$ts.log"
    try {
        Set-Location $gatewayDir
        & $startScript -EnvFile $EnvFile *> $logFile
    } catch {
        $_ | Out-String | Tee-Object -FilePath $logFile -Append
    }
    Start-Sleep -Seconds 5
}
