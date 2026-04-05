package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.AccountMetric;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AccountLeverageResolverTest {

    @Test
    public void displayLeverageShouldNotFallbackToOneWhenMetricMissing() {
        assertEquals("", AccountLeverageResolver.formatDisplayLeverage(Collections.emptyList()));
    }

    @Test
    public void displayLeverageShouldReadRealMetricValue() {
        List<AccountMetric> metrics = Arrays.asList(
                new AccountMetric("总资产", "$100.00"),
                new AccountMetric("杠杆", "400x")
        );

        assertEquals("400x", AccountLeverageResolver.formatDisplayLeverage(metrics));
    }

    @Test
    public void hasDisplayLeverageShouldReportPresenceOfRealMetric() {
        assertEquals(false, AccountLeverageResolver.hasDisplayLeverage(Collections.emptyList()));
        assertEquals(true, AccountLeverageResolver.hasDisplayLeverage(
                Collections.singletonList(new AccountMetric("杠杆", "200x"))
        ));
    }

    @Test
    public void curveLeverageShouldStillFallbackToOneWhenMetricMissing() {
        assertEquals(1d, AccountLeverageResolver.resolveCurveLeverage(Collections.emptyList()), 0.0001d);
    }

    @Test
    public void snapshotLeverageShouldFallbackToAccountMetaWhenAccountMissing() throws Exception {
        JSONObject account = new JSONObject();
        JSONObject accountMeta = new JSONObject().put("leverage", 400);

        assertEquals(400d, AccountLeverageResolver.resolveSnapshotLeverage(account, accountMeta), 0.0001d);
    }
}
