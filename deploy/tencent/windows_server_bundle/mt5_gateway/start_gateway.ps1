# Starts the MT5 gateway on Windows and loads environment variables from the local env file.

param(
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

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

# Runs a native command through cmd.exe so stderr becomes regular log output while exit codes stay intact.
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

if (-not (Test-Path ".venv")) {
    Invoke-NativeCommandSafely -FilePath "python" -Arguments @("-m", "venv", ".venv")
}

$venvPython = Join-Path $scriptDir ".venv\Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
    throw "venv python not found: $venvPython"
}

Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @("-m", "pip", "install", "-r", "requirements.txt")

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

Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @("server_v2.py")
