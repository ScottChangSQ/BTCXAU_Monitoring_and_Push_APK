/*
 * 远程账户摘要去重器，负责按稳定账号身份合并重复的已保存账户项。
 * 该工具供本地会话缓存和账户登录入口共用，避免同一账户被重复保存到手机端。
 */
package com.binance.monitor.data.model.v2.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RemoteAccountProfileDeduplicationHelper {

    private RemoteAccountProfileDeduplicationHelper() {
    }

    // 按稳定身份去重已保存账户，并把重复条目的有效字段合并到同一个结果里。
    @NonNull
    public static List<RemoteAccountProfile> deduplicate(@Nullable List<RemoteAccountProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, RemoteAccountProfile> uniqueProfiles = new LinkedHashMap<>();
        int unstableIndex = 0;
        for (RemoteAccountProfile profile : profiles) {
            if (profile == null) {
                continue;
            }
            String key = buildStableIdentityKey(profile);
            if (key.isEmpty()) {
                key = "unstable:" + unstableIndex++;
            }
            RemoteAccountProfile existing = uniqueProfiles.get(key);
            uniqueProfiles.put(key, existing == null ? profile : mergeProfiles(existing, profile));
        }
        return new ArrayList<>(uniqueProfiles.values());
    }

    // 先认 profileId；缺失时再退回 login + server 这一组稳定身份。
    @NonNull
    private static String buildStableIdentityKey(@NonNull RemoteAccountProfile profile) {
        String profileId = normalize(profile.getProfileId());
        if (!profileId.isEmpty()) {
            return "profileId:" + profileId.toLowerCase(Locale.ROOT);
        }
        String login = normalize(profile.getLogin());
        String server = normalize(profile.getServer());
        if (login.isEmpty() || server.isEmpty()) {
            return "";
        }
        return "loginServer:"
                + login.toLowerCase(Locale.ROOT)
                + "@"
                + server.toLowerCase(Locale.ROOT);
    }

    // 合并重复条目时保留完整字段，并确保任一条目已激活就视为激活。
    @NonNull
    private static RemoteAccountProfile mergeProfiles(@NonNull RemoteAccountProfile existing,
                                                      @NonNull RemoteAccountProfile incoming) {
        boolean active = existing.isActive() || incoming.isActive();
        return new RemoteAccountProfile(
                preferNonEmpty(incoming.getProfileId(), existing.getProfileId()),
                preferNonEmpty(incoming.getLogin(), existing.getLogin()),
                preferNonEmpty(incoming.getLoginMasked(), existing.getLoginMasked()),
                preferNonEmpty(incoming.getServer(), existing.getServer()),
                preferNonEmpty(incoming.getDisplayName(), existing.getDisplayName()),
                active,
                preferNonEmpty(incoming.getState(), existing.getState())
        );
    }

    // 优先使用新的非空字段，没有再保留旧字段。
    @NonNull
    private static String preferNonEmpty(@Nullable String preferred, @Nullable String fallback) {
        String normalizedPreferred = normalize(preferred);
        return normalizedPreferred.isEmpty() ? normalize(fallback) : normalizedPreferred;
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
