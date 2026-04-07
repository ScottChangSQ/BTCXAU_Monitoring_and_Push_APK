/*
 * 远程会话账号摘要模型，负责承载 active/saved account 的统一字段。
 * 由 GatewayV2SessionClient 解析并提供给会话状态与回执模型复用。
 */
package com.binance.monitor.data.model.v2.session;

public class RemoteAccountProfile {
    private final String profileId;
    private final String login;
    private final String loginMasked;
    private final String server;
    private final String displayName;
    private final boolean active;
    private final String state;

    // 构造远程账号摘要对象。
    public RemoteAccountProfile(String profileId,
                                String login,
                                String loginMasked,
                                String server,
                                String displayName,
                                boolean active,
                                String state) {
        this.profileId = profileId == null ? "" : profileId;
        this.login = login == null ? "" : login;
        this.loginMasked = loginMasked == null ? "" : loginMasked;
        this.server = server == null ? "" : server;
        this.displayName = displayName == null ? "" : displayName;
        this.active = active;
        this.state = state == null ? "" : state;
    }

    // 统一判断远程会话是否处于激活态，兼容 active / activated 两种状态词。
    public static boolean resolveActiveFlag(boolean active, String state) {
        if (active) {
            return true;
        }
        String safeState = state == null ? "" : state.trim();
        return "active".equalsIgnoreCase(safeState) || "activated".equalsIgnoreCase(safeState);
    }

    // 返回账号档案标识。
    public String getProfileId() {
        return profileId;
    }

    // 返回账号原始 login。
    public String getLogin() {
        return login;
    }

    // 返回账号掩码。
    public String getLoginMasked() {
        return loginMasked;
    }

    // 返回交易服务器。
    public String getServer() {
        return server;
    }

    // 返回展示名。
    public String getDisplayName() {
        return displayName;
    }

    // 返回是否激活。
    public boolean isActive() {
        return active;
    }

    // 返回状态字符串。
    public String getState() {
        return state;
    }
}
