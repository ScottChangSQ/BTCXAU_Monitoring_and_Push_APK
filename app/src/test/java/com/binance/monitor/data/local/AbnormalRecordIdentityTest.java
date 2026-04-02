/*
 * 异常记录 ID 测试，确保本地补判和服务端回补使用同一套稳定标识。
 */
package com.binance.monitor.data.local;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.constants.AppConstants;

import org.junit.Test;

public class AbnormalRecordIdentityTest {

    @Test
    public void buildStableRecordIdShouldTreatLocalAndGatewayXauSymbolAsSameRecord() {
        String localId = AbnormalRecordManager.buildStableRecordId(AppConstants.SYMBOL_XAU, 1234L, "价格变化");
        String gatewayId = AbnormalRecordManager.buildStableRecordId("XAUUSD", 1234L, "价格变化");

        assertEquals(gatewayId, localId);
    }

    @Test
    public void buildStableRecordIdShouldStayStableForSameInputs() {
        String first = AbnormalRecordManager.buildStableRecordId(AppConstants.SYMBOL_BTC, 5678L, "成交量");
        String second = AbnormalRecordManager.buildStableRecordId(AppConstants.SYMBOL_BTC, 5678L, "成交量");

        assertEquals(first, second);
    }
}
