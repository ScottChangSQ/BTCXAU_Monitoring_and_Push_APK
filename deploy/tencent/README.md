# 腾讯云香港 Windows 单机部署

这套部署文件对应的目标结构是：

```text
Android App
  -> /mt5
       -> 本机 MT5 Python 网关（127.0.0.1:8787）
  -> /binance-rest
       -> https://fapi.binance.com
  -> /binance-ws
       -> https://fstream.binance.com
```

适合你现在这种情况：

- 香港 Windows 服务器
- 只有 C 盘
- 远程桌面端口是 `3389`
- 已有公网 IP
- 想把 MT5 账户数据和 Binance 主入口都统一放到同一台服务器前面

## 1. 先准备什么

1. 腾讯云 CVM，建议 Windows Server 2019 / 2022
2. Python 3.10+
3. MT5 终端，并且已经在服务器里登录只读账户
4. 仓库放到服务器本地路径，例如：

```text
C:\BTCXAU_Monitoring_and_Push_APK
```

5. 可选：
   - 有域名：后面直接上 HTTPS / WSS
   - 没域名：先用公网 IP + HTTP / WS 跑通

如果你不想上传整个仓库，可以直接上传这个精简包：

```text
deploy/tencent/windows_server_bundle
```

它已经包含服务器真正需要的脚本、网关程序、EA 文件和 Caddy 样例。

## 2. 安全组和防火墙建议

1. 放行 `3389`
   - 只允许你自己的固定 IP 访问
2. 放行 `80`
   - 没域名时先用于 HTTP 联调
3. 放行 `443`
   - 有域名后用于 HTTPS / WSS
4. 不要对公网开放 `8787`
   - MT5 网关只监听本机 `127.0.0.1`
5. 如果你要直接公网访问轻量管理面板，再放行 `8788`
   - 这是独立管理服务默认端口

## 3. 第一步：在服务器上准备 MT5 网关

在管理员 PowerShell 里执行：

```powershell
cd C:\BTCXAU_Monitoring_and_Push_APK
.\deploy\tencent\windows\01_bootstrap_gateway.ps1 -RepoRoot "C:\BTCXAU_Monitoring_and_Push_APK"
```

然后编辑运行环境文件：

```powershell
notepad C:\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\.env
```

至少确认这些值：

```properties
MT5_LOGIN=你的 MT5 账号
MT5_PASSWORD=你的投资者密码
MT5_SERVER=你的券商服务器名
MT5_PATH=C:\Program Files\MetaTrader 5\terminal64.exe
GATEWAY_HOST=127.0.0.1
GATEWAY_PORT=8787
GATEWAY_MODE=auto
ADMIN_PANEL_HOST=0.0.0.0
ADMIN_PANEL_PORT=8788
ADMIN_GATEWAY_URL=http://127.0.0.1:8787
```

## 4. 第二步：先在服务器本机把 MT5 跑通

```powershell
cd C:\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway
.\start_gateway.ps1
```

本机检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
Invoke-RestMethod http://127.0.0.1:8787/v1/source
Invoke-RestMethod http://127.0.0.1:8787/v1/summary?range=all
Invoke-RestMethod http://127.0.0.1:8787/v1/snapshot?range=1d
```

如果这一步没有通，不要先做公网代理，先把 MT5 账号、服务器名、终端路径修对。

## 5. 第三步：启动轻量管理面板

管理面板是独立进程，默认同时支持服务器本机 `localhost` 和公网访问：

```powershell
cd C:\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway
.\start_admin_panel.ps1
```

默认地址：

```text
http://127.0.0.1:8788
http://你的公网IP:8788
```

当前面板支持：

- 查看网关健康状态和来源状态
- 查看最近日志
- 编辑 `bridge\mt5_gateway\.env`
- 修改异常规则配置
- 清空网关运行时缓存
- 启动 / 停止 / 重启：
  - MT5 网关
  - MT5 客户端
  - Caddy
  - Nginx

## 6. 第四步：注册开机自启

本机联通后执行：

```powershell
cd C:\BTCXAU_Monitoring_and_Push_APK
.\deploy\tencent\windows\02_register_startup_task.ps1 -RepoRoot "C:\BTCXAU_Monitoring_and_Push_APK" -Force
.\deploy\tencent\windows\04_register_admin_panel_task.ps1 -RepoRoot "C:\BTCXAU_Monitoring_and_Push_APK" -Force
.\deploy\tencent\windows\03_run_healthcheck.ps1
```

这样服务器重启后会自动拉起网关和管理面板。

## 7. 第五步：把 MT5 和 Binance 都挂到同一个公网入口

### 方案 A：Windows 同机 Caddy

最适合你现在这台香港 Windows 服务器。

1. 安装 Caddy
2. 把 `deploy/tencent/windows/Caddyfile.example` 复制成实际配置
3. 只保留一个站点块：
   - 没域名：保留 `http://PUBLIC_HOST_OR_IP`
   - 有域名：保留 `gateway.example.com`
4. 替换实际公网 IP 或域名
   - 你当前公网 IP 可直接用 `43.155.214.62`
5. 启动或重载 Caddy

这份样例已经把 3 条路径配好了：

- `/mt5/*`
- `/binance-rest/*`
- `/binance-ws/*`

### 方案 B：Linux Nginx 反向代理

如果后面你把公网入口单独放到 Linux 机器，可用：

- `deploy/tencent/nginx/mt5_gateway.conf`

## 8. 第六步：把 APK 指到这台服务器

项目已经支持通过 `gradle.properties` 改这 3 个入口：

```properties
MT5_GATEWAY_BASE_URL=http://43.155.214.62/mt5
BINANCE_REST_BASE_URL=http://43.155.214.62/binance-rest/fapi/v1/klines
BINANCE_WS_BASE_URL=ws://43.155.214.62/binance-ws/ws/
```

如果你后面有域名并启用 HTTPS，就改成：

```properties
MT5_GATEWAY_BASE_URL=https://gateway.example.com/mt5
BINANCE_REST_BASE_URL=https://gateway.example.com/binance-rest/fapi/v1/klines
BINANCE_WS_BASE_URL=wss://gateway.example.com/binance-ws/ws/
```

然后重新打包：

```powershell
.\gradlew.bat :app:assembleDebug -x lint
```

## 9. 第七步：公网联调

先测 MT5：

```powershell
Invoke-RestMethod http://43.155.214.62/mt5/health
Invoke-RestMethod http://43.155.214.62/mt5/v1/source
Invoke-RestMethod http://43.155.214.62/mt5/v1/live?range=all
Invoke-RestMethod http://43.155.214.62/mt5/v1/pending?range=all
Invoke-RestMethod http://43.155.214.62/mt5/v1/trades?range=all
Invoke-RestMethod http://43.155.214.62/mt5/v1/curve?range=all
```

再测 Binance REST：

```powershell
Invoke-RestMethod "http://43.155.214.62/binance-rest/fapi/v1/klines?symbol=BTCUSDT&interval=1m&limit=2"
```

如果这两个都能返回内容，APK 侧基本就可以切过去了。

再测轻量管理面板：

```powershell
Invoke-WebRequest http://127.0.0.1:8788
```

如果返回 HTML 内容，说明管理面板已正常运行。

## 10. 日常运维

1. 保持 MT5 终端在线
2. 保持计划任务 `MT5GatewayAutoStart` 正常
3. 如使用管理面板自启，保持计划任务 `MT5AdminPanelAutoStart` 正常
3. 查看日志目录：

```text
bridge\mt5_gateway\logs
```

4. 如果 MT5 密码或令牌泄露，立即更换

## 11. 一个重要说明

当前版本里，Binance 主入口可以先走你这台服务器，但应用内部仍保留官方地址安全回退；这样做是为了代理异常时行情不至于完全中断。
