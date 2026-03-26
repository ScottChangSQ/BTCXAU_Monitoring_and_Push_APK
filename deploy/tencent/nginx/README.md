# Nginx Reverse Proxy Sample

This sample proxies `https://mt5-api.example.com` to local gateway `http://127.0.0.1:8787`.

## Usage

1. Replace domain and cert paths in `mt5_gateway.conf`.
2. Install the file into `/etc/nginx/conf.d/`.
3. Validate and reload:

```bash
nginx -t
systemctl reload nginx
```
