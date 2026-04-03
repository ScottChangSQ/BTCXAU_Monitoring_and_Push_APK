/*
 * 验证 K 线流消息解析器在 combined/direct 两种消息结构下都能输出标准 KlineData。
 */
package com.binance.monitor.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class KlineStreamMessageParserTest {

    @Test
    public void parseShouldSupportCombinedStreamPayload() {
        String text = "{"
                + "\"stream\":\"btcusdt@kline_1m\","
                + "\"data\":{"
                + "\"e\":\"kline\","
                + "\"s\":\"BTCUSDT\","
                + "\"k\":{"
                + "\"t\":1712277180000,"
                + "\"T\":1712277239999,"
                + "\"o\":\"66690.40\","
                + "\"h\":\"66703.10\","
                + "\"l\":\"66478.50\","
                + "\"c\":\"66599.90\","
                + "\"v\":\"5620\","
                + "\"q\":\"374111934\","
                + "\"x\":false"
                + "}"
                + "}"
                + "}";

        KlineStreamMessageParser.ParsedKline parsed = KlineStreamMessageParser.parse(text);
        assertNotNull(parsed);
        assertEquals("BTCUSDT", parsed.symbol);
        assertNotNull(parsed.data);
        assertEquals(66690.40d, parsed.data.getOpenPrice(), 0.000001d);
        assertEquals(66599.90d, parsed.data.getClosePrice(), 0.000001d);
        assertEquals(5620d, parsed.data.getVolume(), 0.000001d);
    }

    @Test
    public void parseShouldSupportDirectStreamPayload() {
        String text = "{"
                + "\"e\":\"kline\","
                + "\"s\":\"XAUUSDT\","
                + "\"k\":{"
                + "\"t\":1712277180000,"
                + "\"T\":1712277239999,"
                + "\"o\":\"2301.10\","
                + "\"h\":\"2302.10\","
                + "\"l\":\"2300.50\","
                + "\"c\":\"2301.70\","
                + "\"v\":\"100\","
                + "\"q\":\"230170\","
                + "\"x\":true"
                + "}"
                + "}";

        KlineStreamMessageParser.ParsedKline parsed = KlineStreamMessageParser.parse(text);
        assertNotNull(parsed);
        assertEquals("XAUUSDT", parsed.symbol);
        assertNotNull(parsed.data);
        assertEquals(2301.10d, parsed.data.getOpenPrice(), 0.000001d);
        assertEquals(2301.70d, parsed.data.getClosePrice(), 0.000001d);
        assertEquals(true, parsed.data.isClosed());
    }

    @Test
    public void parseShouldReturnNullForAggTradePayload() {
        String text = "{"
                + "\"stream\":\"btcusdt@aggTrade\","
                + "\"data\":{"
                + "\"e\":\"aggTrade\","
                + "\"s\":\"BTCUSDT\","
                + "\"p\":\"66600.10\","
                + "\"q\":\"0.01\""
                + "}"
                + "}";

        assertNull(KlineStreamMessageParser.parse(text));
    }
}
