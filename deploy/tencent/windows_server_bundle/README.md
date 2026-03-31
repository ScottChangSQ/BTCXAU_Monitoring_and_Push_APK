# Windows 服务器精简部署包

这个文件夹就是要上传到服务器的最小集合，不需要再上传整个仓库。

## 目录说明

- `mt5_gateway/`
  - MT5 网关主程序、依赖清单、环境示例、EA 文件
- `windows/`
  - Windows 部署脚本、Caddy 反向代理样例

## 推荐上传路径

```text
C:\mt5_bundle
```

上传后目录应类似：

```text
C:\mt5_bundle
├─ mt5_gateway
└─ windows
```

## 第一步：初始化环境

管理员 PowerShell：

```powershell
cd C:\mt5_bundle
.\windows\01_bootstrap_gateway.ps1 -BundleRoot 'C:\mt5_bundle'
```

## 第二步：填写 MT5 配置

```powershell
notepad C:\mt5_bundle\mt5_gateway\.env
```

至少填写：

```properties
MT5_LOGIN=你的MT5账号
MT5_PASSWORD=你的投资者密码
MT5_SERVER=你的券商服务器名
MT5_PATH=C:\Program Files\MetaTrader 5\terminal64.exe
GATEWAY_HOST=127.0.0.1
GATEWAY_PORT=8787
GATEWAY_MODE=auto
```

## 第三步：手动启动检查

```powershell
cd C:\mt5_bundle\mt5_gateway
.\start_gateway.ps1
```

另开一个 PowerShell：

```powershell
cd C:\mt5_bundle
.\windows\03_run_healthcheck.ps1
Invoke-RestMethod http://127.0.0.1:8787/v1/live?range=all
Invoke-RestMethod http://127.0.0.1:8787/v1/pending?range=all
Invoke-RestMethod http://127.0.0.1:8787/v1/trades?range=all
Invoke-RestMethod http://127.0.0.1:8787/v1/curve?range=all
```

## 第四步：注册开机自启

```powershell
cd C:\mt5_bundle
.\windows\02_register_startup_task.ps1 -BundleRoot 'C:\mt5_bundle' -Force
```

## 第五步：公网入口

如果你想让手机访问这台服务器：

1. 安装 Caddy
2. 使用 `windows\Caddyfile.example`
3. 把 `PUBLIC_HOST_OR_IP` 改成你的公网 IP：`43.155.214.62`
4. 启动 Caddy

## APK 侧地址

你本地打包 APK 时可填写：

```properties
MT5_GATEWAY_BASE_URL=http://43.155.214.62/mt5
BINANCE_REST_BASE_URL=http://43.155.214.62/binance-rest/fapi/v1/klines
BINANCE_WS_BASE_URL=ws://43.155.214.62/binance-ws/ws/
```

说明：

- App 后台常驻时会优先走 `/mt5/v1/live`
- 打开账户页后首次会拉 `/mt5/v1/snapshot`，后续再分开拉 `/mt5/v1/pending`、`/mt5/v1/trades`、`/mt5/v1/curve`
