# 在 Windows 服务器上对当前部署包执行一键重启部署。

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$bundleRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$bundleParent = Split-Path -Parent $bundleRoot
$gatewayDir = Join-Path $bundleRoot "mt5_gateway"
$windowsDir = Join-Path $bundleRoot "windows"
$deployLogDir = Join-Path $windowsDir "logs"
$deployLogPath = Join-Path $deployLogDir ("deploy-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".log")

$gatewayTaskName = "MT5GatewayAutoStart"
$adminTaskName = "MT5AdminPanelAutoStart"
$bootstrapScript = Join-Path $windowsDir "01_bootstrap_gateway.ps1"
$registerGatewayTaskScript = Join-Path $windowsDir "02_register_startup_task.ps1"
$registerAdminTaskScript = Join-Path $windowsDir "04_register_admin_panel_task.ps1"

New-Item -ItemType Directory -Force -Path $deployLogDir | Out-Null
try {
    Start-Transcript -Path $deployLogPath -Force | Out-Null
}
catch {
}

Write-Host ("部署日志: " + $deployLogPath)

# 检查 PowerShell 脚本语法，避免运行到一半才发现脚本损坏。
function Test-PsScriptSyntax {
    param([string]$Path)

    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors) | Out-Null

    if ($errors -and $errors.Count -gt 0) {
        Write-Host ("脚本语法错误: " + $Path) -ForegroundColor Red
        foreach ($err in $errors) {
            Write-Host ("  行 " + $err.Extent.StartLineNumber + ": " + $err.Message) -ForegroundColor Yellow
        }
        throw "脚本语法检查失败: $Path"
    }
}

# 等待 HTTP 接口就绪。
function Wait-HttpOk {
    param(
        [string]$Url,
        [int]$MaxSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($MaxSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 5
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300) {
                return $resp
            }
        }
        catch {
        }
        Start-Sleep -Seconds 2
    }

    throw "等待接口超时: $Url"
}

# 解析 Caddy 可执行文件位置，兼容部署包内和上级目录两种布局。
function Resolve-CaddyExecutablePath {
    param(
        [string]$WindowsDir,
        [string]$BundleRoot,
        [string]$BundleParent
    )

    $candidates = @(
        (Join-Path $WindowsDir "caddy.exe"),
        (Join-Path $BundleRoot "caddy.exe"),
        (Join-Path $BundleParent "caddy.exe")
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "找不到 caddy.exe。已检查: $($candidates -join ', ')"
}

if (-not (Test-Path $bundleRoot)) { throw "找不到目录: $bundleRoot" }
if (-not (Test-Path $gatewayDir)) { throw "找不到目录: $gatewayDir" }
if (-not (Test-Path $windowsDir)) { throw "找不到目录: $windowsDir" }

Write-Host "== 1) 停止旧任务和旧进程 =="

Stop-ScheduledTask -TaskName $gatewayTaskName -ErrorAction SilentlyContinue
Stop-ScheduledTask -TaskName $adminTaskName -ErrorAction SilentlyContinue

Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object {
        (
            $_.Name -match '^pythonw?\.exe$' -and
            (
                $_.CommandLine -like "*server_v2.py*" -or
                $_.CommandLine -like "*admin_panel.py*"
            ) -and
            $_.CommandLine -like ("*" + $bundleParent + "*")
        ) -or
        (
            $_.Name -ieq "caddy.exe" -and
            $_.ExecutablePath -like ($bundleParent + "*")
        )
    } |
    ForEach-Object {
        Write-Host ("Stopping PID " + $_.ProcessId + " : " + $_.Name)
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

Start-Sleep -Seconds 2

Write-Host "== 2) 检查关键脚本语法 =="

$requiredScripts = @(
    (Join-Path $windowsDir "01_bootstrap_gateway.ps1"),
    (Join-Path $windowsDir "02_register_startup_task.ps1"),
    (Join-Path $windowsDir "04_register_admin_panel_task.ps1"),
    (Join-Path $windowsDir "run_gateway.ps1"),
    (Join-Path $windowsDir "run_admin_panel.ps1"),
    (Join-Path $gatewayDir "start_gateway.ps1"),
    (Join-Path $gatewayDir "start_admin_panel.ps1")
)

foreach ($script in $requiredScripts) {
    if (-not (Test-Path $script)) {
        throw "缺少脚本: $script"
    }
    Test-PsScriptSyntax -Path $script
}

Write-Host "== 3) 初始化 Python 环境 =="
& $bootstrapScript -BundleRoot $bundleRoot

Write-Host "== 4) 重新注册计划任务 =="
& $registerGatewayTaskScript -BundleRoot $bundleRoot -TaskName $gatewayTaskName -Force
& $registerAdminTaskScript -BundleRoot $bundleRoot -TaskName $adminTaskName -Force

Enable-ScheduledTask -TaskName $gatewayTaskName -ErrorAction SilentlyContinue | Out-Null
Enable-ScheduledTask -TaskName $adminTaskName -ErrorAction SilentlyContinue | Out-Null

Write-Host "== 5) 启动网关和管理面板 =="
Start-ScheduledTask -TaskName $gatewayTaskName
Start-ScheduledTask -TaskName $adminTaskName

Write-Host "== 6) 隐藏启动 Caddy =="
$caddyExe = Resolve-CaddyExecutablePath -WindowsDir $windowsDir -BundleRoot $bundleRoot -BundleParent $bundleParent
$caddyConfig = Join-Path $windowsDir "Caddyfile"
$caddyLogDir = Join-Path $windowsDir "logs"
$caddyOut = Join-Path $caddyLogDir "caddy-out.log"
$caddyErr = Join-Path $caddyLogDir "caddy-err.log"

if (-not (Test-Path $caddyConfig)) { throw "找不到 Caddyfile: $caddyConfig" }

Get-Process caddy -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $caddyLogDir | Out-Null

Start-Process `
    -WindowStyle Hidden `
    -FilePath $caddyExe `
    -ArgumentList @("run", "--config", $caddyConfig) `
    -WorkingDirectory $windowsDir `
    -RedirectStandardOutput $caddyOut `
    -RedirectStandardError $caddyErr

Write-Host "== 7) 等待服务就绪 =="
$directGateway = Wait-HttpOk -Url "http://127.0.0.1:8787/health" -MaxSeconds 90
$directAdmin = Wait-HttpOk -Url "http://127.0.0.1:8788/" -MaxSeconds 90
$proxyGateway = Wait-HttpOk -Url "http://127.0.0.1/health" -MaxSeconds 90

Write-Host "== 8) 检查 /admin/ 鉴权 =="
$adminProxyStatus = $null
try {
    $adminResp = Invoke-WebRequest http://127.0.0.1/admin/ -UseBasicParsing -TimeoutSec 5
    $adminProxyStatus = $adminResp.StatusCode
}
catch {
    if ($_.Exception.Response) {
        $adminProxyStatus = $_.Exception.Response.StatusCode.value__
    }
    else {
        throw
    }
}

Write-Host "== 9) 输出结果 =="
Write-Host "-- 本地监听端口 --"
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in 80, 2019, 8787, 8788 } |
    Select-Object LocalAddress, LocalPort, OwningProcess |
    Sort-Object LocalPort

Write-Host "-- 网关直连 --"
$directGateway | Select-Object StatusCode, Content

Write-Host "-- 管理面板直连 --"
$directAdmin | Select-Object StatusCode

Write-Host "-- Caddy 代理网关 --"
$proxyGateway | Select-Object StatusCode, Content

Write-Host "-- Caddy 代理管理面板鉴权 --"
Write-Host $adminProxyStatus

Write-Host "-- 最近日志 --"
Get-ChildItem (Join-Path $gatewayDir "logs") -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 10 Name, LastWriteTime, Length

Write-Host "部署完成。正常结果应为：8787/8788/80/2019 监听，127.0.0.1:8787/health 返回 200，127.0.0.1:8788/ 返回 200，127.0.0.1/admin/ 返回 401。"

try {
    Stop-Transcript | Out-Null
}
catch {
}
