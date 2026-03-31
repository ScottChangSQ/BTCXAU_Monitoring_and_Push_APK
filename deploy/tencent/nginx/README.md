# Nginx 统一入口样例

这个样例把同一个公网域名下的 3 条路径统一代理出去：

- `/mt5/*` -> 本机 `127.0.0.1:8787`
- `/binance-rest/*` -> `https://fapi.binance.com`
- `/binance-ws/*` -> `https://fstream.binance.com`

## 使用方法

1. 把 `gateway.example.com` 和证书路径改成你的实际值
2. 将文件放到 `/etc/nginx/conf.d/`
3. 执行校验并重载

```bash
nginx -t
systemctl reload nginx
```
