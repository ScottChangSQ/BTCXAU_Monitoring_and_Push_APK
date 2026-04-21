/*
 * 交易模板仓库，负责统一管理默认参数、快捷模板和模板列表持久化。
 * 与 ConfigManager、图表页快捷交易条和统一交易弹窗协同工作。
 */
package com.binance.monitor.ui.trade;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeTemplate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TradeTemplateRepository {

    interface Store {
        double getTradeDefaultVolume();
        void setTradeDefaultVolume(double volume);
        double getTradeDefaultSl();
        void setTradeDefaultSl(double sl);
        double getTradeDefaultTp();
        void setTradeDefaultTp(double tp);
        String getTradeDefaultTemplateId();
        void setTradeDefaultTemplateId(String templateId);
        String getTradeQuickTemplateId();
        void setTradeQuickTemplateId(String templateId);
        String getTradeTemplatesJson();
        void setTradeTemplatesJson(String templatesJson);
    }

    private static final String DEFAULT_TEMPLATE_ID = "default_market";

    private final Store store;

    // 用正式配置中心创建模板仓库。
    public TradeTemplateRepository(@NonNull Context context) {
        this(new ConfigStore(ConfigManager.getInstance(context.getApplicationContext())));
    }

    // 用可替换存储创建模板仓库，供单测和纯逻辑复用。
    TradeTemplateRepository(@NonNull Store store) {
        this.store = store;
    }

    // 返回全部可用模板；空存储时回退到系统默认模板集。
    @NonNull
    public List<TradeTemplate> getTemplates() {
        List<TradeTemplate> templates = parseTemplates(store.getTradeTemplatesJson());
        return templates.isEmpty() ? buildSystemTemplates() : templates;
    }

    // 返回当前默认模板。
    @NonNull
    public TradeTemplate getDefaultTemplate() {
        return findTemplate(getTemplates(), store.getTradeDefaultTemplateId(), DEFAULT_TEMPLATE_ID);
    }

    // 返回当前快捷交易模板。
    @NonNull
    public TradeTemplate getQuickTradeTemplate() {
        return findTemplate(getTemplates(), store.getTradeQuickTemplateId(), getDefaultTemplate().getTemplateId());
    }

    // 读取当前默认手数；缺失时回退到默认模板。
    public double getDefaultVolume() {
        double stored = store.getTradeDefaultVolume();
        return stored > 0d ? stored : getDefaultTemplate().getDefaultVolume();
    }

    // 读取当前默认止损；缺失时回退到默认模板。
    public double getDefaultSl() {
        double stored = store.getTradeDefaultSl();
        return stored > 0d ? stored : getDefaultTemplate().getDefaultSl();
    }

    // 读取当前默认止盈；缺失时回退到默认模板。
    public double getDefaultTp() {
        double stored = store.getTradeDefaultTp();
        return stored > 0d ? stored : getDefaultTemplate().getDefaultTp();
    }

    // 批量保存模板列表，并同步默认模板和快捷模板真值。
    public void saveTemplates(@Nullable List<TradeTemplate> templates,
                              @Nullable String defaultTemplateId,
                              @Nullable String quickTemplateId) {
        List<TradeTemplate> normalized = normalizeTemplates(templates);
        store.setTradeTemplatesJson(serializeTemplates(normalized));
        TradeTemplate defaultTemplate = findTemplate(normalized, defaultTemplateId, DEFAULT_TEMPLATE_ID);
        TradeTemplate quickTemplate = findTemplate(normalized, quickTemplateId, defaultTemplate.getTemplateId());
        store.setTradeDefaultTemplateId(defaultTemplate.getTemplateId());
        store.setTradeQuickTemplateId(quickTemplate.getTemplateId());
        syncDefaultsFromTemplate(defaultTemplate);
    }

    // 单独更新快捷模板。
    public void setQuickTradeTemplateId(@Nullable String templateId) {
        TradeTemplate quickTemplate = findTemplate(getTemplates(), templateId, getDefaultTemplate().getTemplateId());
        store.setTradeQuickTemplateId(quickTemplate.getTemplateId());
    }

    // 单独更新默认模板。
    public void setDefaultTemplateId(@Nullable String templateId) {
        TradeTemplate defaultTemplate = findTemplate(getTemplates(), templateId, DEFAULT_TEMPLATE_ID);
        store.setTradeDefaultTemplateId(defaultTemplate.getTemplateId());
        syncDefaultsFromTemplate(defaultTemplate);
    }

    // 给交易命令补模板元信息，不改变执行动作本身。
    @NonNull
    public TradeCommand applyTemplate(@NonNull TradeCommand command, @Nullable TradeTemplate template) {
        return TradeCommandFactory.withTemplate(command, template);
    }

    @NonNull
    static List<TradeTemplate> buildSystemTemplates() {
        List<TradeTemplate> templates = new ArrayList<>();
        templates.add(new TradeTemplate(DEFAULT_TEMPLATE_ID, "默认模板", 0.05d, 0d, 0d, "both"));
        templates.add(new TradeTemplate("scalp_fast", "快进快出", 0.10d, 60d, 120d, "market"));
        templates.add(new TradeTemplate("swing_basic", "标准波段", 0.08d, 150d, 300d, "both"));
        return templates;
    }

    @NonNull
    static List<TradeTemplate> parseTemplates(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(json);
            List<TradeTemplate> templates = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                TradeTemplate template = TradeTemplate.fromJson(item);
                if (!template.getTemplateId().isEmpty()) {
                    templates.add(template);
                }
            }
            return templates;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @NonNull
    static String serializeTemplates(@Nullable List<TradeTemplate> templates) {
        JSONArray array = new JSONArray();
        if (templates != null) {
            for (TradeTemplate template : templates) {
                if (template == null || template.getTemplateId().isEmpty()) {
                    continue;
                }
                array.put(template.toJson());
            }
        }
        return array.toString();
    }

    @NonNull
    private List<TradeTemplate> normalizeTemplates(@Nullable List<TradeTemplate> templates) {
        List<TradeTemplate> normalized = new ArrayList<>();
        if (templates != null) {
            for (TradeTemplate template : templates) {
                if (template == null || template.getTemplateId().isEmpty()) {
                    continue;
                }
                normalized.add(template);
            }
        }
        return normalized.isEmpty() ? buildSystemTemplates() : normalized;
    }

    @NonNull
    private TradeTemplate findTemplate(@NonNull List<TradeTemplate> templates,
                                       @Nullable String requestedId,
                                       @Nullable String fallbackId) {
        String safeRequestedId = requestedId == null ? "" : requestedId.trim();
        String safeFallbackId = fallbackId == null ? "" : fallbackId.trim();
        for (TradeTemplate template : templates) {
            if (template != null && safeRequestedId.equals(template.getTemplateId()) && !safeRequestedId.isEmpty()) {
                return template;
            }
        }
        for (TradeTemplate template : templates) {
            if (template != null && safeFallbackId.equals(template.getTemplateId()) && !safeFallbackId.isEmpty()) {
                return template;
            }
        }
        return templates.isEmpty() ? buildSystemTemplates().get(0) : templates.get(0);
    }

    private void syncDefaultsFromTemplate(@NonNull TradeTemplate template) {
        store.setTradeDefaultVolume(template.getDefaultVolume());
        store.setTradeDefaultSl(template.getDefaultSl());
        store.setTradeDefaultTp(template.getDefaultTp());
    }

    private static final class ConfigStore implements Store {
        private final ConfigManager configManager;

        private ConfigStore(@NonNull ConfigManager configManager) {
            this.configManager = configManager;
        }

        @Override
        public double getTradeDefaultVolume() {
            return configManager.getTradeDefaultVolume();
        }

        @Override
        public void setTradeDefaultVolume(double volume) {
            configManager.setTradeDefaultVolume(volume);
        }

        @Override
        public double getTradeDefaultSl() {
            return configManager.getTradeDefaultSl();
        }

        @Override
        public void setTradeDefaultSl(double sl) {
            configManager.setTradeDefaultSl(sl);
        }

        @Override
        public double getTradeDefaultTp() {
            return configManager.getTradeDefaultTp();
        }

        @Override
        public void setTradeDefaultTp(double tp) {
            configManager.setTradeDefaultTp(tp);
        }

        @Override
        public String getTradeDefaultTemplateId() {
            return configManager.getTradeDefaultTemplateId();
        }

        @Override
        public void setTradeDefaultTemplateId(String templateId) {
            configManager.setTradeDefaultTemplateId(templateId);
        }

        @Override
        public String getTradeQuickTemplateId() {
            return configManager.getTradeQuickTemplateId();
        }

        @Override
        public void setTradeQuickTemplateId(String templateId) {
            configManager.setTradeQuickTemplateId(templateId);
        }

        @Override
        public String getTradeTemplatesJson() {
            return configManager.getTradeTemplatesJson();
        }

        @Override
        public void setTradeTemplatesJson(String templatesJson) {
            configManager.setTradeTemplatesJson(templatesJson);
        }
    }
}
