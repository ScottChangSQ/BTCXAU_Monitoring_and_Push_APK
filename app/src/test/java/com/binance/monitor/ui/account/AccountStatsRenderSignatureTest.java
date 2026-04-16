package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.ui.account.history.AccountStatsRenderSignature;

import org.junit.Test;

import java.util.Collections;

public class AccountStatsRenderSignatureTest {

    @Test
    public void accountStatsSignatureShouldIgnoreRuntimePositionsAndPendingOrders() {
        AccountStatsRenderSignature signature = AccountStatsRenderSignature.from(
                "rev-1",
                Collections.singletonList(new TradeRecordItem(1L, "XAUUSD", "XAUUSD", "Sell", 1d, 1d, 1d, 0d, "")),
                Collections.singletonList(new CurvePoint(2L, 10d, 9d, 0.2d)),
                Collections.singletonList(new AccountMetric("profit", "10")),
                "全部产品",
                "全部方向",
                "平仓时间",
                true
        );

        assertTrue(signature.asText().contains("rev-1"));
        assertFalse(signature.asText().contains("position"));
        assertFalse(signature.asText().contains("pending"));
    }
}
