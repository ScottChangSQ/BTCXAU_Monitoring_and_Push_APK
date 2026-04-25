param(
    [string]$RepoRoot = "",
    [string]$BundleRoot = "",
    [string]$PythonExe = "python",
    [string]$EnvTemplatePath = ""
)

$ErrorActionPreference = "Stop"
$minimumPythonMajor = 3
$minimumPythonMinor = 8

# 解析部署根目录，兼容“完整仓库根目录”和“部署包根目录”两种布局。
function Resolve-DeploymentLayout {
    param(
        [string]$RepoRootValue,
        [string]$BundleRootValue
    )

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($BundleRootValue)) {
        $candidates += [PSCustomObject]@{ Type = "bundle"; Root = $BundleRootValue }
    }
    if (-not [string]::IsNullOrWhiteSpace($RepoRootValue)) {
        $candidates += [PSCustomObject]@{ Type = "repo"; Root = $RepoRootValue }
    }
    if ($candidates.Count -eq 0) {
        $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
        $bundleCandidate = Split-Path -Parent $scriptDir
        $candidates += [PSCustomObject]@{ Type = "bundle"; Root = $bundleCandidate }
        $repoCandidate = (Resolve-Path (Join-Path $scriptDir "..\..\..")).Path
        $candidates += [PSCustomObject]@{ Type = "repo"; Root = $repoCandidate }
    }

    foreach ($candidate in $candidates) {
        if (-not (Test-Path $candidate.Root)) {
            continue
        }

        $resolvedRoot = (Resolve-Path $candidate.Root).Path
        if ($candidate.Type -eq "bundle") {
            $gatewayDir = Join-Path $resolvedRoot "mt5_gateway"
            $windowsDir = Join-Path $resolvedRoot "windows"
        } else {
            $gatewayDir = Join-Path $resolvedRoot "bridge\mt5_gateway"
            $windowsDir = Join-Path $resolvedRoot "deploy\tencent\windows"
        }

        if ((Test-Path $gatewayDir) -and (Test-Path $windowsDir)) {
            return [PSCustomObject]@{
                Root = $resolvedRoot
                GatewayDir = $gatewayDir
                WindowsDir = $windowsDir
                Layout = $candidate.Type
            }
        }
    }

    throw "Deployment root not found. Provide -RepoRoot <repo> or -BundleRoot <bundle>."
}

$layout = Resolve-DeploymentLayout -RepoRootValue $RepoRoot -BundleRootValue $BundleRoot
$gatewayDir = $layout.GatewayDir
$requirementsStampPath = Join-Path $gatewayDir ".requirements.sha256"

# 计算 requirements 哈希，并与启动脚本共用同一份依赖标记文件。
function Get-FileSha256 {
    param(
        [string]$PathValue
    )

    if (-not (Test-Path $PathValue)) {
        return ""
    }

    $stream = [System.IO.File]::OpenRead($PathValue)
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hashBytes = $sha256.ComputeHash($stream)
            return ([System.BitConverter]::ToString($hashBytes)).Replace("-", "")
        }
        finally {
            $sha256.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

# 读取某个 Python 命令的版本与位数，失败时返回 null。
function Get-PythonVersionInfo {
    param(
        [string]$FilePath,
        [string[]]$PrefixArguments = @()
    )

    $versionOutput = & $FilePath @PrefixArguments -c "import sys, struct; print('{0}.{1}.{2}'.format(sys.version_info[0], sys.version_info[1], struct.calcsize('P') * 8))" 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    $versionText = ""
    if ($versionOutput -is [System.Array]) {
        $versionText = ($versionOutput | Select-Object -First 1).ToString().Trim()
    }
    elseif ($null -ne $versionOutput) {
        $versionText = $versionOutput.ToString().Trim()
    }

    if ([string]::IsNullOrWhiteSpace($versionText)) {
        return $null
    }

    $parts = $versionText.Split(".")
    if ($parts.Count -lt 3) {
        return $null
    }

    return [PSCustomObject]@{
        Major = [int]$parts[0]
        Minor = [int]$parts[1]
        Bits = [int]$parts[2]
        Display = ($parts[0] + "." + $parts[1])
    }
}

# 选择满足最低版本和位数要求的 Python 命令；优先尊重传入值，不够时回退到 Windows py 启动器。
function Resolve-CompatiblePythonCommand {
    param(
        [string]$PreferredPythonExe
    )

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($PreferredPythonExe)) {
        $candidates += [PSCustomObject]@{
            FilePath = $PreferredPythonExe
            PrefixArguments = @()
            Label = $PreferredPythonExe
        }
    }
    $candidates += [PSCustomObject]@{
        FilePath = "py"
        PrefixArguments = @("-3")
        Label = "py -3"
    }

    foreach ($candidate in $candidates) {
        $command = Get-Command $candidate.FilePath -ErrorAction SilentlyContinue
        if (-not $command) {
            continue
        }

        $versionInfo = Get-PythonVersionInfo -FilePath $candidate.FilePath -PrefixArguments $candidate.PrefixArguments
        if ($null -eq $versionInfo) {
            continue
        }

        if (
            (($versionInfo.Major -gt $minimumPythonMajor) -or (($versionInfo.Major -eq $minimumPythonMajor) -and ($versionInfo.Minor -ge $minimumPythonMinor))) -and
            ($versionInfo.Bits -eq 64)
        ) {
            return [PSCustomObject]@{
                FilePath = $candidate.FilePath
                PrefixArguments = @($candidate.PrefixArguments)
                Label = $candidate.Label
                Version = $versionInfo.Display
                Bits = $versionInfo.Bits
            }
        }
    }

    throw "Python 3.8 or newer (64-bit) is required for the MT5 gateway bundle."
}

# 通过旧版已验证可用的 cmd 转发链执行原生命令，确保 pip stderr 会进入部署日志。
function Invoke-NativeCommandSafely {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    $commandParts = @('"' + $FilePath.Replace('"', '""') + '"')
    foreach ($argument in $Arguments) {
        $commandParts += '"' + $argument.Replace('"', '""') + '"'
    }
    $commandLine = ($commandParts -join " ") + " 2>&1"
    & cmd.exe /d /s /c $commandLine | ForEach-Object { $_ }
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        throw "Native command failed: $commandLine (exit code $exitCode)"
    }
}

# 旧虚拟环境损坏或被锁定时，自动改名备份并重建，避免部署时手工处理 .venv。
function Backup-GatewayVenv {
    param(
        [string]$VenvPath,
        [string]$Reason
    )

    if (-not (Test-Path $VenvPath)) {
        return ""
    }

    $resolvedGatewayDir = (Resolve-Path $gatewayDir).Path.TrimEnd("\")
    $resolvedVenvPath = (Resolve-Path $VenvPath).Path
    if (-not $resolvedVenvPath.StartsWith($resolvedGatewayDir + "\", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refuse to rename venv outside gateway directory: $resolvedVenvPath"
    }

    $stamp = Get-Date -Format "yyyyMMddHHmmss"
    $backupName = ".venv.bak-" + $stamp
    $backupPath = Join-Path $gatewayDir $backupName
    $index = 1
    while (Test-Path $backupPath) {
        $backupName = ".venv.bak-" + $stamp + "-" + $index
        $backupPath = Join-Path $gatewayDir $backupName
        $index += 1
    }

    Write-Host ("Rebuilding existing .venv. Reason: " + $Reason)
    try {
        Rename-Item -LiteralPath $resolvedVenvPath -NewName $backupName -ErrorAction Stop
    }
    catch {
        Write-Host ("Rename .venv failed, trying to repair directory permissions: " + $_.Exception.Message)
        & takeown.exe /F $resolvedVenvPath /R /D Y | ForEach-Object { $_ }
        & icacls.exe $resolvedVenvPath /grant "*S-1-5-32-544:F" /T | ForEach-Object { $_ }
        Rename-Item -LiteralPath $resolvedVenvPath -NewName $backupName -ErrorAction Stop
    }

    if (Test-Path $requirementsStampPath) {
        Remove-Item -LiteralPath $requirementsStampPath -Force -ErrorAction SilentlyContinue
    }
    Write-Host ("Old .venv moved to: " + $backupPath)
    return $backupPath
}

function Initialize-GatewayVenv {
    param($PythonCommand)

    $venvPath = Join-Path $gatewayDir ".venv"
    $venvArguments = @($PythonCommand.PrefixArguments + @("-m", "venv"))
    $hadExistingVenv = Test-Path $venvPath
    if ($hadExistingVenv) {
        $venvArguments += "--upgrade"
    }
    $venvArguments += ".venv"

    try {
        Invoke-NativeCommandSafely -FilePath $PythonCommand.FilePath -Arguments $venvArguments
        return
    }
    catch {
        if (-not $hadExistingVenv) {
            throw
        }
        $reason = $_.Exception.Message
        Backup-GatewayVenv -VenvPath $venvPath -Reason $reason | Out-Null
        $freshVenvArguments = @($PythonCommand.PrefixArguments + @("-m", "venv", ".venv"))
        Invoke-NativeCommandSafely -FilePath $PythonCommand.FilePath -Arguments $freshVenvArguments
    }
}

$resolvedPythonCommand = Resolve-CompatiblePythonCommand -PreferredPythonExe $PythonExe
Write-Host ("Using Python runtime: " + $resolvedPythonCommand.Label + " (" + $resolvedPythonCommand.Version + ", " + $resolvedPythonCommand.Bits + "-bit)")

if ([string]::IsNullOrWhiteSpace($EnvTemplatePath)) {
    $EnvTemplatePath = Join-Path $layout.WindowsDir ".env.example"
}

Set-Location $gatewayDir

Initialize-GatewayVenv -PythonCommand $resolvedPythonCommand

$venvPython = Join-Path $gatewayDir ".venv\Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
    throw "venv python not found: $venvPython"
}

Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @("-m", "pip", "install", "--upgrade", "pip")
Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @("-m", "pip", "install", "-r", "requirements.txt")

$requirementsHash = Get-FileSha256 -PathValue (Join-Path $gatewayDir "requirements.txt")
Set-Content -LiteralPath $requirementsStampPath -Value $requirementsHash -Encoding UTF8

$envFile = Join-Path $gatewayDir ".env"
if (-not (Test-Path $envFile)) {
    if (-not (Test-Path $EnvTemplatePath)) {
        throw "Env template not found: $EnvTemplatePath"
    }
    Copy-Item $EnvTemplatePath $envFile
    Write-Host "Created $envFile from $EnvTemplatePath"
    Write-Host "Set GATEWAY_AUTH_TOKEN in the generated .env before starting the gateway."
} else {
    Write-Host ".env already exists, skipped template copy."
}

Write-Host "Bootstrap completed."
Write-Host ("Next step: edit " + (Join-Path $gatewayDir ".env") + " and run start_gateway.ps1")
