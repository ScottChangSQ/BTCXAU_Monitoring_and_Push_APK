#property strict

input string GatewayUrl = "http://127.0.0.1:8787/v1/ea/snapshot";
input string BridgeToken = "";
input int PushIntervalSeconds = 5;
input int RequestTimeoutMs = 3000;

string JsonEscape(string value)
{
   string escaped = value;
   StringReplace(escaped, "\\", "\\\\");
   StringReplace(escaped, "\"", "\\\"");
   StringReplace(escaped, "\r", " ");
   StringReplace(escaped, "\n", " ");
   return escaped;
}

string BuildMetric(string name, string value)
{
   return StringFormat("{\"name\":\"%s\",\"value\":\"%s\"}", JsonEscape(name), JsonEscape(value));
}

double GetContractSize(string symbol)
{
   double contractSize = 1.0;
   if(!SymbolInfoDouble(symbol, SYMBOL_TRADE_CONTRACT_SIZE, contractSize) || contractSize <= 0.0)
      contractSize = 1.0;
   return contractSize;
}

string BuildOverviewMetrics()
{
   double equity = AccountInfoDouble(ACCOUNT_EQUITY);
   double margin = AccountInfoDouble(ACCOUNT_MARGIN);
   double freeMargin = AccountInfoDouble(ACCOUNT_MARGIN_FREE);
   double balance = AccountInfoDouble(ACCOUNT_BALANCE);

   string json = "[";
   json += BuildMetric("Total Asset", DoubleToString(equity, 2));
   json += "," + BuildMetric("Margin", DoubleToString(margin, 2));
   json += "," + BuildMetric("Free Fund", DoubleToString(freeMargin, 2));
   json += "," + BuildMetric("Current Equity", DoubleToString(equity, 2));
   json += "," + BuildMetric("Balance", DoubleToString(balance, 2));
   json += "]";
   return json;
}

string BuildCurvePoints()
{
   long nowMs = (long)TimeCurrent() * 1000;
   double equity = AccountInfoDouble(ACCOUNT_EQUITY);
   double balance = AccountInfoDouble(ACCOUNT_BALANCE);
   return StringFormat("[{\"timestamp\":%I64d,\"equity\":%.2f,\"balance\":%.2f}]", nowMs, equity, balance);
}

string BuildCurveIndicators()
{
   string json = "[";
   json += BuildMetric("1D Return", "0.00%");
   json += "," + BuildMetric("7D Return", "0.00%");
   json += "," + BuildMetric("30D Return", "0.00%");
   json += "," + BuildMetric("Max Drawdown", "0.00%");
   json += "," + BuildMetric("Volatility", "0.00%");
   json += "]";
   return json;
}

string BuildPositions()
{
   int total = PositionsTotal();
   if(total <= 0)
      return "[]";

   double totalMarketValue = 0.0;
   int i;
   for(i = 0; i < total; i++)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket == 0 || !PositionSelectByTicket(ticket))
         continue;

      string symbol = PositionGetString(POSITION_SYMBOL);
      double volume = PositionGetDouble(POSITION_VOLUME);
      double priceCurrent = PositionGetDouble(POSITION_PRICE_CURRENT);
      totalMarketValue += MathAbs(volume * priceCurrent * GetContractSize(symbol));
   }

   string json = "[";
   bool first = true;
   for(i = 0; i < total; i++)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket == 0 || !PositionSelectByTicket(ticket))
         continue;

      string symbol = PositionGetString(POSITION_SYMBOL);
      double volume = PositionGetDouble(POSITION_VOLUME);
      double priceOpen = PositionGetDouble(POSITION_PRICE_OPEN);
      double priceCurrent = PositionGetDouble(POSITION_PRICE_CURRENT);
      double profit = PositionGetDouble(POSITION_PROFIT);
      double marketValue = MathAbs(volume * priceCurrent * GetContractSize(symbol));
      double ratio = 0.0;
      if(totalMarketValue > 0.0)
         ratio = marketValue / totalMarketValue;

      string item = StringFormat(
         "{\"productName\":\"%s\",\"code\":\"%s\",\"quantity\":%.4f,\"sellableQuantity\":%.4f,\"costPrice\":%.5f,\"latestPrice\":%.5f,\"marketValue\":%.2f,\"positionRatio\":%.6f,\"dayPnL\":%.2f,\"totalPnL\":%.2f,\"returnRate\":%.6f}",
         JsonEscape(symbol),
         JsonEscape(symbol),
         volume,
         volume,
         priceOpen,
         priceCurrent,
         marketValue,
         ratio,
         profit * 0.2,
         profit,
         (priceOpen == 0.0 ? 0.0 : (priceCurrent - priceOpen) / priceOpen)
      );

      if(!first)
         json += ",";
      json += item;
      first = false;
   }

   json += "]";
   return json;
}

string BuildTrades()
{
   datetime fromTime = TimeCurrent() - 30 * 24 * 60 * 60;
   datetime toTime = TimeCurrent();
   if(!HistorySelect(fromTime, toTime))
      return "[]";

   int total = HistoryDealsTotal();
   if(total <= 0)
      return "[]";

   int maxItems = 80;
   int start = total - 1;
   int end = MathMax(0, total - maxItems);

   string json = "[";
   bool first = true;
   int i;
   for(i = start; i >= end; i--)
   {
      ulong dealTicket = HistoryDealGetTicket(i);
      if(dealTicket == 0)
         continue;

      string symbol = HistoryDealGetString(dealTicket, DEAL_SYMBOL);
      long dealType = HistoryDealGetInteger(dealTicket, DEAL_TYPE);
      string side = (dealType == DEAL_TYPE_BUY ? "Buy" : "Sell");
      double volume = HistoryDealGetDouble(dealTicket, DEAL_VOLUME);
      double price = HistoryDealGetDouble(dealTicket, DEAL_PRICE);
      double fee = MathAbs(HistoryDealGetDouble(dealTicket, DEAL_COMMISSION) + HistoryDealGetDouble(dealTicket, DEAL_SWAP));
      long timeMs = (long)HistoryDealGetInteger(dealTicket, DEAL_TIME) * 1000;
      double amount = MathAbs(volume * price * GetContractSize(symbol));
      string comment = HistoryDealGetString(dealTicket, DEAL_COMMENT);

      string item = StringFormat(
         "{\"timestamp\":%I64d,\"productName\":\"%s\",\"code\":\"%s\",\"side\":\"%s\",\"price\":%.5f,\"quantity\":%.4f,\"amount\":%.2f,\"fee\":%.2f,\"remark\":\"%s\"}",
         timeMs,
         JsonEscape(symbol),
         JsonEscape(symbol),
         side,
         price,
         volume,
         amount,
         fee,
         JsonEscape(comment)
      );

      if(!first)
         json += ",";
      json += item;
      first = false;
   }

   json += "]";
   return json;
}

string BuildStats()
{
   string json = "[";
   json += BuildMetric("Data Source", "MT5 EA Push");
   json += "," + BuildMetric("Pushed At", TimeToString(TimeCurrent(), TIME_DATE | TIME_SECONDS));
   json += "]";
   return json;
}

string BuildSnapshotJson()
{
   long nowMs = (long)TimeCurrent() * 1000;
   long login = AccountInfoInteger(ACCOUNT_LOGIN);
   string server = AccountInfoString(ACCOUNT_SERVER);

   string accountMeta = StringFormat(
      "{\"login\":\"%I64d\",\"server\":\"%s\",\"source\":\"MT5 EA Push\",\"updatedAt\":%I64d}",
      login,
      JsonEscape(server),
      nowMs
   );

   string json = "{";
   json += "\"accountMeta\":" + accountMeta;
   json += ",\"overviewMetrics\":" + BuildOverviewMetrics();
   json += ",\"curvePoints\":" + BuildCurvePoints();
   json += ",\"curveIndicators\":" + BuildCurveIndicators();
   json += ",\"positions\":" + BuildPositions();
   json += ",\"trades\":" + BuildTrades();
   json += ",\"statsMetrics\":" + BuildStats();
   json += "}";

   return json;
}

bool PushSnapshot()
{
   string body = BuildSnapshotJson();
   char payload[];
   StringToCharArray(body, payload, 0, -1, CP_UTF8);

   string headers = "Content-Type: application/json\r\n";
   if(StringLen(BridgeToken) > 0)
      headers += "X-Bridge-Token: " + BridgeToken + "\r\n";

   char response[];
   string responseHeaders = "";
   int code = WebRequest("POST", GatewayUrl, headers, RequestTimeoutMs, payload, response, responseHeaders);
   if(code == -1)
   {
      Print("MT5BridgePushEA: WebRequest failed, error=", GetLastError());
      return false;
   }

   string responseText = CharArrayToString(response, 0, -1, CP_UTF8);
   Print("MT5BridgePushEA: push status=", code, " response=", responseText);
   return code >= 200 && code < 300;
}

int OnInit()
{
   if(PushIntervalSeconds < 1)
      PushIntervalSeconds = 5;

   EventSetTimer(PushIntervalSeconds);
   Print("MT5BridgePushEA started. Gateway=", GatewayUrl, " interval=", PushIntervalSeconds, "s");
   PushSnapshot();
   return(INIT_SUCCEEDED);
}

void OnDeinit(const int reason)
{
   EventKillTimer();
   Print("MT5BridgePushEA stopped.");
}

void OnTimer()
{
   PushSnapshot();
}
