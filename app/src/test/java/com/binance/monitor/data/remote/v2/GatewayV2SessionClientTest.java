package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.session.SessionPublicKeyPayload;
import com.binance.monitor.data.model.v2.session.SessionReceipt;
import com.binance.monitor.data.model.v2.session.SessionStatusPayload;

import org.junit.Test;

public class GatewayV2SessionClientTest {

    @Test
    public void parseSessionPublicKeyShouldKeepKeyAndAccounts() throws Exception {
        String body = "{"
                + "\"ok\":true,"
                + "\"keyId\":\"key-1\","
                + "\"algorithm\":\"rsa-oaep+aes-gcm\","
                + "\"publicKeyPem\":\"pem\","
                + "\"expiresAt\":1775400000000,"
                + "\"activeAccount\":{\"profileId\":\"acc-active\",\"loginMasked\":\"****1234\",\"server\":\"IC\",\"displayName\":\"IC 1234\"},"
                + "\"savedAccounts\":[{\"profileId\":\"acc-1\",\"loginMasked\":\"****5678\",\"server\":\"IC\",\"displayName\":\"IC 5678\"}]"
                + "}";

        SessionPublicKeyPayload payload = GatewayV2SessionClient.parseSessionPublicKey(body);

        assertTrue(payload.isOk());
        assertEquals("key-1", payload.getKeyId());
        assertEquals("rsa-oaep+aes-gcm", payload.getAlgorithm());
        assertEquals("pem", payload.getPublicKeyPem());
        assertEquals(1775400000000L, payload.getExpiresAt());
        assertEquals("acc-active", payload.getActiveAccount().getProfileId());
        assertEquals(1, payload.getSavedAccounts().size());
        assertEquals("acc-1", payload.getSavedAccounts().get(0).getProfileId());
    }

    @Test
    public void parseSessionStatusShouldKeepStateAndSavedAccounts() throws Exception {
        String body = "{"
                + "\"ok\":true,"
                + "\"state\":\"activated\","
                + "\"activeAccount\":{\"profileId\":\"acc-2\",\"loginMasked\":\"****1111\",\"server\":\"IC\"},"
                + "\"savedAccounts\":[{\"profileId\":\"acc-2\"},{\"profileId\":\"acc-3\"}],"
                + "\"savedAccountCount\":2"
                + "}";

        SessionStatusPayload payload = GatewayV2SessionClient.parseSessionStatus(body);

        assertTrue(payload.isOk());
        assertEquals("activated", payload.getState());
        assertEquals("acc-2", payload.getActiveAccount().getProfileId());
        assertEquals(2, payload.getSavedAccounts().size());
        assertEquals(2, payload.getSavedAccountCount());
    }

    @Test
    public void parseSessionReceiptShouldTreatActivatedStateAsActiveWhenBooleanMissing() throws Exception {
        String body = "{"
                + "\"ok\":true,"
                + "\"state\":\"activated\","
                + "\"requestId\":\"req-login\","
                + "\"account\":{\"profileId\":\"acc-1\",\"state\":\"activated\",\"loginMasked\":\"****1234\"},"
                + "\"message\":\"登录成功\""
                + "}";

        SessionReceipt receipt = GatewayV2SessionClient.parseSessionReceipt(body);

        assertTrue(receipt.isOk());
        assertEquals("activated", receipt.getState());
        assertTrue(receipt.getAccount().isActive());
    }

    @Test
    public void parseSessionReceiptShouldHandleSwitchAndLogoutShape() throws Exception {
        String switchBody = "{"
                + "\"ok\":true,"
                + "\"state\":\"activated\","
                + "\"requestId\":\"req-switch\","
                + "\"account\":{\"profileId\":\"acc-3\",\"loginMasked\":\"****3333\"},"
                + "\"message\":\"切换成功\""
                + "}";
        String logoutBody = "{"
                + "\"ok\":true,"
                + "\"state\":\"logged_out\","
                + "\"requestId\":\"req-logout\","
                + "\"activeAccount\":null,"
                + "\"message\":\"已退出\""
                + "}";

        SessionReceipt switchReceipt = GatewayV2SessionClient.parseSessionReceipt(switchBody);
        SessionReceipt logoutReceipt = GatewayV2SessionClient.parseSessionReceipt(logoutBody);

        assertTrue(switchReceipt.isOk());
        assertEquals("activated", switchReceipt.getState());
        assertEquals("req-switch", switchReceipt.getRequestId());
        assertEquals("acc-3", switchReceipt.getAccount().getProfileId());
        assertEquals("切换成功", switchReceipt.getMessage());

        assertTrue(logoutReceipt.isOk());
        assertEquals("logged_out", logoutReceipt.getState());
        assertEquals("req-logout", logoutReceipt.getRequestId());
        assertNull(logoutReceipt.getAccount());
        assertFalse(logoutReceipt.isFailed());
    }
}
