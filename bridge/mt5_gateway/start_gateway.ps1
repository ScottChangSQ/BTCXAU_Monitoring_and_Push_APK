# Starts the MT5 gateway on Windows and loads environment variables from the local env file.

param(
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir
$requirementsStampPath = Join-Path $scriptDir ".requirements.sha256"
$minimumPythonMajor = 3
$minimumPythonMinor = 8

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

# 校验网关启动所需的关键环境变量，缺失时直接阻止坏配置启动。
function Assert-RequiredGatewayEnv {
    param(
        [string[]]$Keys
    )

    $missingKeys = @()
    foreach ($key in $Keys) {
        $value = [System.Environment]::GetEnvironmentVariable($key, "Process")
        if ([string]::IsNullOrWhiteSpace($value)) {
            $missingKeys += $key
        }
    }

    if ($missingKeys.Count -gt 0) {
        throw ("Gateway required env missing or blank: " + ($missingKeys -join ", "))
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

# 校验虚拟环境里的 Python 版本是否达到运行网关的最低要求。
function Test-PythonVersionAtLeast {
    param(
        [string]$PythonPath,
        [int]$MinimumMajor,
        [int]$MinimumMinor
    )

    $versionOutput = & $PythonPath -c "import sys; print('{0}.{1}'.format(sys.version_info[0], sys.version_info[1]))" 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $false
    }

    $versionText = ""
    if ($versionOutput -is [System.Array]) {
        $versionText = ($versionOutput | Select-Object -First 1).ToString().Trim()
    }
    elseif ($null -ne $versionOutput) {
        $versionText = $versionOutput.ToString().Trim()
    }

    if ([string]::IsNullOrWhiteSpace($versionText)) {
        return $false
    }

    $parts = $versionText.Split(".")
    if ($parts.Count -lt 2) {
        return $false
    }

    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    return (($major -gt $MinimumMajor) -or (($major -eq $MinimumMajor) -and ($minor -ge $MinimumMinor)))
}

# 校验当前虚拟环境是否真的具备网关启动所需依赖，避免残缺环境被旧指纹误判为可用。
function Test-GatewayDependencyContract {
    param(
        [string]$PythonPath,
        [string[]]$ModuleNames
    )

    $moduleCheckScript = "import sys; __import__(sys.argv[1])"
    $missingModules = @()

    foreach ($moduleName in $ModuleNames) {
        & $PythonPath -c $moduleCheckScript $moduleName *> $null
        if ($LASTEXITCODE -ne 0) {
            $missingModules += $moduleName
        }
    }

    if ($missingModules.Count -gt 0) {
        Write-Host ("Python dependency import failed: " + ($missingModules -join ", "))
        return $false
    }

    return $true
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

    # Use a C# process runner to forward stdout/stderr without PowerShell runspace callbacks.
    if (-not ("NativeCommandRunner" -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;

public static class NativeCommandRunner
{
    public static int Run(string filePath, string argumentsLine, string workingDirectory)
    {
        var startInfo = new ProcessStartInfo(filePath, argumentsLine)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            WorkingDirectory = workingDirectory
        };

        using (var process = new Process())
        {
            process.StartInfo = startInfo;
            process.OutputDataReceived += (sender, eventArgs) =>
            {
                if (eventArgs.Data != null)
                {
                    Console.WriteLine(eventArgs.Data);
                }
            };
            process.ErrorDataReceived += (sender, eventArgs) =>
            {
                if (eventArgs.Data != null)
                {
                    Console.WriteLine(eventArgs.Data);
                }
            };
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            process.WaitForExit();
            return process.ExitCode;
        }
    }
}
'@
    }

    $escapedArguments = @()
    foreach ($argument in $Arguments) {
        $escapedArguments += '"' + $argument.Replace('"', '\"') + '"'
    }
    $argumentsLine = ($escapedArguments -join " ")
    $exitCode = [NativeCommandRunner]::Run($FilePath, $argumentsLine, $scriptDir)

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
if (-not (Test-PythonVersionAtLeast -PythonPath $venvPython -MinimumMajor 3 -MinimumMinor 8)) {
    throw "Python 3.8 or newer is required for the MT5 gateway runtime."
}

$requirementsHash = Get-FileSha256 -PathValue (Join-Path $scriptDir "requirements.txt")
$cachedRequirementsHash = ""
if (Test-Path $requirementsStampPath) {
    $cachedRequirementsHash = (Get-Content -LiteralPath $requirementsStampPath -ErrorAction SilentlyContinue | Select-Object -First 1).ToString().Trim()
}
$requirementsLines = @()
if (Test-Path (Join-Path $scriptDir "requirements.txt")) {
    $requirementsLines = Get-Content -Encoding UTF8 (Join-Path $scriptDir "requirements.txt") |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and -not $_.Trim().StartsWith("#") }
}
$dependencyModules = @()
if ($requirementsLines.Count -gt 0) {
    $dependencyModules = @("fastapi", "uvicorn", "MetaTrader5", "dotenv", "cryptography", "tzdata")
}
$dependencyContractOk = $false
if ($requirementsHash -eq $cachedRequirementsHash) {
    $dependencyContractOk = Test-GatewayDependencyContract -PythonPath $venvPython -ModuleNames $dependencyModules
}
if (($requirementsHash -ne $cachedRequirementsHash) -or (-not $dependencyContractOk)) {
    if (($requirementsHash -eq $cachedRequirementsHash) -and (-not $dependencyContractOk)) {
        Write-Host "Python dependency contract invalid, reinstalling dependencies..."
    }
    else {
        Write-Host "Installing Python dependencies..."
    }
    Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @(
        "-m", "pip", "install", "--disable-pip-version-check", "--quiet", "-r", "requirements.txt"
    )
    if (-not (Test-GatewayDependencyContract -PythonPath $venvPython -ModuleNames $dependencyModules)) {
        throw "Gateway dependency contract check failed after reinstall."
    }
    Set-Content -LiteralPath $requirementsStampPath -Value $requirementsHash -Encoding UTF8
}

$resolvedEnvFile = Resolve-EnvFilePath -PathValue $EnvFile
$defaultEnvFile = Join-Path $scriptDir ".env"
if (-not (Test-Path $resolvedEnvFile) -and (Test-Path ".env.example")) {
    Write-Host "No env file found, creating .env from .env.example"
    Copy-Item ".env.example" ".env" -Force
    $resolvedEnvFile = $defaultEnvFile
}

if (Test-Path $resolvedEnvFile) {
    Write-Host "Using env file: $resolvedEnvFile"
    Import-EnvFile -PathValue $resolvedEnvFile
} else {
    Write-Host "Env file not found, continuing with current process environment."
}

$currentIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
$currentSessionId = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
Write-Host ("Gateway process identity: " + $currentIdentity.Name)
Write-Host ("Gateway process session id: " + $currentSessionId)

Assert-RequiredGatewayEnv -Keys @(
    "MT5_LOGIN",
    "MT5_PASSWORD",
    "MT5_SERVER",
    "MT5_SERVER_TIMEZONE",
    "GATEWAY_AUTH_TOKEN"
)

Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @("server_v2.py")
