param(
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

if (-not (Test-Path ".venv")) {
    python -m venv .venv
}

. .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt

if (Test-Path $EnvFile) {
    Write-Host "Using env file: $EnvFile"
} elseif (Test-Path ".env.example") {
    Write-Host "No .env found, falling back to .env.example"
    Copy-Item .env.example .env -Force
}

python server_v2.py
