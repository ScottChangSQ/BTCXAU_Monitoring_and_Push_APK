# MT5 Gateway API Contract

## GET `/v1/snapshot?range=1d|7d|1m|3m|1y|all`

Response JSON:

```json
{
  "accountMeta": {
    "login": "7400048",
    "server": "ICMarketsSC-MT5-6",
    "source": "MT5 Python Pull",
    "updatedAt": 1760000000000
  },
  "overviewMetrics": [{"name": "Total Asset", "value": "$100,000.00"}],
  "curvePoints": [{"timestamp": 1760000000000, "equity": 100000.0, "balance": 100000.0}],
  "curveIndicators": [{"name": "1D Return", "value": "+0.20%"}],
  "positions": [{
    "productName": "XAUUSD",
    "code": "XAUUSD",
    "quantity": 1.0,
    "sellableQuantity": 1.0,
    "costPrice": 2300.0,
    "latestPrice": 2310.0,
    "marketValue": 2310.0,
    "positionRatio": 0.2,
    "dayPnL": 100.0,
    "totalPnL": 200.0,
    "returnRate": 0.0043
  }],
  "trades": [{
    "timestamp": 1760000000000,
    "productName": "XAUUSD",
    "code": "XAUUSD",
    "side": "Buy",
    "price": 2300.0,
    "quantity": 1.0,
    "amount": 2300.0,
    "fee": 2.3,
    "remark": "EA sync"
  }],
  "statsMetrics": [{"name": "Win Rate", "value": "+58.00%"}]
}
```

## POST `/v1/ea/snapshot`

- Content-Type: `application/json`
- Optional header: `X-Bridge-Token: <EA_INGEST_TOKEN>`
- Body: same structure as `/v1/snapshot` response

Response:

```json
{"ok": true, "receivedAt": 1760000000000}
```
