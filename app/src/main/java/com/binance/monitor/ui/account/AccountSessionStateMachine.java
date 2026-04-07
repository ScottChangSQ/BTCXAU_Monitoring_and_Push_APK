/*
 * 远程账户会话状态机，负责区分加密、提交、切换、同步和激活几个关键阶段。
 * 账户页只根据这里的状态渲染，避免把“服务器已受理”误当成“页面已切换完成”。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AccountSessionStateMachine {

    public enum AccountSessionUiState {
        IDLE,
        ENCRYPTING,
        SUBMITTING,
        SWITCHING,
        SYNCING,
        ACTIVE,
        FAILED
    }

    public static final class StateSnapshot {
        private final AccountSessionUiState state;
        private final String message;
        private final String activeProfileId;
        private final boolean awaitingSync;

        // 构造当前状态快照。
        public StateSnapshot(AccountSessionUiState state,
                             String message,
                             String activeProfileId,
                             boolean awaitingSync) {
            this.state = state == null ? AccountSessionUiState.IDLE : state;
            this.message = message == null ? "" : message;
            this.activeProfileId = activeProfileId == null ? "" : activeProfileId;
            this.awaitingSync = awaitingSync;
        }

        // 返回当前 UI 状态。
        public AccountSessionUiState getState() {
            return state;
        }

        // 返回状态说明文案。
        public String getMessage() {
            return message;
        }

        // 返回当前关联的账号档案 ID。
        public String getActiveProfileId() {
            return activeProfileId;
        }

        // 返回是否仍在等待新快照落地。
        public boolean isAwaitingSync() {
            return awaitingSync;
        }
    }

    private StateSnapshot current = new StateSnapshot(AccountSessionUiState.IDLE, "", "", false);

    // 切换到常规中间态。
    public synchronized void moveTo(@NonNull AccountSessionUiState nextState, @Nullable String message) {
        current = new StateSnapshot(nextState, message, current.getActiveProfileId(), false);
    }

    // 标记服务器已接受新账号，但页面仍在等待新快照。
    public synchronized void markSyncing(@Nullable String profileId, @Nullable String message) {
        current = new StateSnapshot(AccountSessionUiState.SYNCING, message, profileId, true);
    }

    // 标记页面已经拿到新账号快照并可视为真正切换完成。
    public synchronized void markActive(@Nullable String profileId, @Nullable String message) {
        current = new StateSnapshot(AccountSessionUiState.ACTIVE, message, profileId, false);
    }

    // 标记会话流失败，并结束等待同步状态。
    public synchronized void markFailed(@Nullable String message) {
        current = new StateSnapshot(AccountSessionUiState.FAILED, message, current.getActiveProfileId(), false);
    }

    // 重置回未登录空态。
    public synchronized void reset() {
        current = new StateSnapshot(AccountSessionUiState.IDLE, "", "", false);
    }

    // 返回当前状态快照。
    public synchronized StateSnapshot snapshot() {
        return current;
    }
}
