# Tencent Cloud Deployment (MT5 Self-Hosted Gateway)

This package targets the architecture:

`Mobile App -> HTTPS Gateway -> MT5 data source (self-hosted)`

## 1. What you need

1. Tencent Cloud CVM (Windows Server 2019/2022 recommended).
2. Public domain name (recommended for HTTPS).
3. Python 3.10+ installed on the CVM.
4. MT5 terminal installed on the CVM and logged in with your read-only account.

## 2. Security group baseline

1. Allow inbound `443` from `0.0.0.0/0`.
2. Restrict inbound `3389` to your office/home fixed IP only.
3. Do not expose `8787` publicly.

## 3. Quick start on Windows CVM

Run in elevated PowerShell:

```powershell
cd D:\your-repo
.\deploy\tencent\windows\01_bootstrap_gateway.ps1 -RepoRoot "D:\your-repo"
```

Then edit the runtime env file:

```powershell
notepad D:\your-repo\bridge\mt5_gateway\.env
```

Start manually for smoke test:

```powershell
cd D:\your-repo\bridge\mt5_gateway
.\start_gateway.ps1
```

Check health:

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
Invoke-RestMethod http://127.0.0.1:8787/v1/source
```

If healthy, register startup task:

```powershell
cd D:\your-repo
.\deploy\tencent\windows\02_register_startup_task.ps1 -RepoRoot "D:\your-repo" -Force
.\deploy\tencent\windows\03_run_healthcheck.ps1
```

## 4. HTTPS exposure options

### Option A (recommended): Caddy on same Windows host

1. Install Caddy on the CVM.
2. Copy `deploy/tencent/windows/Caddyfile.example` to Caddy config and replace domain/email.
3. Reverse proxy to `127.0.0.1:8787`.

### Option B: Linux Nginx reverse proxy

Use config sample at `deploy/tencent/nginx/mt5_gateway.conf`.

## 5. App-side production endpoint switch

`app/build.gradle.kts` now supports a configurable build field:

```properties
# gradle.properties
MT5_GATEWAY_BASE_URL=https://mt5-api.example.com
```

Then rebuild:

```powershell
.\gradlew :app:assembleDebug -x lint
```

`AppConstants.MT5_GATEWAY_BASE_URL` reads from `BuildConfig.MT5_GATEWAY_BASE_URL`.

## 6. Operations checklist

1. Keep MT5 terminal process alive on CVM.
2. Keep gateway task running (`MT5GatewayAutoStart`).
3. Monitor logs under `bridge/mt5_gateway/logs`.
4. Rotate credentials if leaked.
