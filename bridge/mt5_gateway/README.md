# MT5 Bridge Gateway

这个目录提供 MT5 账户数据网关，负责把本机 MT5 终端里的账户、持仓、交易和净值曲线整理成 Android App 可直接读取的 HTTP 接口。

## 支持模式

- `pull`
  - Python 通过 `MetaTrader5` 包直接读取本机 MT5 终端。
- `ea`
  - MT5 EA 主动把快照推送到网关。
- `auto`
  - 推荐模式；优先使用新鲜 EA 快照，不满足时回退到 Python 直连。

## API

- `GET /health`
- `GET /v1/source`
- `GET /v1/snapshot?range=1d|7d|1m|3m|1y|all`
- `GET /v1/summary?range=1d|7d|1m|3m|1y|all`
- `GET /v1/live?range=1d|7d|1m|3m|1y|all`
- `GET /v1/pending?range=1d|7d|1m|3m|1y|all`
- `GET /v1/trades?range=1d|7d|1m|3m|1y|all`
- `GET /v1/curve?range=1d|7d|1m|3m|1y|all`
- `GET /binance-rest/fapi/v1/klines?...`
- `WebSocket /binance-ws/ws/{stream}` 或 `/binance-ws/stream?streams=...`
- `POST /v1/ea/snapshot`
- 完整返回结构见 `API.md`

## 1）配置

1. 复制 `.env.example` 为 `.env`
2. 至少填写这些值：
   - `MT5_LOGIN=12345678`
   - `MT5_PASSWORD=your_investor_password`
   - `MT5_SERVER=ICMarketsSC-MT5-6`
3. 建议同时确认：
   - `MT5_PATH=C:\Program Files\MetaTrader 5\terminal64.exe`
   - `MT5_TIME_OFFSET_MINUTES=0`
   - `MT5_SERVER_ALIASES=ICMarketsSC-MT5-6,ICMarketsSC-MT5-5,ICMarketsSC-MT5`
   - `GATEWAY_HOST=127.0.0.1`
   - `GATEWAY_PORT=8787`
   - `GATEWAY_MODE=auto`
   - `SNAPSHOT_BUILD_CACHE_MS=8000`
   - `SNAPSHOT_BUILD_MAX_STALE_MS=30000`
   - `SNAPSHOT_BUILD_CACHE_MAX_ENTRIES=6`
   - `SNAPSHOT_SYNC_CACHE_MAX_ENTRIES=12`

说明：
- 如果网关前面还有 Caddy / Nginx，建议 `GATEWAY_HOST` 固定为 `127.0.0.1`，不要直接对公网暴露 `8787`。
- 如果 MT5 返回的成交时间比你实际本地时间固定慢 8 小时，可把 `MT5_TIME_OFFSET_MINUTES` 改成 `480`；改完后 `/v1/trades`、历史成交点和账户曲线会一起按同一口径修正。
- 这个网关现在也可直接转发 Binance REST / WebSocket，方便 App 统一只连韩国服务器。
- EA 推送活跃时，网关会在短时间内平滑续用最近一次快照缓存，减少固定轮询下“一快一慢”交替；同时快照缓存会按最近使用裁剪，避免历史范围缓存长期堆积占内存。

## 2）启动

```powershell
cd bridge\mt5_gateway
.\start_gateway.ps1
```

默认本机地址：

```text
http://127.0.0.1:8787
```

如果你想指定其他环境文件：

```powershell
.\start_gateway.ps1 -EnvFile ".env"
```

## 3）EA 推送模式（可选）

1. 打开 MT5 MetaEditor
2. 导入 `bridge/mt5_gateway/ea/MT5BridgePushEA.mq5`
3. 把 EA 挂到任意图表
4. 在 MT5 中打开：
   - `Tools -> Options -> Expert Advisors`
5. 允许 WebRequest 地址：
   - `http://127.0.0.1:8787`
6. 如果 `.env` 设置了 `EA_INGEST_TOKEN`，EA 的 `BridgeToken` 也要填同一个值

## 4）App 连接方式

- 模拟器默认可直接使用：
  - `http://10.0.2.2:8787`
- 物理手机或云服务器统一入口部署时，建议改成反向代理后的地址，例如：
  - `MT5_GATEWAY_BASE_URL=http://43.155.214.62:8787`
  - 或
  - `MT5_GATEWAY_BASE_URL=https://gateway.example.com/mt5`
- App 运行时也可以在设置页直接修改网关地址，Binance REST / WS 会自动跟随同一地址切换。

## 5）腾讯云部署

完整的香港 Windows 单机部署步骤、统一公网入口代理、Caddy / Nginx 样例都在：

- `deploy/tencent/README.md`

## 说明

- 这是只读监控链路，推荐使用投资者密码
- 需要保证 MT5 终端和网关进程持续在线
- 后台常驻建议优先使用 `/v1/live`，只有首次完整加载时再拉 `/v1/snapshot`
- 返回字段已经对齐 App 当前使用的数据结构：
  - `accountMeta`
  - `overviewMetrics`
  - `curvePoints`
  - `curveIndicators`
  - `positions`
  - `pendingOrders`
  - `trades`
  - `statsMetrics`
