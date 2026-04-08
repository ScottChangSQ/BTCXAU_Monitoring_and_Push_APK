# Regression tests for the MT5 gateway startup script under scheduled-task style log piping.

function New-GatewayTestFixture {
    param(
        [string]$ServerScript
    )

    # Build a minimal temporary directory that mimics the server startup layout.
    $fixtureRoot = Join-Path $env:TEMP ("mt5-gateway-test-" + [Guid]::NewGuid().ToString())
    New-Item -Path $fixtureRoot -ItemType Directory -Force | Out-Null

    $sourceStartScript = Get-Content -Path "E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\start_gateway.ps1" -Raw -Encoding UTF8
    Set-Content -Path (Join-Path $fixtureRoot "start_gateway.ps1") -Value $sourceStartScript -Encoding UTF8
    Set-Content -Path (Join-Path $fixtureRoot "requirements.txt") -Value "" -Encoding UTF8
    Set-Content -Path (Join-Path $fixtureRoot ".env") -Value "GATEWAY_HOST=127.0.0.1" -Encoding UTF8
    Set-Content -Path (Join-Path $fixtureRoot "server_v2.py") -Value $ServerScript -Encoding UTF8

    $sourcePython = "E:\Github\BTCXAU_Monitoring_and_Push_APK\.venv\Scripts\python.exe"
    if (-not (Test-Path $sourcePython)) {
        $sourcePython = (Get-Command python).Source
    }
    & $sourcePython -m venv (Join-Path $fixtureRoot ".venv")

    return $fixtureRoot
}

function Invoke-StartGatewayThroughLogPipe {
    param(
        [string]$FixtureRoot
    )

    # Use Windows PowerShell to mimic the scheduled-task invocation chain.
    $entryScript = @"
`$ErrorActionPreference = 'Stop'
Set-Location '$FixtureRoot'
try {
    & '$FixtureRoot\start_gateway.ps1' -EnvFile '.env' *>&1 | Tee-Object -FilePath '$FixtureRoot\gateway.log'
    Write-Host 'TRY_OK'
} catch {
    Write-Host 'CAUGHT'
    `$_ | Out-String | Write-Host
}
"@

    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -Command $entryScript 2>&1
    return [PSCustomObject]@{
        ExitCode = $LASTEXITCODE
        Output = @($output | ForEach-Object { $_.ToString() })
        LogPath = Join-Path $FixtureRoot "gateway.log"
    }
}

Describe "start_gateway.ps1" {
    It "does not fail when python only writes informational stderr logs" {
        $fixtureRoot = New-GatewayTestFixture -ServerScript @'
import sys
sys.stderr.write("INFO: simulated uvicorn log\n")
'@

        try {
            $result = Invoke-StartGatewayThroughLogPipe -FixtureRoot $fixtureRoot
            $joinedOutput = $result.Output -join "`n"

            $result.ExitCode | Should Be 0
            $joinedOutput | Should Match "TRY_OK"
            $joinedOutput | Should Match "INFO: simulated uvicorn log"
            $joinedOutput | Should Not Match "CAUGHT"
        } finally {
            if (Test-Path $fixtureRoot) {
                Remove-Item -Path $fixtureRoot -Recurse -Force
            }
        }
    }

    It "still fails clearly when python exits with a non-zero status" {
        $fixtureRoot = New-GatewayTestFixture -ServerScript @'
import sys
sys.stderr.write("ERROR: simulated startup failure\n")
raise SystemExit(3)
'@

        try {
            $result = Invoke-StartGatewayThroughLogPipe -FixtureRoot $fixtureRoot
            $joinedOutput = $result.Output -join "`n"

            $result.ExitCode | Should Be 0
            $joinedOutput | Should Match "CAUGHT"
            $joinedOutput | Should Match "exit code 3"
            $joinedOutput | Should Match "ERROR: simulated startup failure"
        } finally {
            if (Test-Path $fixtureRoot) {
                Remove-Item -Path $fixtureRoot -Recurse -Force
            }
        }
    }
}
