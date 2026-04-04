/*
 * 验证旧 MT5 快照客户端也会统一时间口径，避免回退链路继续产出错时长数据。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.json.JSONArray;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

public class Mt5GatewayClientTest {

    @Test
    @SuppressWarnings("unchecked")
    public void parseCurvePointsShouldNormalizeSecondTimestamp() throws Exception {
        Mt5GatewayClient client = new Mt5GatewayClient();
        Method method = Mt5GatewayClient.class.getDeclaredMethod("parseCurvePoints", JSONArray.class);
        method.setAccessible(true);

        JSONArray array = new JSONArray("[{\"timestamp\":1704067200,\"equity\":100.0,\"balance\":90.0}]");
        List<CurvePoint> points = (List<CurvePoint>) method.invoke(client, array);

        assertEquals(1, points.size());
        assertEquals(1_704_067_200_000L, points.get(0).getTimestamp());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void parseTradesShouldSupportSnakeCaseLifecycleTimeFields() throws Exception {
        Mt5GatewayClient client = new Mt5GatewayClient();
        Method method = Mt5GatewayClient.class.getDeclaredMethod("parseTrades", JSONArray.class);
        method.setAccessible(true);

        JSONArray array = new JSONArray("[{" +
                "\"productName\":\"BTCUSD\"," +
                "\"code\":\"BTCUSD\"," +
                "\"side\":\"Buy\"," +
                "\"price\":100.0," +
                "\"open_price\":95.0," +
                "\"close_price\":100.0," +
                "\"timestamp\":1704069000," +
                "\"open_time\":1704067200," +
                "\"close_time\":1704069000," +
                "\"quantity\":1.0," +
                "\"profit\":5.0" +
                "}]");

        List<TradeRecordItem> trades = (List<TradeRecordItem>) method.invoke(client, array);

        assertEquals(1, trades.size());
        assertEquals(1_704_067_200_000L, trades.get(0).getOpenTime());
        assertEquals(1_704_069_000_000L, trades.get(0).getCloseTime());
        assertEquals(95.0d, trades.get(0).getOpenPrice(), 1e-9);
        assertEquals(100.0d, trades.get(0).getClosePrice(), 1e-9);
    }
}
