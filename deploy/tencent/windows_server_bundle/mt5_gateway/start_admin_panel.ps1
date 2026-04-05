# Starts the lightweight admin panel service and loads environment variables from the local env file.

param(
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir
$requirementsStampPath = Join-Path $scriptDir ".requirements.sha256"

# 强制当前脚本使用 UTF-8 输出，减少日志中的乱码前缀。
function Set-Utf8Console {
    chcp 65001 > $null
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [Console]::InputEncoding = $utf8
    [Console]::OutputEncoding = $utf8
    $global:OutputEncoding = $utf8
    $env:PYTHONIOENCODING = "utf-8"
}

# Resolves the absolute path of the env file.
function Resolve-EnvFilePath {
    param(
        [string]$PathValue
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return (Join-Path $scriptDir ".env")
    }
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }
    return (Join-Path $scriptDir $PathValue)
}

# Imports key-value pairs from the env file into the current process.
function Import-EnvFile {
    param(
        [string]$PathValue
    )

    $lines = Get-Content -Encoding UTF8 $PathValue
    foreach ($rawLine in $lines) {
        $line = ""
        if ($null -ne $rawLine) {
            $line = $rawLine.ToString().Trim()
        }
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line.StartsWith("#")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }

        $key = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()
        if ([string]::IsNullOrWhiteSpace($key)) {
            continue
        }

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        Set-Item -Path ("Env:" + $key) -Value $value
    }
}

# 计算文件内容哈希，用来判断依赖是否真的发生变化。
function Get-FileSha256 {
    param(
        [string]$PathValue
    )

    if (-not (Test-Path $PathValue)) {
        return ""
    }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $PathValue).Hash
}

# Runs a native command and preserves exit code.
function Invoke-NativeCommandSafely {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    $commandParts = @('"' + $FilePath.Replace('"', '""') + '"')
    foreach ($argument in $Arguments) {
        $commandParts += '"' + $argument.Replace('"', '""') + '"'
    }
    $commandLine = ($commandParts -join " ")

    $previousErrorPreference = $ErrorActionPreference
    try {
        # PowerShell 会把原生命令写到 stderr 的普通日志也包装成错误记录，这里临时放宽，只按退出码判断成败。
        $ErrorActionPreference = "Continue"
        & $FilePath @Arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }

    if ($exitCode -ne 0) {
        throw "Native command failed: $commandLine (exit code $exitCode)"
    }
}

Set-Utf8Console

if (-not (Test-Path ".venv")) {
    Invoke-NativeCommandSafely -FilePath "python" -Arguments @("-m", "venv", ".venv")
}

$venvPython = Join-Path $scriptDir ".venv\Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
    throw "venv python not found: $venvPython"
}

$requirementsHash = Get-FileSha256 -PathValue (Join-Path $scriptDir "requirements.txt")
$cachedRequirementsHash = ""
if (Test-Path $requirementsStampPath) {
    $cachedRequirementsHash = (Get-Content -LiteralPath $requirementsStampPath -ErrorAction SilentlyContinue | Select-Object -First 1).ToString().Trim()
}
if ($requirementsHash -ne $cachedRequirementsHash) {
    Write-Host "Installing Python dependencies..."
    Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @(
        "-m", "pip", "install", "--disable-pip-version-check", "--quiet", "-r", "requirements.txt"
    )
    Set-Content -LiteralPath $requirementsStampPath -Value $requirementsHash -Encoding UTF8
}

$resolvedEnvFile = Resolve-EnvFilePath -PathValue $EnvFile
if (Test-Path $resolvedEnvFile) {
    Write-Host "Using env file: $resolvedEnvFile"
    Import-EnvFile -PathValue $resolvedEnvFile
} else {
    Write-Host "Env file not found, continuing with current process environment."
}

Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @("admin_panel.py")
