package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.json.JSONObject;
import org.junit.Test;

public class TradeRiskPreviewResolverTest {

    @Test
    public void shouldBuildRiskPreviewFromExecutableCheckPayload() throws Exception {
        JSONObject check = new JSONObject();
        check.put("margin", 120.5d);
        check.put("freeMargin", 880.0d);
        check.put("freeMarginAfter", 759.5d);
        check.put("stopLossAmount", 45.0d);
        check.put("takeProfitAmount", 80.0d);

        TradeCheckResult result = new TradeCheckResult(
                "req-risk",
                "OPEN_MARKET",
                "hedging",
                "EXECUTABLE",
                null,
                check,
                1713500000L
        );
        TradeCommand command = TradeCommandFactory.openMarket(
                "acc-1",
                "BTCUSD",
                "buy",
                0.05d,
                65000d,
                64800d,
                65400d
        );
        command = TradeCommandFactory.withTemplate(
                command,
                new com.binance.monitor.data.model.v2.trade.TradeTemplate(
                        "default_market",
                        "默认模板",
                        0.05d,
                        0d,
                        0d,
                        "both"
                )
        );

        TradeRiskPreview preview = TradeRiskPreviewResolver.resolve(command, result);

        assertEquals("买入 BTCUSD 0.05 手 @ $65,000.00", preview.getActionSummary());
        assertEquals("默认模板", preview.getTemplateName());
        assertEquals("$120.50", preview.getMarginText());
        assertEquals("$759.50", preview.getFreeMarginAfterText());
        assertEquals("$45.00", preview.getStopLossAmountText());
        assertEquals("$80.00", preview.getTakeProfitAmountText());
        assertTrue(preview.hasAnyRiskMetric());
    }
}
