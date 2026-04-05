param(
    [Parameter(Mandatory = $true)]
    [string]$BundleRoot,
    [string]$TaskName = "MT5AdminPanelAutoStart",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BundleRoot)) {
    throw "BundleRoot not found: $BundleRoot"
}
$bundle = (Resolve-Path $BundleRoot).Path
$runner = Join-Path $bundle "windows\run_admin_panel.ps1"
if (-not (Test-Path $runner)) {
    throw "Runner script not found: $runner"
}

$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existing) {
    if (-not $Force) {
        throw "Task '$TaskName' already exists. Use -Force to replace."
    }
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}

$args = "-NoProfile -ExecutionPolicy Bypass -File `"$runner`" -BundleRoot `"$bundle`""
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $args
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal | Out-Null

Start-ScheduledTask -TaskName $TaskName
Write-Host "Task '$TaskName' is registered and started."
