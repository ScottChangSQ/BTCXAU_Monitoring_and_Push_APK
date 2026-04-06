param(
    [string]$RepoRoot = "",
    [string]$BundleRoot = "",
    [string]$PythonExe = "python",
    [string]$EnvTemplatePath = ""
)

$ErrorActionPreference = "Stop"

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

$pythonCmd = Get-Command $PythonExe -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    throw "Python executable not found: $PythonExe"
}

if ([string]::IsNullOrWhiteSpace($EnvTemplatePath)) {
    $EnvTemplatePath = Join-Path $layout.WindowsDir ".env.example"
}

Set-Location $gatewayDir

if (-not (Test-Path ".venv")) {
    & $PythonExe -m venv .venv
}

$venvPython = Join-Path $gatewayDir ".venv\Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
    throw "venv python not found: $venvPython"
}

& $venvPython -m pip install --upgrade pip
& $venvPython -m pip install -r requirements.txt

$envFile = Join-Path $gatewayDir ".env"
if (-not (Test-Path $envFile)) {
    if (-not (Test-Path $EnvTemplatePath)) {
        throw "Env template not found: $EnvTemplatePath"
    }
    Copy-Item $EnvTemplatePath $envFile
    Write-Host "Created $envFile from $EnvTemplatePath"
} else {
    Write-Host ".env already exists, skipped template copy."
}

Write-Host "Bootstrap completed."
Write-Host ("Next step: edit " + (Join-Path $gatewayDir ".env") + " and run start_gateway.ps1")
