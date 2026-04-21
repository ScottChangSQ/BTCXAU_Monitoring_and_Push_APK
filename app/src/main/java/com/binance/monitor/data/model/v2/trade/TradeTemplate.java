/*
 * 交易模板模型，负责保存默认手数、默认止损止盈和可用范围。
 * 与 TradeTemplateRepository、图表交易入口和确认摘要协同工作。
 */
package com.binance.monitor.data.model.v2.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public class TradeTemplate {
    private final String templateId;
    private final String displayName;
    private final double defaultVolume;
    private final double defaultSl;
    private final double defaultTp;
    private final String entryScope;

    // 构造交易模板。
    public TradeTemplate(String templateId,
                         String displayName,
                         double defaultVolume,
                         double defaultSl,
                         double defaultTp,
                         String entryScope) {
        this.templateId = templateId == null ? "" : templateId.trim();
        this.displayName = displayName == null ? "" : displayName.trim();
        this.defaultVolume = Math.max(0d, defaultVolume);
        this.defaultSl = Math.max(0d, defaultSl);
        this.defaultTp = Math.max(0d, defaultTp);
        this.entryScope = entryScope == null ? "both" : entryScope.trim();
    }

    @NonNull
    public String getTemplateId() {
        return templateId;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    public double getDefaultVolume() {
        return defaultVolume;
    }

    public double getDefaultSl() {
        return defaultSl;
    }

    public double getDefaultTp() {
        return defaultTp;
    }

    @NonNull
    public String getEntryScope() {
        return entryScope;
    }

    @NonNull
    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        putQuietly(object, "templateId", templateId);
        putQuietly(object, "displayName", displayName);
        putQuietly(object, "defaultVolume", defaultVolume);
        putQuietly(object, "defaultSl", defaultSl);
        putQuietly(object, "defaultTp", defaultTp);
        putQuietly(object, "entryScope", entryScope);
        return object;
    }

    @NonNull
    public static TradeTemplate fromJson(@Nullable JSONObject object) {
        if (object == null) {
            return new TradeTemplate("", "", 0d, 0d, 0d, "both");
        }
        return new TradeTemplate(
                object.optString("templateId", ""),
                object.optString("displayName", ""),
                object.optDouble("defaultVolume", 0d),
                object.optDouble("defaultSl", 0d),
                object.optDouble("defaultTp", 0d),
                object.optString("entryScope", "both")
        );
    }

    private static void putQuietly(@NonNull JSONObject target, @NonNull String key, @Nullable Object value) {
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
