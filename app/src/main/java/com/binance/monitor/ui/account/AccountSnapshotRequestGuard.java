/*
 * 账户快照请求守卫，负责拦截过期快照回包，避免退出登录或切换会话后旧数据重新写回页面。
 * 供 AccountStatsBridgeActivity 在发起异步快照请求时复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

public final class AccountSnapshotRequestGuard {
    private long sessionVersion = 1L;
    private long requestVersion = 0L;

    // 为当前会话发起一枚新的请求令牌。
    public synchronized RequestToken openRequest() {
        requestVersion++;
        return new RequestToken(sessionVersion, requestVersion);
    }

    // 当前会话失效时推进版本，确保旧回包立即作废。
    public synchronized void invalidateSession() {
        sessionVersion++;
        requestVersion = 0L;
    }

    // 仅允许当前会话、当前请求号的回包继续落地。
    public synchronized boolean shouldApply(@Nullable RequestToken token) {
        return token != null
                && token.sessionVersion == sessionVersion
                && token.requestVersion == requestVersion;
    }

    public static final class RequestToken {
        private final long sessionVersion;
        private final long requestVersion;

        RequestToken(long sessionVersion, long requestVersion) {
            this.sessionVersion = sessionVersion;
            this.requestVersion = requestVersion;
        }
    }
}
