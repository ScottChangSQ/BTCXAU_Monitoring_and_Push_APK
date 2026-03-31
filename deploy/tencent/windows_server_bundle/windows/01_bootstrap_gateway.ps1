param(
    [Parameter(Mandatory = $true)]
    [string]$BundleRoot,
    [string]$PythonExe = 'python',
    [string]$EnvTemplatePath = ''
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $BundleRoot)) {
    throw "BundleRoot not found: $BundleRoot"
}
$bundle = (Resolve-Path $BundleRoot).Path
$gatewayDir = Join-Path $bundle 'mt5_gateway'
if (-not (Test-Path $gatewayDir)) {
    throw "Gateway directory not found: $gatewayDir"
}

$pythonCmd = Get-Command $PythonExe -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    throw "Python executable not found: $PythonExe"
}

if ([string]::IsNullOrWhiteSpace($EnvTemplatePath)) {
    $EnvTemplatePath = Join-Path $bundle 'windows\.env.example'
}

Set-Location $gatewayDir

if (-not (Test-Path '.venv')) {
    & $PythonExe -m venv .venv
}

$venvPython = Join-Path $gatewayDir '.venv\Scripts\python.exe'
if (-not (Test-Path $venvPython)) {
    throw "venv python not found: $venvPython"
}

& $venvPython -m pip install --upgrade pip
& $venvPython -m pip install -r requirements.txt

$envFile = Join-Path $gatewayDir '.env'
if (-not (Test-Path $envFile)) {
    if (-not (Test-Path $EnvTemplatePath)) {
        throw "Env template not found: $EnvTemplatePath"
    }
    Copy-Item $EnvTemplatePath $envFile
    Write-Host "Created $envFile from $EnvTemplatePath"
} else {
    Write-Host '.env already exists, skipped template copy.'
}

Write-Host 'Bootstrap completed.'
Write-Host 'Next step: edit mt5_gateway\.env and run mt5_gateway\start_gateway.ps1'
