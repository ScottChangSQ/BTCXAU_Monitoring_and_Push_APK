/*
 * 远程会话协调器测试，负责锁定切换后先清旧缓存、再等待新快照收口的行为。
 * 这样可以避免账号已切换但页面仍显示旧持仓、旧挂单和旧展开态。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.session.SessionPublicKeyPayload;
import com.binance.monitor.data.model.v2.session.SessionReceipt;
import com.binance.monitor.data.model.v2.session.SessionStatusPayload;
import com.binance.monitor.security.SessionCredentialEncryptor;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AccountRemoteSessionCoordinatorTest {

    @Test
    public void switchSuccessShouldClearOldAccountCachesBeforeMarkingActive() throws Exception {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                new RemoteAccountProfile("acc-2", "87654321", "****4321", "IC", "IC 4321", true, "active"),
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusPayload = new SessionStatusPayload(
                true,
                "activated",
                new RemoteAccountProfile("acc-2", "87654321", "****4321", "IC", "IC 4321", true, "active"),
                Collections.singletonList(new RemoteAccountProfile("acc-2", "87654321", "****4321", "IC", "IC 4321", true, "active")),
                1,
                "{}"
        );

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        AccountRemoteSessionCoordinator.SessionActionResult result = coordinator.switchSavedAccount("acc-2");

        assertNotNull(result);
        assertTrue(resetter.accountSnapshotCleared);
        assertTrue(resetter.tradeHistoryCleared);
        assertTrue(resetter.chartDraftCleared);
        assertTrue(resetter.pendingExpandedStateCleared);
        assertTrue(resetter.positionExpandedStateCleared);
        assertTrue(resetter.tradeExpandedStateCleared);
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.SYNCING, machine.snapshot().getState());
        assertTrue(machine.snapshot().isAwaitingSync());
        assertTrue(coordinator.isAwaitingSync());
        assertEquals("acc-2", result.getActiveAccount().getProfileId());
        assertEquals(1, store.savedAccounts.size());

        boolean activated = coordinator.onSnapshotApplied("87654321", "IC");

        assertTrue(activated);
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.ACTIVE, machine.snapshot().getState());
        assertFalse(coordinator.isAwaitingSync());
    }

    @Test
    public void switchAcceptedShouldFailWhenStatusDoesNotConfirmTargetAccount() {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                new RemoteAccountProfile("acc-new", "87654321", "****4321", "IC-NEW", "IC NEW", true, "activated"),
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusPayload = new SessionStatusPayload(
                true,
                "activated",
                new RemoteAccountProfile("acc-old", "12345678", "****5678", "IC-OLD", "IC OLD", true, "active"),
                Collections.singletonList(new RemoteAccountProfile("acc-old", "12345678", "****5678", "IC-OLD", "IC OLD", true, "active")),
                1,
                "{}"
        );

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        boolean thrown = false;
        try {
            coordinator.switchSavedAccount("acc-new");
        } catch (IllegalStateException expected) {
            thrown = true;
            assertEquals("会话账号摘要不一致", expected.getMessage());
        } catch (Exception other) {
            throw new AssertionError("unexpected exception", other);
        }

        assertTrue(thrown);
        assertFalse(coordinator.isAwaitingSync());
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, machine.snapshot().getState());
    }

    @Test
    public void logoutShouldClearLocalSessionAndReturnIdle() throws Exception {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        store.savedAccounts = Collections.singletonList(
                new RemoteAccountProfile("acc-9", "", "****9999", "IC", "IC 9999", false, "")
        );
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.logoutReceipt = new SessionReceipt(true, "logged_out", "req-logout", null, "已退出", "", false, "{}");
        gateway.statusPayload = new SessionStatusPayload(
                true,
                "logged_out",
                null,
                Collections.singletonList(new RemoteAccountProfile("acc-9", "", "****9999", "IC", "IC 9999", false, "")),
                1,
                "{}"
        );

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-logout-local"
        );

        coordinator.logoutCurrent();

        assertTrue(store.cleared);
        assertEquals(1, store.savedAccounts.size());
        assertTrue(resetter.accountSnapshotCleared);
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.IDLE, machine.snapshot().getState());
        assertFalse(coordinator.isAwaitingSync());
    }

    @Test
    public void switchAcceptedShouldFailWhenStatusFetchFails() {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        store.savedAccounts = Collections.singletonList(
                new RemoteAccountProfile("acc-old", "12345678", "****5678", "IC", "IC 5678", false, "")
        );
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                new RemoteAccountProfile("acc-7", "76543210", "****3210", "IC", "IC 3210", true, "activated"),
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusError = new RuntimeException("status fetch failed");

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        boolean thrown = false;
        try {
            coordinator.switchSavedAccount("acc-7");
        } catch (IllegalStateException expected) {
            thrown = true;
            assertEquals("会话状态确认失败", expected.getMessage());
        } catch (Exception other) {
            throw new AssertionError("unexpected exception", other);
        }

        assertTrue(thrown);
        assertFalse(coordinator.isAwaitingSync());
        assertFalse(resetter.accountSnapshotCleared);
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, machine.snapshot().getState());
    }

    @Test
    public void switchAcceptedShouldFailWhenStatusPayloadIsMissing() {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                new RemoteAccountProfile("acc-9", "99887766", "****7766", "IC", "IC 7766", true, "activated"),
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusPayload = null;

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        boolean thrown = false;
        try {
            coordinator.switchSavedAccount("acc-9");
        } catch (IllegalStateException expected) {
            thrown = true;
            assertEquals("会话状态缺失", expected.getMessage());
        } catch (Exception other) {
            throw new AssertionError("unexpected exception", other);
        }

        assertTrue(thrown);
        assertFalse(coordinator.isAwaitingSync());
        assertFalse(resetter.accountSnapshotCleared);
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, machine.snapshot().getState());
    }

    @Test
    public void switchAcceptedShouldFailWhenStatusResponseIsNotOk() {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                new RemoteAccountProfile("acc-10", "10000001", "****0001", "IC", "IC 0001", true, "activated"),
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusPayload = new SessionStatusPayload(
                false,
                "activated",
                new RemoteAccountProfile("acc-10", "10000001", "****0001", "IC", "IC 0001", true, "activated"),
                Collections.singletonList(new RemoteAccountProfile("acc-10", "10000001", "****0001", "IC", "IC 0001", true, "activated")),
                1,
                "{}"
        );

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        boolean thrown = false;
        try {
            coordinator.switchSavedAccount("acc-10");
        } catch (IllegalStateException expected) {
            thrown = true;
            assertEquals("会话状态缺失", expected.getMessage());
        } catch (Exception other) {
            throw new AssertionError("unexpected exception", other);
        }

        assertTrue(thrown);
        assertFalse(coordinator.isAwaitingSync());
        assertFalse(resetter.accountSnapshotCleared);
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, machine.snapshot().getState());
    }

    @Test
    public void switchAcceptedShouldFailWhenReceiptAndStatusBothMissAccountSummary() {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                null,
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusPayload = new SessionStatusPayload(
                true,
                "activated",
                null,
                Collections.emptyList(),
                0,
                "{}"
        );

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        boolean thrown = false;
        try {
            coordinator.switchSavedAccount("acc-8");
        } catch (IllegalStateException expected) {
            thrown = true;
            assertEquals("会话账号摘要缺失", expected.getMessage());
        } catch (Exception other) {
            throw new AssertionError("unexpected exception", other);
        }

        assertTrue(thrown);
        assertFalse(coordinator.isAwaitingSync());
        assertFalse(resetter.accountSnapshotCleared);
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, machine.snapshot().getState());
    }

    @Test
    public void switchShouldFailWhenStatusMissingCanonicalServer() {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                new RemoteAccountProfile("acc-12", "12000000", "****0000", "IC", "IC 0000", true, "activated"),
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusPayload = new SessionStatusPayload(
                true,
                "activated",
                new RemoteAccountProfile("acc-12", "12000000", "****0000", "", "IC 0000", true, "activated"),
                Collections.singletonList(new RemoteAccountProfile("acc-12", "12000000", "****0000", "", "IC 0000", true, "activated")),
                1,
                "{}"
        );

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        boolean thrown = false;
        try {
            coordinator.switchSavedAccount("acc-12");
        } catch (IllegalStateException expected) {
            thrown = true;
            assertEquals("会话账号摘要缺失", expected.getMessage());
        } catch (Exception other) {
            throw new AssertionError("unexpected exception", other);
        }

        assertTrue(thrown);
        assertFalse(coordinator.isAwaitingSync());
        assertFalse(resetter.accountSnapshotCleared);
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, machine.snapshot().getState());
    }

    @Test
    public void switchShouldFailWhenStatusMissingCanonicalIdentity() {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        store.savedAccounts = Collections.singletonList(
                new RemoteAccountProfile("acc-11", "11223344", "****3344", "IC-HYDRATE", "IC 3344", false, "")
        );
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                new RemoteAccountProfile("acc-11", "", "****3344", "", "IC 3344", true, "activated"),
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusPayload = new SessionStatusPayload(
                true,
                "activated",
                new RemoteAccountProfile("acc-11", "", "****3344", "", "IC 3344", true, "activated"),
                Collections.singletonList(new RemoteAccountProfile("acc-11", "", "****3344", "", "IC 3344", true, "activated")),
                1,
                "{}"
        );

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        boolean thrown = false;
        try {
            coordinator.switchSavedAccount("acc-11");
        } catch (IllegalStateException expected) {
            thrown = true;
            assertEquals("会话账号摘要缺失", expected.getMessage());
        } catch (Exception other) {
            throw new AssertionError("unexpected exception", other);
        }

        assertTrue(thrown);
        assertFalse(coordinator.isAwaitingSync());
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, machine.snapshot().getState());
    }

    @Test
    public void onSnapshotAppliedShouldNotActivateWhenOnlyServerIdentityExists() throws Exception {
        FakeCacheResetter resetter = new FakeCacheResetter();
        FakeSessionStore store = new FakeSessionStore();
        store.savedAccounts = Collections.singletonList(
                new RemoteAccountProfile("acc-13", "", "****0013", "IC-SHARED", "IC 0013", false, "")
        );
        AccountSessionStateMachine machine = new AccountSessionStateMachine();
        FakeGateway gateway = new FakeGateway();
        gateway.switchReceipt = new SessionReceipt(
                true,
                "activated",
                "req-switch",
                new RemoteAccountProfile("acc-13", "", "****0013", "", "IC 0013", true, "activated"),
                "切换成功",
                "",
                false,
                "{}"
        );
        gateway.statusPayload = new SessionStatusPayload(
                true,
                "activated",
                new RemoteAccountProfile("acc-13", "", "****0013", "", "IC 0013", true, "activated"),
                Collections.singletonList(new RemoteAccountProfile("acc-13", "", "****0013", "", "IC 0013", true, "activated")),
                1,
                "{}"
        );

        AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(
                machine,
                gateway,
                new FakeEncryptor(),
                resetter,
                store,
                () -> "req-local"
        );

        boolean thrown = false;
        try {
            coordinator.switchSavedAccount("acc-13");
        } catch (IllegalStateException expected) {
            thrown = true;
            assertEquals("会话账号摘要缺失", expected.getMessage());
        } catch (Exception other) {
            throw new AssertionError("unexpected exception", other);
        }

        assertTrue(thrown);
        assertFalse(coordinator.isAwaitingSync());
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, machine.snapshot().getState());
    }

    private static final class FakeGateway implements AccountRemoteSessionCoordinator.SessionGateway {
        private SessionReceipt switchReceipt;
        private SessionReceipt logoutReceipt;
        private SessionStatusPayload statusPayload;
        private RuntimeException statusError;

        @Override
        public SessionPublicKeyPayload fetchPublicKey() {
            return null;
        }

        @Override
        public SessionReceipt login(SessionCredentialEncryptor.LoginEnvelope envelope, boolean saveAccount) {
            return null;
        }

        @Override
        public SessionReceipt switchAccount(String profileId, String requestId) {
            return switchReceipt;
        }

        @Override
        public SessionReceipt logout(String requestId) {
            return logoutReceipt;
        }

        @Override
        public SessionStatusPayload fetchStatus() {
            if (statusError != null) {
                throw statusError;
            }
            return statusPayload;
        }
    }

    private static final class FakeEncryptor implements AccountRemoteSessionCoordinator.CredentialEnvelopeFactory {
        @Override
        public SessionCredentialEncryptor.LoginEnvelope encrypt(String publicKeyPem,
                                                                String keyId,
                                                                String login,
                                                                String password,
                                                                String server,
                                                                boolean remember,
                                                                long clientTime) {
            return new SessionCredentialEncryptor.LoginEnvelope("req", keyId, "rsa-oaep+aes-gcm", "key", "payload", "iv");
        }
    }

    private static final class FakeCacheResetter implements AccountRemoteSessionCoordinator.CacheResetter {
        private boolean accountSnapshotCleared;
        private boolean tradeHistoryCleared;
        private boolean chartDraftCleared;
        private boolean pendingExpandedStateCleared;
        private boolean positionExpandedStateCleared;
        private boolean tradeExpandedStateCleared;

        @Override
        public void clearAccountSnapshot() {
            accountSnapshotCleared = true;
        }

        @Override
        public void clearTradeHistory() {
            tradeHistoryCleared = true;
        }

        @Override
        public void clearChartTradeDrafts() {
            chartDraftCleared = true;
        }

        @Override
        public void clearPendingExpandedState() {
            pendingExpandedStateCleared = true;
        }

        @Override
        public void clearPositionExpandedState() {
            positionExpandedStateCleared = true;
        }

        @Override
        public void clearTradeExpandedState() {
            tradeExpandedStateCleared = true;
        }
    }

    private static final class FakeSessionStore implements AccountRemoteSessionCoordinator.SessionSummaryStore {
        private RemoteAccountProfile activeAccount;
        private List<RemoteAccountProfile> savedAccounts = Collections.emptyList();
        private boolean active;
        private boolean cleared;

        @Override
        public void saveSession(RemoteAccountProfile activeAccount,
                                List<RemoteAccountProfile> savedAccounts,
                                boolean active) {
            this.activeAccount = activeAccount;
            this.savedAccounts = savedAccounts == null ? Collections.emptyList() : Arrays.asList(savedAccounts.toArray(new RemoteAccountProfile[0]));
            this.active = active;
            this.cleared = false;
        }

        @Override
        public void clearSession() {
            this.activeAccount = null;
            this.active = false;
            this.cleared = true;
        }

        @Override
        public List<RemoteAccountProfile> getSavedAccountsSnapshot() {
            return savedAccounts == null ? Collections.emptyList() : new java.util.ArrayList<>(savedAccounts);
        }
    }
}
