/*
 * 全局指标定义合同，固定指标显示名、值类型、精度与颜色规则。
 */
package com.binance.monitor.ui.rules;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class IndicatorDefinition {
    private final IndicatorId id;
    private final String displayName;
    private final String shortName;
    private final IndicatorCategory category;
    private final IndicatorValueType valueType;
    private final String unit;
    private final int precision;
    private final IndicatorColorRule colorRule;
    private final List<String> aliases;

    // 构建正式指标定义，并记录迁移期允许识别的历史别名。
    public IndicatorDefinition(@NonNull IndicatorId id,
                               @NonNull String displayName,
                               @NonNull String shortName,
                               @NonNull IndicatorCategory category,
                               @NonNull IndicatorValueType valueType,
                               @NonNull String unit,
                               int precision,
                               @NonNull IndicatorColorRule colorRule,
                               @NonNull String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.category = category;
        this.valueType = valueType;
        this.unit = unit;
        this.precision = precision;
        this.colorRule = colorRule;
        this.aliases = aliases.length == 0
                ? Collections.emptyList()
                : Collections.unmodifiableList(Arrays.asList(aliases));
    }

    // 返回指标正式 ID。
    @NonNull
    public IndicatorId getId() {
        return id;
    }

    // 返回指标正式显示名。
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    // 返回指标短名称。
    @NonNull
    public String getShortName() {
        return shortName;
    }

    // 返回指标分类。
    @NonNull
    public IndicatorCategory getCategory() {
        return category;
    }

    // 返回指标值类型。
    @NonNull
    public IndicatorValueType getValueType() {
        return valueType;
    }

    // 返回指标单位。
    @NonNull
    public String getUnit() {
        return unit;
    }

    // 返回指标展示精度。
    public int getPrecision() {
        return precision;
    }

    // 返回指标颜色规则。
    @NonNull
    public IndicatorColorRule getColorRule() {
        return colorRule;
    }

    // 返回迁移期可识别的历史别名。
    @NonNull
    public List<String> getAliases() {
        return aliases;
    }
}
