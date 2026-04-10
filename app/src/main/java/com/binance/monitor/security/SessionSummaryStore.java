/*
 * 远程会话摘要存储契约，供会话协调器和安全存储实现解耦复用。
 */
package com.binance.monitor.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;

import java.util.List;

public interface SessionSummaryStore {

    // 保存当前激活账号和已保存账号列表摘要。
    void saveSession(@Nullable RemoteAccountProfile activeAccount,
                     @NonNull List<RemoteAccountProfile> savedAccounts,
                     boolean active);

    // 清理当前激活会话摘要。
    void clearSession();

    // 返回最近一次保存的账号列表快照。
    @NonNull
    List<RemoteAccountProfile> getSavedAccountsSnapshot();
}
