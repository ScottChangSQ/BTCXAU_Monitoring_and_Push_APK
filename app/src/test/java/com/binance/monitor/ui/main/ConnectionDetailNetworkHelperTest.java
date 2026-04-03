/*
 * 主界面连接详情网络辅助测试，确保服务器地址、地理位置和延迟文案稳定可读。
 */
package com.binance.monitor.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConnectionDetailNetworkHelperTest {

    @Test
    public void isPrivateHost_shouldRecognizePrivateAndLoopbackHosts() {
        assertTrue(ConnectionDetailNetworkHelper.isPrivateHost("127.0.0.1"));
        assertTrue(ConnectionDetailNetworkHelper.isPrivateHost("10.0.2.2"));
        assertTrue(ConnectionDetailNetworkHelper.isPrivateHost("192.168.1.3"));
        assertTrue(ConnectionDetailNetworkHelper.isPrivateHost("localhost"));
    }

    @Test
    public void formatLocation_shouldReturnLocalLabel_forPrivateHost() {
        assertEquals(
                "内网/本地服务器",
                ConnectionDetailNetworkHelper.formatLocation("10.0.2.2", "10.0.2.2", "", "", "")
        );
    }

    @Test
    public void formatLocation_shouldComposeCityRegionCountry_forPublicHost() {
        assertEquals(
                "Singapore, Singapore, Singapore",
                ConnectionDetailNetworkHelper.formatLocation(
                        "43.155.214.62",
                        "43.155.214.62",
                        "Singapore",
                        "Singapore",
                        "Singapore"
                )
        );
    }

    @Test
    public void formatLatency_shouldReturnPlaceholder_whenUnknown() {
        assertEquals("--", ConnectionDetailNetworkHelper.formatLatency(-1L));
        assertEquals("128ms", ConnectionDetailNetworkHelper.formatLatency(128L));
    }
}
