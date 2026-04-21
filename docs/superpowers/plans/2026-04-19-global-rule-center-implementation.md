# Global Rule Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 BTCXAU Android 客户端中建立“全局规则中心”，把视觉 token、模块容器、标准主体、指标定义、格式输出统一收口为单一真值，并让分析页、账户页、图表页、悬浮窗都改成只消费全局规则。

**Architecture:** 先用测试锁定“规则中心存在且页面不得再私有定义指标/格式”的合同，再在 `ui/rules` 下建立轻量注册中心，把现有 `colors.xml / dimens.xml / styles.xml / themes.xml / UiPaletteManager / AccountValueStyleHelper / FloatingWindowTextFormatter / AccountOverviewMetricsHelper` 统一挂到规则中心。页面迁移顺序严格按 `分析 -> 账户 -> 图表/悬浮窗 -> 清理` 执行，避免在规则未冻结前到处改页面。

**Tech Stack:** Android XML、Java、Material3、ViewBinding、JUnit4 源码/资源测试、Gradle `:app:testDebugUnitTest`、`:app:assembleDebug`

**Execution Status (2026-04-19):** 已完成。本计划中的规则中心骨架、指标注册表、格式中心、容器注册表、账户/图表/悬浮窗主链迁移与防回退测试均已落地；最新补收了 `AccountStatsScreen / AccountStatsBridgeActivity` 的交易汇总格式与红绿规则遗留，最终验收以本文件对应的规则中心定向测试和 `.\gradlew.bat :app:assembleDebug` 为准。

---

## File Map

### 新增规则中心骨架

- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorId.java`
  全局指标 ID 枚举，统一收口指标命名。
- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorCategory.java`
  资产 / 收益 / 交易 / 持仓 / 风险 / 监控分类。
- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorValueType.java`
  金额、百分比、数量、文本、时间等值类型。
- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorColorRule.java`
  指标的方向/红绿规则。
- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorDefinition.java`
  指标正式定义合同。
- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorRegistry.java`
  全局指标注册表，集中声明允许使用的指标。
- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorFormatterCenter.java`
  金额、百分比、数量、时间、状态、红绿统一格式入口。
- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorPresentation.java`
  页面消费用的展示结果对象，包含标题、格式化结果、颜色与空值表现。
- `app/src/main/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicy.java`
  把 definition + raw value 组装成可展示结果，页面不得绕过。
- `app/src/main/java/com/binance/monitor/ui/rules/ContainerSurfaceRole.java`
  `PageCanvas / SectionSurface / RowSurface / FieldSurface / OverlaySurface / FloatingSurface` 分类。
- `app/src/main/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistry.java`
  模块容器分类到 `UiPaletteManager` 背景/描边的映射。

### 现有共享入口，需要接入规则中心

- `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelper.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountValueStyleHelper.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricViewBinder.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsSummaryDetailAdapter.java`
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java`

### 分析页 / 账户页 / 图表页主链

- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/AccountMetricAdapter.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricAdapter.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapterV2.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAggregateAdapter.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`

### 文档与测试

- `app/src/test/java/com/binance/monitor/ui/rules/IndicatorRegistryTest.java`
- `app/src/test/java/com/binance/monitor/ui/rules/IndicatorFormatterCenterTest.java`
- `app/src/test/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicyTest.java`
- `app/src/test/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistrySourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelperTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountValueNumberColorSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatterTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java`
- `README.md`
- `ARCHITECTURE.md`
- `CONTEXT.md`

---

### Task 1: 建立规则中心合同测试与骨架类型

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/rules/IndicatorRegistryTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/rules/IndicatorFormatterCenterTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicyTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistrySourceTest.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorId.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorCategory.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorValueType.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorColorRule.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorDefinition.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/ContainerSurfaceRole.java`

- [ ] **Step 1: 写失败测试，锁定规则中心必须存在**

```java
@Test
public void indicatorRegistryShouldExposeCanonicalIds() {
    assertNotNull(IndicatorId.ACCOUNT_TOTAL_ASSET);
    assertNotNull(IndicatorId.ACCOUNT_TOTAL_RETURN_RATE);
    assertNotNull(IndicatorId.ACCOUNT_MAX_DRAWDOWN);
    assertNotNull(IndicatorId.TRADE_WIN_RATE);
}
```

- [ ] **Step 2: 写失败测试，锁定指标定义合同字段**

```java
@Test
public void indicatorDefinitionShouldKeepCoreContractFields() {
    IndicatorDefinition definition = new IndicatorDefinition(
            IndicatorId.ACCOUNT_TOTAL_RETURN_RATE,
            "累计收益率",
            "累计收益率",
            IndicatorCategory.RETURN,
            IndicatorValueType.PERCENT,
            "%",
            2,
            IndicatorColorRule.PROFIT_UP_LOSS_DOWN
    );

    assertEquals("累计收益率", definition.getDisplayName());
    assertEquals(2, definition.getPrecision());
    assertEquals(IndicatorColorRule.PROFIT_UP_LOSS_DOWN, definition.getColorRule());
}
```

- [ ] **Step 3: 写失败测试，锁定容器角色枚举**

```java
@Test
public void containerSurfaceRoleShouldCoverSixCanonicalRoles() {
    assertNotNull(ContainerSurfaceRole.PAGE_CANVAS);
    assertNotNull(ContainerSurfaceRole.SECTION_SURFACE);
    assertNotNull(ContainerSurfaceRole.ROW_SURFACE);
    assertNotNull(ContainerSurfaceRole.FIELD_SURFACE);
    assertNotNull(ContainerSurfaceRole.OVERLAY_SURFACE);
    assertNotNull(ContainerSurfaceRole.FLOATING_SURFACE);
}
```

- [ ] **Step 4: 运行测试，确认当前先失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.IndicatorRegistryTest" --tests "com.binance.monitor.ui.rules.IndicatorFormatterCenterTest" --tests "com.binance.monitor.ui.rules.IndicatorPresentationPolicyTest" --tests "com.binance.monitor.ui.rules.ContainerSurfaceRegistrySourceTest"
```

Expected:

- `ClassNotFoundException` 或编译失败，因为规则中心骨架还不存在

- [ ] **Step 5: 建立最小骨架类型**

```java
public enum IndicatorId {
    ACCOUNT_TOTAL_ASSET,
    ACCOUNT_NET_ASSET,
    ACCOUNT_POSITION_PNL,
    ACCOUNT_POSITION_PNL_RATE,
    ACCOUNT_TOTAL_RETURN_AMOUNT,
    ACCOUNT_TOTAL_RETURN_RATE,
    ACCOUNT_MAX_DRAWDOWN,
    TRADE_TOTAL_COUNT,
    TRADE_WIN_RATE
}
```

```java
public final class IndicatorDefinition {
    private final IndicatorId id;
    private final String displayName;
    private final String shortName;
    private final IndicatorCategory category;
    private final IndicatorValueType valueType;
    private final String unit;
    private final int precision;
    private final IndicatorColorRule colorRule;

    public IndicatorDefinition(IndicatorId id,
                               String displayName,
                               String shortName,
                               IndicatorCategory category,
                               IndicatorValueType valueType,
                               String unit,
                               int precision,
                               IndicatorColorRule colorRule) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.category = category;
        this.valueType = valueType;
        this.unit = unit;
        this.precision = precision;
        this.colorRule = colorRule;
    }
}
```

- [ ] **Step 6: 重新运行定向测试，确认骨架通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.IndicatorRegistryTest" --tests "com.binance.monitor.ui.rules.ContainerSurfaceRegistrySourceTest"
```

Expected:

- PASS

- [ ] **Step 7: 提交 Task 1**

```powershell
git add app/src/main/java/com/binance/monitor/ui/rules/IndicatorId.java app/src/main/java/com/binance/monitor/ui/rules/IndicatorCategory.java app/src/main/java/com/binance/monitor/ui/rules/IndicatorValueType.java app/src/main/java/com/binance/monitor/ui/rules/IndicatorColorRule.java app/src/main/java/com/binance/monitor/ui/rules/IndicatorDefinition.java app/src/main/java/com/binance/monitor/ui/rules/ContainerSurfaceRole.java app/src/test/java/com/binance/monitor/ui/rules/IndicatorRegistryTest.java app/src/test/java/com/binance/monitor/ui/rules/IndicatorFormatterCenterTest.java app/src/test/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicyTest.java app/src/test/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistrySourceTest.java
git commit -m "feat: add global rule center contracts"
```

### Task 2: 建立全局指标注册表与格式中心

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorRegistry.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorFormatterCenter.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorPresentation.java`
- Create: `app/src/main/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicy.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/rules/IndicatorRegistryTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/rules/IndicatorFormatterCenterTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicyTest.java`

- [ ] **Step 1: 写失败测试，锁定首批指标注册**

```java
@Test
public void registryShouldExposeCoreAccountAndTradeIndicators() {
    assertEquals("总资产", IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_ASSET).getDisplayName());
    assertEquals("累计收益率", IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_RETURN_RATE).getDisplayName());
    assertEquals("最大回撤", IndicatorRegistry.require(IndicatorId.ACCOUNT_MAX_DRAWDOWN).getDisplayName());
    assertEquals("胜率", IndicatorRegistry.require(IndicatorId.TRADE_WIN_RATE).getDisplayName());
}
```

- [ ] **Step 2: 写失败测试，锁定统一格式输出**

```java
@Test
public void formatterCenterShouldFormatPercentAndMoneyConsistently() {
    assertEquals("+12.35%", IndicatorFormatterCenter.formatPercent(0.12345, 2, true));
    assertEquals("-$56.70", IndicatorFormatterCenter.formatMoney(-56.7, 2, false));
}
```

- [ ] **Step 3: 写失败测试，锁定红绿判断不再依赖页面标题**

```java
@Test
public void presentationPolicyShouldResolveColorFromDefinitionRule() {
    IndicatorPresentation presentation = IndicatorPresentationPolicy.present(
            IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_RETURN_AMOUNT),
            -23.5d,
            false
    );

    assertEquals("累计收益额", presentation.getLabel());
    assertEquals("-$23.50", presentation.getFormattedValue());
    assertEquals(IndicatorColorRule.PROFIT_UP_LOSS_DOWN, presentation.getColorRule());
}
```

- [ ] **Step 4: 跑测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.IndicatorRegistryTest" --tests "com.binance.monitor.ui.rules.IndicatorFormatterCenterTest" --tests "com.binance.monitor.ui.rules.IndicatorPresentationPolicyTest"
```

Expected:

- 断言失败，因为注册表和格式中心还不存在或返回值不对

- [ ] **Step 5: 实现指标注册表**

```java
public final class IndicatorRegistry {
    private static final Map<IndicatorId, IndicatorDefinition> DEFINITIONS = new EnumMap<>(IndicatorId.class);

    static {
        register(new IndicatorDefinition(IndicatorId.ACCOUNT_TOTAL_ASSET, "总资产", "总资产",
                IndicatorCategory.ASSET, IndicatorValueType.MONEY, "$", 2, IndicatorColorRule.NEUTRAL));
        register(new IndicatorDefinition(IndicatorId.ACCOUNT_TOTAL_RETURN_AMOUNT, "累计收益额", "累计收益额",
                IndicatorCategory.RETURN, IndicatorValueType.MONEY, "$", 2, IndicatorColorRule.PROFIT_UP_LOSS_DOWN));
        register(new IndicatorDefinition(IndicatorId.ACCOUNT_TOTAL_RETURN_RATE, "累计收益率", "累计收益率",
                IndicatorCategory.RETURN, IndicatorValueType.PERCENT, "%", 2, IndicatorColorRule.PROFIT_UP_LOSS_DOWN));
        register(new IndicatorDefinition(IndicatorId.ACCOUNT_MAX_DRAWDOWN, "最大回撤", "最大回撤",
                IndicatorCategory.RISK, IndicatorValueType.PERCENT, "%", 2, IndicatorColorRule.DRAWDOWN_HIGH_IS_BAD));
    }

    public static IndicatorDefinition require(IndicatorId id) {
        IndicatorDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Missing indicator definition for " + id);
        }
        return definition;
    }
}
```

- [ ] **Step 6: 实现统一格式中心与 presentation policy**

```java
public final class IndicatorFormatterCenter {
    public static String formatMoney(double value, int precision, boolean masked) {
        if (masked) {
            return "***";
        }
        return String.format(Locale.US, "%s$%." + precision + "f", value >= 0d ? "+" : "-", Math.abs(value));
    }

    public static String formatPercent(double value, int precision, boolean showSign) {
        double percent = value * 100d;
        String sign = percent > 0d && showSign ? "+" : "";
        return String.format(Locale.US, "%s%." + precision + "f%%", sign, percent);
    }
}
```

- [ ] **Step 7: 重新运行测试，确认注册表和格式中心通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.IndicatorRegistryTest" --tests "com.binance.monitor.ui.rules.IndicatorFormatterCenterTest" --tests "com.binance.monitor.ui.rules.IndicatorPresentationPolicyTest"
```

Expected:

- PASS

- [ ] **Step 8: 提交 Task 2**

```powershell
git add app/src/main/java/com/binance/monitor/ui/rules/IndicatorRegistry.java app/src/main/java/com/binance/monitor/ui/rules/IndicatorFormatterCenter.java app/src/main/java/com/binance/monitor/ui/rules/IndicatorPresentation.java app/src/main/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicy.java app/src/test/java/com/binance/monitor/ui/rules/IndicatorRegistryTest.java app/src/test/java/com/binance/monitor/ui/rules/IndicatorFormatterCenterTest.java app/src/test/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicyTest.java
git commit -m "feat: add indicator registry and formatter center"
```

### Task 3: 建立模块容器注册表并把 UiPaletteManager 接到正式角色

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistry.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistrySourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java`

- [ ] **Step 1: 写失败测试，锁定容器角色必须映射到统一入口**

```java
@Test
public void containerRegistryShouldMapSectionAndOverlayRoles() {
    assertEquals(ContainerSurfaceRole.SECTION_SURFACE,
            ContainerSurfaceRegistry.resolveSectionRole("account_stats"));
    assertEquals(ContainerSurfaceRole.OVERLAY_SURFACE,
            ContainerSurfaceRegistry.resolveSectionRole("global_status_sheet"));
}
```

- [ ] **Step 2: 写失败测试，锁定 UiPaletteManager 不再散落 new shape 逻辑**

```java
@Test
public void uiPaletteManagerShouldDelegateSurfaceCreationToRegistry() throws Exception {
    String source = readUtf8("src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java");
    assertTrue(source.contains("ContainerSurfaceRegistry"));
}
```

- [ ] **Step 3: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.ContainerSurfaceRegistrySourceTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest" --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest"
```

Expected:

- 断言失败，因为容器注册表尚未接入

- [ ] **Step 4: 实现容器角色注册表**

```java
public final class ContainerSurfaceRegistry {
    public static ContainerSurfaceRole resolveSectionRole(String tag) {
        if ("global_status_sheet".equals(tag) || "dialog".equals(tag)) {
            return ContainerSurfaceRole.OVERLAY_SURFACE;
        }
        if ("floating_window".equals(tag)) {
            return ContainerSurfaceRole.FLOATING_SURFACE;
        }
        return ContainerSurfaceRole.SECTION_SURFACE;
    }
}
```

- [ ] **Step 5: 修改 UiPaletteManager，把 surface 创建收口到角色**

```java
public static GradientDrawable createSurfaceForRole(Context context,
                                                    Palette palette,
                                                    ContainerSurfaceRole role) {
    switch (role) {
        case PAGE_CANVAS:
            return createPageBackground(context, palette);
        case FLOATING_SURFACE:
            return createFloatingBackground(context, palette);
        case ROW_SURFACE:
            return createListRowBackground(context, palette.surfaceEnd, palette.stroke);
        case OVERLAY_SURFACE:
            return createSurfaceDrawable(context, palette.card, palette.stroke);
        case FIELD_SURFACE:
            return createOutlinedDrawable(context, palette.control, palette.stroke);
        case SECTION_SURFACE:
        default:
            return createSurfaceDrawable(context, palette.card, palette.stroke);
    }
}
```

- [ ] **Step 6: 重新运行测试，确认容器角色主链通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.ContainerSurfaceRegistrySourceTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest" --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest"
```

Expected:

- PASS

- [ ] **Step 7: 提交 Task 3**

```powershell
git add app/src/main/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistry.java app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java app/src/test/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistrySourceTest.java app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java
git commit -m "feat: route surfaces through container registry"
```

### Task 4: 先迁移分析页与账户统计主链到全局指标中心

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricViewBinder.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsSummaryDetailAdapter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/AccountMetricAdapter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricAdapter.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountValueNumberColorSourceTest.java`

- [ ] **Step 1: 写失败测试，锁定账户总览不再直接写标题**

```java
@Test
public void overviewHelperShouldBuildCanonicalMetricIds() {
    List<AccountMetric> metrics = AccountOverviewMetricsHelper.buildOverviewMetrics(...);
    assertEquals("总资产", metrics.get(0).getName());
    assertEquals("净资产", metrics.get(1).getName());
}
```

- [ ] **Step 2: 写失败测试，锁定统计绑定器走全局 presentation**

```java
@Test
public void sourceShouldUseIndicatorPresentationPolicy() throws Exception {
    String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricViewBinder.java");
    assertTrue(source.contains("IndicatorPresentationPolicy"));
    assertFalse(source.contains("resolveMetricDirection("));
}
```

- [ ] **Step 3: 跑测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountOverviewMetricsHelperTest" --tests "com.binance.monitor.ui.account.AccountValueNumberColorSourceTest"
```

Expected:

- 断言失败，因为 helper 和 binder 仍在直接拼标题与颜色

- [ ] **Step 4: 改造账户总览 helper，先输出规范指标名**

```java
replaceOrAppendOverviewMetric(result,
        IndicatorRegistry.require(IndicatorId.ACCOUNT_POSITION_PNL).getDisplayName(),
        IndicatorFormatterCenter.formatMoney(overviewValues.getPositionPnl(), 2, false));

replaceOrAppendOverviewMetric(result,
        IndicatorRegistry.require(IndicatorId.ACCOUNT_POSITION_PNL_RATE).getDisplayName(),
        IndicatorFormatterCenter.formatPercent(overviewValues.getPositionPnlRate(), 2, true));
```

- [ ] **Step 5: 改造统计绑定器与展开区适配器，统一走 presentation policy**

```java
IndicatorPresentation presentation = IndicatorPresentationPolicy.present(
        IndicatorRegistry.findByDisplayName(nameCn),
        value,
        masked
);
nameView.setText(presentation.getLabel());
valueView.setText(presentation.getFormattedValue());
valueView.setTextColor(presentation.resolveAndroidColor(context));
```

- [ ] **Step 6: 重新运行分析页/账户统计定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountOverviewMetricsHelperTest" --tests "com.binance.monitor.ui.account.AccountValueNumberColorSourceTest" --tests "com.binance.monitor.ui.account.AnalysisStatsSummaryExpandSourceTest"
```

Expected:

- PASS

- [ ] **Step 7: 提交 Task 4**

```powershell
git add app/src/main/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelper.java app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricViewBinder.java app/src/main/java/com/binance/monitor/ui/account/adapter/StatsSummaryDetailAdapter.java app/src/main/java/com/binance/monitor/ui/account/adapter/AccountMetricAdapter.java app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricAdapter.java app/src/test/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelperTest.java app/src/test/java/com/binance/monitor/ui/account/AccountValueNumberColorSourceTest.java
git commit -m "feat: route account stats through indicator center"
```

### Task 5: 迁移账户持仓、历史、聚合摘要到统一格式与颜色中心

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAggregateAdapter.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionAdapterSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionEnhancementSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/TradeRecordAdapterV2SourceTest.java`

- [ ] **Step 1: 写失败测试，锁定适配器不再手写金额格式和红绿范围**

```java
@Test
public void positionAdapterShouldUseIndicatorFormatterCenter() throws Exception {
    String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");
    assertTrue(source.contains("IndicatorFormatterCenter"));
    assertFalse(source.contains("ForegroundColorSpan("));
}
```

- [ ] **Step 2: 跑测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPositionAdapterSourceTest" --tests "com.binance.monitor.ui.account.TradeRecordAdapterV2SourceTest"
```

Expected:

- FAIL，当前适配器仍在页面侧处理部分格式/着色

- [ ] **Step 3: 把持仓 / 历史 / 聚合摘要统一改成由规则中心输出字符串**

```java
binding.tvPositionPnl.setText(
        IndicatorFormatterCenter.formatMoney(item.getProfit(), 2, masked)
);
binding.tvPositionPnl.setTextColor(
        IndicatorPresentationPolicy.present(
                IndicatorRegistry.require(IndicatorId.ACCOUNT_POSITION_PNL),
                item.getProfit(),
                masked
        ).resolveAndroidColor(context)
);
```

- [ ] **Step 4: 把“产品 | 买卖/手数/盈亏”聚合摘要改成复用统一 formatter**

```java
String pnlText = IndicatorFormatterCenter.formatMoney(summary.getPnl(), 2, masked);
String lotsText = IndicatorFormatterCenter.formatQuantity(summary.getLots(), 2, "手");
binding.tvAggregateSummary.setText(symbol + " | " + direction + " | " + lotsText + " | " + pnlText);
```

- [ ] **Step 5: 重新运行账户列表相关定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPositionAdapterSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionEnhancementSourceTest" --tests "com.binance.monitor.ui.account.TradeRecordAdapterV2SourceTest"
```

Expected:

- PASS

- [ ] **Step 6: 提交 Task 5**

```powershell
git add app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java app/src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapterV2.java app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAggregateAdapter.java app/src/test/java/com/binance/monitor/ui/account/AccountPositionAdapterSourceTest.java app/src/test/java/com/binance/monitor/ui/account/AccountPositionEnhancementSourceTest.java app/src/test/java/com/binance/monitor/ui/account/TradeRecordAdapterV2SourceTest.java
git commit -m "feat: unify account list formatting through rule center"
```

### Task 6: 迁移图表摘要与悬浮窗到统一格式中心

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatterTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`

- [ ] **Step 1: 写失败测试，锁定悬浮窗文案必须走 formatter center**

```java
@Test
public void floatingFormatterShouldDelegateMoneyAndQuantityFormatting() throws Exception {
    String source = readUtf8("src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java");
    assertTrue(source.contains("IndicatorFormatterCenter"));
}
```

- [ ] **Step 2: 写失败测试，锁定图表摘要不再页面侧猜红绿**

```java
@Test
public void marketChartSourceShouldUseIndicatorPresentationPolicyForSummary() throws Exception {
    String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
    assertTrue(source.contains("IndicatorPresentationPolicy"));
}
```

- [ ] **Step 3: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingWindowTextFormatterTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest"
```

Expected:

- FAIL，因为图表和悬浮窗还在直接用旧格式函数

- [ ] **Step 4: 改造悬浮窗 formatter**

```java
static String formatPnlAmount(double totalPnl, boolean masked) {
    return IndicatorFormatterCenter.formatMoney(totalPnl, 1, masked);
}

static String formatLotsText(double totalLots) {
    return IndicatorFormatterCenter.formatQuantity(totalLots, 2, "手");
}
```

- [ ] **Step 5: 改造图表页摘要绑定**

```java
IndicatorPresentation pnlPresentation = IndicatorPresentationPolicy.present(
        IndicatorRegistry.require(IndicatorId.ACCOUNT_POSITION_PNL),
        currentPnl,
        masked
);
binding.tvChartPositionSummary.setText(pnlPresentation.getFormattedValue());
binding.tvChartPositionSummary.setTextColor(pnlPresentation.resolveAndroidColor(this));
```

- [ ] **Step 6: 重新运行图表/悬浮窗定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingWindowTextFormatterTest" --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest"
```

Expected:

- PASS

- [ ] **Step 7: 提交 Task 6**

```powershell
git add app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatterTest.java app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java
git commit -m "feat: route chart and floating summaries through rule center"
```

### Task 7: 增加防回退测试，禁止页面私有指标和私有格式

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/rules/PagePrivateIndicatorSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountValueNumberColorSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java`

- [ ] **Step 1: 写失败测试，禁止页面新增私有指标标题**

```java
@Test
public void accountAndChartPagesShouldNotDefinePrivateIndicatorLabels() throws Exception {
    assertFalse(readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java")
            .contains("累计收益额："));
    assertFalse(readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
            .contains("持仓盈亏："));
}
```

- [ ] **Step 2: 写失败测试，禁止页面直接 setTextColor 做业务红绿**

```java
@Test
public void pagesShouldNotSetBusinessColorsDirectly() throws Exception {
    String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
    assertFalse(source.contains("R.color.accent_green"));
    assertFalse(source.contains("R.color.accent_red"));
}
```

- [ ] **Step 3: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.PagePrivateIndicatorSourceTest" --tests "com.binance.monitor.ui.account.AccountValueNumberColorSourceTest" --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest"
```

Expected:

- FAIL，因为页面仍残留私有格式和私有颜色分支

- [ ] **Step 4: 把测试口径同步到规则中心新边界**

```java
assertTrue(source.contains("IndicatorRegistry"));
assertTrue(source.contains("IndicatorFormatterCenter"));
assertTrue(source.contains("IndicatorPresentationPolicy"));
```

- [ ] **Step 5: 重新运行防回退测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.PagePrivateIndicatorSourceTest" --tests "com.binance.monitor.ui.account.AccountValueNumberColorSourceTest" --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest"
```

Expected:

- PASS

- [ ] **Step 6: 提交 Task 7**

```powershell
git add app/src/test/java/com/binance/monitor/ui/rules/PagePrivateIndicatorSourceTest.java app/src/test/java/com/binance/monitor/ui/account/AccountValueNumberColorSourceTest.java app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java
git commit -m "test: ban private indicator and formatting logic"
```

### Task 8: 更新项目文档并做最终验证

**Files:**
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 更新 README，补充“全局规则中心”**

```md
## 已完成功能
- 已建立全局规则中心：视觉 token、模块容器、标准主体、指标定义、格式中心统一收口。

## 测试方法
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.*"`
```

- [ ] **Step 2: 更新 ARCHITECTURE，说明新模块职责**

```md
- `ui/rules/IndicatorRegistry.java`
  全局指标正式定义中心，页面不得再私有定义指标。
- `ui/rules/IndicatorFormatterCenter.java`
  统一金额、百分比、数量、时间、状态格式。
- `ui/rules/ContainerSurfaceRegistry.java`
  统一模块容器角色到视觉外壳的映射。
```

- [ ] **Step 3: 更新 CONTEXT，记录当前完成状态与停点**

```md
- 最新续做已完成“全局规则中心”第一阶段实现：规则中心骨架、指标注册表、格式中心、分析/账户/图表主链迁移已完成，页面私有指标与私有格式已被源测试拦住。当前停点是等待用户验证页面表现与统计口径。
```

- [ ] **Step 4: 运行规则中心相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.rules.IndicatorRegistryTest" --tests "com.binance.monitor.ui.rules.IndicatorFormatterCenterTest" --tests "com.binance.monitor.ui.rules.IndicatorPresentationPolicyTest" --tests "com.binance.monitor.ui.rules.PagePrivateIndicatorSourceTest" --tests "com.binance.monitor.ui.account.AccountOverviewMetricsHelperTest" --tests "com.binance.monitor.ui.account.AccountValueNumberColorSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowTextFormatterTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest"
```

Expected:

- PASS

- [ ] **Step 5: 跑完整构建**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交 Task 8**

```powershell
git add README.md ARCHITECTURE.md CONTEXT.md
git commit -m "docs: record global rule center architecture"
```

## Self-Review

- 规格覆盖检查：
  - 视觉 Token 层：Task 3
  - 模块容器层：Task 3
  - 标准主体层：沿用现有主体真值，Task 3 和 Task 7 负责防回退
  - 指标定义层：Task 1、Task 2
  - 格式与展示层：Task 2、Task 4、Task 5、Task 6
  - 页面禁止私有定义：Task 7
  - 文档同步：Task 8
- 占位词检查：本计划未使用 `TODO / TBD / implement later / similar to`
- 类型一致性检查：
  - 指标 ID 统一使用 `IndicatorId`
  - 定义统一使用 `IndicatorDefinition`
  - 页面展示统一使用 `IndicatorPresentationPolicy`
  - 容器角色统一使用 `ContainerSurfaceRole`
