# Trading Quick Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把交易页改成“顶部 `交易 / 挂单` 模式切换 + 图表内快捷执行”，删除旧快捷交易区和旧成交量/成交额摘要，并补齐真实下单链路与手机验证。

**Architecture:** 继续沿用现有 `MarketChartScreen + MarketChartTradeDialogCoordinator + TradeExecutionCoordinator + KlineChartView` 主线，不新增第二套交易提交架构。页面层只负责模式、输入和视图装配；快捷交易判断、命令生成和执行下沉到新的 `ChartQuickTradeCoordinator`；图表拖线只在 `KlineChartView` 内负责绘制和交互。

**Tech Stack:** Android Java、XML、ViewBinding、自定义 View (`KlineChartView`)、JUnit Source Test / Unit Test、Gradle (`testDebugUnitTest`, `assembleDebug`)、ADB 安装

---

## File Map

### Existing files to modify

- `app/src/main/res/layout/activity_market_chart.xml`
  交易页共享布局，负责顶部产品栏、快捷模式条、图表和旧快捷交易模块。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  交易页主装配层，负责按钮接线、图表状态、页面提示和执行触发。
- `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
  K 线自定义 View，负责绘制价格参考线、拖动交互和价格回传。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
  旧弹窗交易协调器，继续保留复杂交易入口；需要避免与新快捷模式冲突。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeCommandFactory.java`
  交易命令工厂，继续复用 `OPEN_MARKET / PENDING_ADD` 命令结构。
- `app/src/main/res/values/strings.xml`
  新增快捷交易、挂单模式、输入错误文案。
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradingStructureSourceTest.java`
  交易页源码结构测试，锁定旧模块删除和新结构存在。
- `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`
  图表源码结构测试，锁定参考线绘制与拖动入口。
- `app/src/test/java/com/binance/monitor/ui/trade/TradeCommandFactoryTest.java`
  命令工厂单测，补充快捷交易命令覆盖。

### New files to create

- `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeMode.java`
  快捷模式枚举，统一 `CLOSED / MARKET / PENDING`。
- `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java`
  快捷交易协调器，负责输入校验、当前有效价解析、挂单类型判断、命令生成和执行。
- `app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java`
  纯单测，覆盖市价命令、挂单类型自动判断、边界错误文案。
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartQuickTradeSourceTest.java`
  页面结构源码测试，锁定顶部按钮、快捷条容器、旧快捷交易模块删除。

### Existing docs to modify

- `CONTEXT.md`
  记录设计已批准、计划已写完，以及下一步实现停点。

## Task 1: 先锁定新交易页结构并删除旧模块

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartQuickTradeSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradingStructureSourceTest.java`
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`

- [ ] **Step 1: 写失败测试，锁定顶部模式按钮、快捷条容器和旧模块删除**

```java
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartQuickTradeSourceTest {

    @Test
    public void marketChartLayoutShouldExposeQuickTradeModesAndRemoveLegacyTradeCard() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_market_chart.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/btnChartModeMarket"));
        assertTrue(layout.contains("@+id/btnChartModePending"));
        assertTrue(layout.contains("@+id/layoutChartQuickTradeBar"));
        assertTrue(layout.contains("@+id/btnQuickTradePrimary"));
        assertTrue(layout.contains("@+id/etQuickTradeVolume"));
        assertTrue(layout.contains("@+id/btnQuickTradeSecondary"));
        assertFalse(layout.contains("@+id/cardChartTradeActions"));
        assertFalse(layout.contains("@+id/btnChartTradeBuy"));
        assertFalse(layout.contains("@+id/btnChartTradeSell"));
        assertFalse(layout.contains("@+id/btnChartTradePending"));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartQuickTradeSourceTest" -v
```

Expected:

```text
MarketChartQuickTradeSourceTest > marketChartLayoutShouldExposeQuickTradeModesAndRemoveLegacyTradeCard FAILED
```

- [ ] **Step 3: 最小改布局，把顶部按钮和快捷条放到图表上方**

在 `activity_market_chart.xml` 里把第一行收成“产品选择 | 交易 | 挂单 | 状态”，并在周期行上方新增一个默认 `gone` 的快捷条容器：

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/btnChartModeMarket"
    style="@style/Widget.BinanceMonitor.Button.SecondarySquare"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginEnd="6dp"
    android:minWidth="0dp"
    android:text="@string/chart_mode_market"
    android:textAllCaps="false"
    android:textSize="11sp" />

<com.google.android.material.button.MaterialButton
    android:id="@+id/btnChartModePending"
    style="@style/Widget.BinanceMonitor.Button.SecondarySquare"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginEnd="6dp"
    android:minWidth="0dp"
    android:text="@string/chart_mode_pending"
    android:textAllCaps="false"
    android:textSize="11sp" />
```

```xml
<LinearLayout
    android:id="@+id/layoutChartQuickTradeBar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:visibility="gone">

    <Button
        android:id="@+id/btnQuickTradePrimary"
        android:layout_width="0dp"
        android:layout_height="@dimen/control_height_sm"
        android:layout_weight="1"
        android:text="买入"
        android:textAllCaps="false" />

    <EditText
        android:id="@+id/etQuickTradeVolume"
        android:layout_width="72dp"
        android:layout_height="@dimen/control_height_sm"
        android:layout_marginStart="6dp"
        android:layout_marginEnd="6dp"
        android:gravity="center"
        android:inputType="numberDecimal"
        android:text="0.05" />

    <Button
        android:id="@+id/btnQuickTradeSecondary"
        android:layout_width="0dp"
        android:layout_height="@dimen/control_height_sm"
        android:layout_weight="1"
        android:text="卖出"
        android:textAllCaps="false" />
</LinearLayout>
```

同时删除旧 `cardChartTradeActions` 整块，保留风险条和图表本体不动。

在 `strings.xml` 中补齐文案：

```xml
<string name="chart_mode_market">交易</string>
<string name="chart_mode_pending">挂单</string>
<string name="chart_quick_trade_buy">买入</string>
<string name="chart_quick_trade_sell">卖出</string>
<string name="chart_quick_trade_pending_buy">挂单买入</string>
<string name="chart_quick_trade_pending_sell">挂单卖出</string>
<string name="chart_quick_trade_default_volume">0.05</string>
```

- [ ] **Step 4: 更新交易结构测试，明确旧卡片已被新模式条替代**

把 `MarketChartTradingStructureSourceTest` 的断言改成新结构：

```java
assertTrue(layout.contains("@+id/cardChartRiskBanner"));
assertTrue(layout.contains("@+id/btnChartModeMarket"));
assertTrue(layout.contains("@+id/btnChartModePending"));
assertTrue(layout.contains("@+id/layoutChartQuickTradeBar"));
assertFalse(layout.contains("@+id/cardChartTradeActions"));
assertFalse(layout.contains("@+id/btnChartTradeBuy"));
assertFalse(layout.contains("@+id/btnChartTradeSell"));
assertFalse(layout.contains("@+id/btnChartTradePending"));
```

- [ ] **Step 5: 运行页面结构测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartQuickTradeSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradingStructureSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/activity_market_chart.xml \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/binance/monitor/ui/chart/MarketChartQuickTradeSourceTest.java \
        app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradingStructureSourceTest.java
git commit -m "feat: add chart quick trade mode bar skeleton"
```

## Task 2: 下沉快捷交易协调层，接上市价与挂单执行规则

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeMode.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java`
- Create: `app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeCommandFactory.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/trade/TradeCommandFactoryTest.java`

- [ ] **Step 1: 写失败单测，锁定挂单类型自动判断和快捷命令生成**

```java
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.ui.trade.TradeExecutionCoordinator;

import org.junit.Test;

public class ChartQuickTradeCoordinatorTest {

    @Test
    public void marketBuyShouldBuildOpenMarketBuyCommand() {
        FakeExecutor executor = new FakeExecutor();
        ChartQuickTradeCoordinator coordinator = new ChartQuickTradeCoordinator(
                () -> "acc-1",
                () -> "BTCUSD",
                () -> 65000d,
                executor
        );

        coordinator.executeMarketBuy("0.05");

        TradeCommand command = executor.lastCommand;
        assertEquals("OPEN_MARKET", command.getAction());
        assertEquals("buy", command.getParams().optString("side", ""));
        assertEquals(0.05d, command.getVolume(), 0.0000001d);
    }

    @Test
    public void pendingSellAboveCurrentShouldResolveSellLimit() {
        FakeExecutor executor = new FakeExecutor();
        ChartQuickTradeCoordinator coordinator = new ChartQuickTradeCoordinator(
                () -> "acc-1",
                () -> "BTCUSD",
                () -> 65000d,
                executor
        );

        coordinator.executePendingSell("0.05", 65100d);

        assertEquals("sell_limit", executor.lastCommand.getParams().optString("orderType", ""));
    }

    @Test
    public void pendingLineTooCloseShouldReject() {
        FakeExecutor executor = new FakeExecutor();
        ChartQuickTradeCoordinator coordinator = new ChartQuickTradeCoordinator(
                () -> "acc-1",
                () -> "BTCUSD",
                () -> 65000d,
                executor
        );

        try {
            coordinator.executePendingBuy("0.05", 65000d);
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("挂单线"));
        }
    }

    private static final class FakeExecutor implements ChartQuickTradeCoordinator.Executor {
        private TradeCommand lastCommand;

        @Override
        public void execute(TradeCommand command) {
            lastCommand = command;
        }
    }
}
```

- [ ] **Step 2: 运行单测，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartQuickTradeCoordinatorTest" -v
```

Expected:

```text
ChartQuickTradeCoordinatorTest > marketBuyShouldBuildOpenMarketBuyCommand FAILED
```

- [ ] **Step 3: 实现模式枚举和快捷交易协调器**

`ChartQuickTradeMode.java`：

```java
package com.binance.monitor.ui.chart;

enum ChartQuickTradeMode {
    CLOSED,
    MARKET,
    PENDING
}
```

`ChartQuickTradeCoordinator.java`：

```java
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;

import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.ui.trade.TradeCommandFactory;

import java.util.Locale;

final class ChartQuickTradeCoordinator {

    interface AccountProvider { @NonNull String getAccountId(); }
    interface SymbolProvider { @NonNull String getTradeSymbol(); }
    interface PriceProvider { double getCurrentPrice(); }
    interface Executor { void execute(TradeCommand command); }

    private static final double MIN_PENDING_DISTANCE = 1e-6d;

    private final AccountProvider accountProvider;
    private final SymbolProvider symbolProvider;
    private final PriceProvider priceProvider;
    private final Executor executor;

    ChartQuickTradeCoordinator(AccountProvider accountProvider,
                               SymbolProvider symbolProvider,
                               PriceProvider priceProvider,
                               Executor executor) {
        this.accountProvider = accountProvider;
        this.symbolProvider = symbolProvider;
        this.priceProvider = priceProvider;
        this.executor = executor;
    }

    void executeMarketBuy(@NonNull String volumeText) {
        execute(TradeCommandFactory.openMarket(
                requireAccountId(),
                requireSymbol(),
                "buy",
                requireVolume(volumeText),
                requireCurrentPrice(),
                0d,
                0d
        ));
    }

    void executeMarketSell(@NonNull String volumeText) {
        execute(TradeCommandFactory.openMarket(
                requireAccountId(),
                requireSymbol(),
                "sell",
                requireVolume(volumeText),
                requireCurrentPrice(),
                0d,
                0d
        ));
    }

    void executePendingBuy(@NonNull String volumeText, double linePrice) {
        executePending(volumeText, linePrice, true);
    }

    void executePendingSell(@NonNull String volumeText, double linePrice) {
        executePending(volumeText, linePrice, false);
    }

    private void executePending(@NonNull String volumeText, double linePrice, boolean buySide) {
        double currentPrice = requireCurrentPrice();
        requirePendingDistance(linePrice, currentPrice);
        String orderType = resolvePendingOrderType(buySide, linePrice, currentPrice);
        execute(TradeCommandFactory.pendingAdd(
                requireAccountId(),
                requireSymbol(),
                orderType,
                requireVolume(volumeText),
                linePrice,
                0d,
                0d
        ));
    }

    @NonNull
    String resolvePendingOrderType(boolean buySide, double linePrice, double currentPrice) {
        if (buySide) {
            return linePrice < currentPrice ? "buy_limit" : "buy_stop";
        }
        return linePrice > currentPrice ? "sell_limit" : "sell_stop";
    }

    private void execute(TradeCommand command) {
        executor.execute(command);
    }

    private void requirePendingDistance(double linePrice, double currentPrice) {
        if (linePrice <= 0d) {
            throw new IllegalArgumentException("挂单价格未就绪");
        }
        if (Math.abs(linePrice - currentPrice) <= MIN_PENDING_DISTANCE) {
            throw new IllegalArgumentException("请把挂单线再上移或下移一点");
        }
    }

    private double requireVolume(@NonNull String volumeText) {
        double volume = MarketChartTradeSupport.parseOptionalDouble(volumeText, 0d);
        if (volume <= 0d) {
            throw new IllegalArgumentException("手数必须大于 0");
        }
        return volume;
    }

    private double requireCurrentPrice() {
        double value = priceProvider.getCurrentPrice();
        if (value <= 0d) {
            throw new IllegalArgumentException("当前价格未就绪，暂时不能执行交易");
        }
        return value;
    }

    private String requireAccountId() {
        String accountId = accountProvider.getAccountId().trim();
        if (accountId.isEmpty()) {
            throw new IllegalArgumentException("当前账户未连接，暂时不能交易");
        }
        return accountId;
    }

    private String requireSymbol() {
        String symbol = symbolProvider.getTradeSymbol().trim().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("当前品种未就绪");
        }
        return symbol;
    }
}
```

- [ ] **Step 4: 在 `MarketChartScreen` 里接模式状态、按钮互斥和执行桥**

在字段区补状态：

```java
private ChartQuickTradeMode quickTradeMode = ChartQuickTradeMode.CLOSED;
private double pendingLinePrice = Double.NaN;
private ChartQuickTradeCoordinator chartQuickTradeCoordinator;
```

在初始化里创建协调器，继续复用现有执行链：

```java
chartQuickTradeCoordinator = new ChartQuickTradeCoordinator(
        this::resolveQuickTradeAccountId,
        () -> MarketChartTradeSupport.toTradeSymbol(selectedSymbol),
        this::resolveQuickTradeCurrentPrice,
        this::executeQuickTradeCommand
);
```

模式切换逻辑固定成互斥：

```java
private void toggleQuickTradeMode(@NonNull ChartQuickTradeMode targetMode) {
    if (quickTradeMode == targetMode) {
        applyQuickTradeMode(ChartQuickTradeMode.CLOSED);
        return;
    }
    applyQuickTradeMode(targetMode);
}

private void applyQuickTradeMode(@NonNull ChartQuickTradeMode mode) {
    quickTradeMode = mode;
    if (mode != ChartQuickTradeMode.PENDING) {
        pendingLinePrice = Double.NaN;
        binding.klineChartView.hideQuickPendingLine();
    } else {
        pendingLinePrice = resolveQuickTradeCurrentPrice();
        binding.klineChartView.showQuickPendingLine(pendingLinePrice);
    }
    updateQuickTradeModeButtons();
    updateQuickTradeBar();
}
```

快捷条文案随模式切换：

```java
private void updateQuickTradeBar() {
    boolean visible = quickTradeMode != ChartQuickTradeMode.CLOSED;
    binding.layoutChartQuickTradeBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (!visible) {
        return;
    }
    binding.etQuickTradeVolume.setText(binding.etQuickTradeVolume.getText().length() == 0
            ? getString(R.string.chart_quick_trade_default_volume)
            : binding.etQuickTradeVolume.getText());
    if (quickTradeMode == ChartQuickTradeMode.MARKET) {
        binding.btnQuickTradePrimary.setText(R.string.chart_quick_trade_buy);
        binding.btnQuickTradeSecondary.setText(R.string.chart_quick_trade_sell);
    } else {
        binding.btnQuickTradePrimary.setText(R.string.chart_quick_trade_pending_buy);
        binding.btnQuickTradeSecondary.setText(R.string.chart_quick_trade_pending_sell);
    }
}
```

执行按钮直接走协调器：

```java
binding.btnQuickTradePrimary.setOnClickListener(v -> executePrimaryQuickTrade());
binding.btnQuickTradeSecondary.setOnClickListener(v -> executeSecondaryQuickTrade());
binding.btnChartModeMarket.setOnClickListener(v -> toggleQuickTradeMode(ChartQuickTradeMode.MARKET));
binding.btnChartModePending.setOnClickListener(v -> toggleQuickTradeMode(ChartQuickTradeMode.PENDING));
binding.klineChartView.setOnQuickPendingLineChangeListener(price -> pendingLinePrice = price);
```

- [ ] **Step 5: 继续补命令工厂单测，锁定快捷挂单生成结果**

在 `TradeCommandFactoryTest` 里追加：

```java
@Test
public void pendingAddShouldKeepOrderTypeForQuickPendingTrade() {
    TradeCommand command = TradeCommandFactory.pendingAdd(
            "acc-1",
            "BTCUSD",
            "buy_stop",
            0.05d,
            65100d,
            0d,
            0d
    );

    assertEquals("PENDING_ADD", command.getAction());
    assertEquals("buy_stop", command.getParams().optString("orderType", ""));
    assertEquals(0.05d, command.getVolume(), 0.0000001d);
}
```

- [ ] **Step 6: 运行协调层与命令测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartQuickTradeCoordinatorTest" --tests "com.binance.monitor.ui.trade.TradeCommandFactoryTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSupportTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeMode.java \
        app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java \
        app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java \
        app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeCommandFactoryTest.java
git commit -m "feat: wire chart quick trade coordinator and mode state"
```

## Task 3: 给图表加可拖动挂单线，并把挂单模式闭环补齐

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`

- [ ] **Step 1: 写失败源码测试，锁定挂单线字段、绘制和拖动入口**

在 `KlineChartViewSourceTest` 里追加：

```java
@Test
public void klineChartViewShouldExposeQuickPendingLineDrawingAndDragHooks() throws Exception {
    Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
    String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n');

    assertTrue(source.contains("private boolean quickPendingLineVisible;"));
    assertTrue(source.contains("private double quickPendingLinePrice = Double.NaN;"));
    assertTrue(source.contains("public void showQuickPendingLine(double price) {"));
    assertTrue(source.contains("public void hideQuickPendingLine() {"));
    assertTrue(source.contains("public void setOnQuickPendingLineChangeListener("));
    assertTrue(source.contains("drawQuickPendingLine(canvas"));
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" -v
```

Expected:

```text
KlineChartViewSourceTest > klineChartViewShouldExposeQuickPendingLineDrawingAndDragHooks FAILED
```

- [ ] **Step 3: 在 `KlineChartView` 中增加挂单线状态和监听接口**

补字段和接口：

```java
public interface OnQuickPendingLineChangeListener {
    void onPriceChanged(double price);
}

private final Paint quickPendingLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
private final Paint quickPendingTagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
private final Paint quickPendingTagTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
private boolean quickPendingLineVisible;
private boolean quickPendingLineDragging;
private double quickPendingLinePrice = Double.NaN;
@Nullable
private OnQuickPendingLineChangeListener onQuickPendingLineChangeListener;
```

公开方法：

```java
public void showQuickPendingLine(double price) {
    quickPendingLineVisible = price > 0d;
    quickPendingLinePrice = price;
    requestFrame();
}

public void hideQuickPendingLine() {
    quickPendingLineVisible = false;
    quickPendingLineDragging = false;
    quickPendingLinePrice = Double.NaN;
    requestFrame();
}

public void setOnQuickPendingLineChangeListener(@Nullable OnQuickPendingLineChangeListener listener) {
    onQuickPendingLineChangeListener = listener;
}
```

- [ ] **Step 4: 在绘制和触摸里接入拖线**

在 `onDraw()` 的主价格区段里补：

```java
drawLatestPriceGuide(canvas);
if (quickPendingLineVisible) {
    drawQuickPendingLine(canvas, visiblePriceMin, visiblePriceMax);
}
```

绘制方法：

```java
private void drawQuickPendingLine(Canvas canvas, double min, double max) {
    if (!quickPendingLineVisible || Double.isNaN(quickPendingLinePrice) || priceRect.isEmpty()) {
        return;
    }
    float y = yFor(quickPendingLinePrice, min, max, priceRect);
    quickPendingLinePaint.setColor(0xFFEAB308);
    canvas.drawLine(priceRect.left, y, priceRect.right, y, quickPendingLinePaint);
    String text = FormatUtils.formatPrice(quickPendingLinePrice);
    float padX = dp(5f);
    float boxW = quickPendingTagTextPaint.measureText(text) + padX * 2f;
    RectF box = new RectF(priceRect.right - boxW, y - dp(9f), priceRect.right, y + dp(9f));
    canvas.drawRoundRect(box, dp(3f), dp(3f), quickPendingTagPaint);
    canvas.drawText(text, box.left + padX, box.bottom - dp(5f), quickPendingTagTextPaint);
}
```

在 `onTouchEvent()` 中优先处理拖线：

```java
if (quickPendingLineVisible && handleQuickPendingLineTouch(event)) {
    return true;
}
```

触摸逻辑：

```java
private boolean handleQuickPendingLineTouch(MotionEvent event) {
    if (priceRect.isEmpty() || Double.isNaN(quickPendingLinePrice)) {
        return false;
    }
    float targetY = yFor(quickPendingLinePrice, visiblePriceMin, visiblePriceMax, priceRect);
    switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            if (Math.abs(event.getY() - targetY) > dp(18f) || event.getX() < priceRect.left || event.getX() > priceRect.right) {
                return false;
            }
            quickPendingLineDragging = true;
            requestDisallow(true);
            return true;
        case MotionEvent.ACTION_MOVE:
            if (!quickPendingLineDragging) {
                return false;
            }
            float clampedY = clamp(event.getY(), priceRect.top, priceRect.bottom);
            quickPendingLinePrice = valueForY(clampedY, visiblePriceMin, visiblePriceMax, priceRect);
            if (onQuickPendingLineChangeListener != null) {
                onQuickPendingLineChangeListener.onPriceChanged(quickPendingLinePrice);
            }
            requestFrame();
            return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            if (!quickPendingLineDragging) {
                return false;
            }
            quickPendingLineDragging = false;
            requestDisallow(false);
            return true;
        default:
            return false;
    }
}
```

- [ ] **Step 5: 回到 `MarketChartScreen`，让挂单模式进出同步控制价格线**

确保 `applyQuickTradeMode()` 和价格回调一致：

```java
binding.klineChartView.setOnQuickPendingLineChangeListener(price -> {
    pendingLinePrice = price;
    if (quickTradeMode == ChartQuickTradeMode.PENDING) {
        binding.tvChartState.setText("挂单价: $" + FormatUtils.formatPrice(price));
    }
});
```

执行挂单前走统一价格：

```java
private void executePrimaryQuickTrade() {
    String volumeText = binding.etQuickTradeVolume.getText() == null
            ? ""
            : binding.etQuickTradeVolume.getText().toString();
    try {
        if (quickTradeMode == ChartQuickTradeMode.MARKET) {
            chartQuickTradeCoordinator.executeMarketBuy(volumeText);
        } else if (quickTradeMode == ChartQuickTradeMode.PENDING) {
            chartQuickTradeCoordinator.executePendingBuy(volumeText, pendingLinePrice);
        }
    } catch (IllegalArgumentException exception) {
        Toast.makeText(activity, exception.getMessage(), Toast.LENGTH_SHORT).show();
    }
}
```

- [ ] **Step 6: 运行图表相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartQuickTradeSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartPositionSummarySourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java \
        app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java \
        app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java
git commit -m "feat: add draggable pending line to chart quick trade"
```

## Task 4: 回归验证、文档同步、编译并安装手机

**Files:**
- Modify: `CONTEXT.md`

- [ ] **Step 1: 跑本轮最小回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartQuickTradeSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradingStructureSourceTest" --tests "com.binance.monitor.ui.chart.ChartQuickTradeCoordinatorTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" --tests "com.binance.monitor.ui.trade.TradeCommandFactoryTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: 更新 `CONTEXT.md`，记录当前停点**

把当前状态更新成：

```md
## 当前正在做什么
- 正在落地交易页“交易 / 挂单”快捷模式，目标是把快捷下单收口到图表主场并删除旧快捷交易区。
- 设计稿 `docs/superpowers/specs/2026-04-16-trading-quick-actions-design.md` 已确认，实现计划 `docs/superpowers/plans/2026-04-16-trading-quick-actions-implementation.md` 已写完。
- 下一步停点是：按计划接入页面模式条、快捷交易协调层、图表拖动挂单线，然后编译并安装到手机。
```

- [ ] **Step 3: 编译 Debug 包**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: 安装到手机**

Run:

```powershell
adb install -r "E:\Github\BTCXAU_Monitoring_and_Push_APK\app\build\outputs\apk\debug\app-debug.apk"
```

Expected:

```text
Performing Streamed Install
Success
```

- [ ] **Step 5: Commit**

```bash
git add CONTEXT.md
git commit -m "chore: sync trading quick action context"
```

## Self-Review

### Spec coverage

- 顶部新增 `交易 / 挂单` 按钮并互斥展开：Task 1 + Task 2
- 快捷条位于按钮行下方、周期行上方：Task 1
- 市价买入 / 卖出直接执行：Task 2
- 挂单拖线与自动 `Limit / Stop` 判断：Task 2 + Task 3
- 删除旧快捷交易模块和旧成交量 / 成交额摘要：Task 1
- 保持复用现有执行链、不做第二套提交流程：Task 2
- 编译并安装手机：Task 4

### Placeholder scan

- 未使用 `TODO / TBD / implement later`
- 每个任务都包含文件路径、代码片段、运行命令和预期结果
- 提交点明确到文件和 commit message

### Type consistency

- 快捷模式统一命名为 `ChartQuickTradeMode`
- 执行协调器统一命名为 `ChartQuickTradeCoordinator`
- 快捷挂单线统一命名为 `QuickPendingLine`
- 页面按钮 id 统一使用 `btnChartMode* / btnQuickTrade*`
