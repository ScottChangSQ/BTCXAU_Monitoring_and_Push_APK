/*
 * 账户运行态快照，统一表达当前账户身份、修订号和账户级真值。
 */
package com.binance.monitor.runtime.state.model;

import com.binance.monitor.domain.account.model.AccountSnapshot;

public final class AccountRuntimeSnapshot {
    private final long accountRevision;
    private final boolean connected;
    private final String accountKey;
    private final String account;
    private final String server;
    private final String source;
    private final String gateway;
    private final String historyRevision;
    private final long updatedAt;
    private final AccountSnapshot snapshot;

    public AccountRuntimeSnapshot(long accountRevision,
                                  boolean connected,
                                  String accountKey,
                                  String account,
                                  String server,
                                  String source,
                                  String gateway,
                                  String historyRevision,
                                  long updatedAt,
                                  AccountSnapshot snapshot) {
        this.accountRevision = accountRevision;
        this.connected = connected;
        this.accountKey = accountKey == null ? "" : accountKey;
        this.account = account == null ? "" : account;
        this.server = server == null ? "" : server;
        this.source = source == null ? "" : source;
        this.gateway = gateway == null ? "" : gateway;
        this.historyRevision = historyRevision == null ? "" : historyRevision;
        this.updatedAt = updatedAt;
        this.snapshot = snapshot;
    }

    public long getAccountRevision() {
        return accountRevision;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public String getAccount() {
        return account;
    }

    public String getServer() {
        return server;
    }

    public String getSource() {
        return source;
    }

    public String getGateway() {
        return gateway;
    }

    public String getHistoryRevision() {
        return historyRevision;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public AccountSnapshot getSnapshot() {
        return snapshot;
    }
}
