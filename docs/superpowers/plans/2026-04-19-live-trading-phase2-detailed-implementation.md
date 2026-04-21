# Live Trading Phase 2 Detailed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐第二阶段真正缺失的四块能力：风险预演、统一拒单翻译、图表交易状态分层、快速模式边界，并完成真机闭环验收。

**Architecture:** 保持第一阶段 `check -> confirm -> submit -> sync` 主链不变，不新增第二套交易提交逻辑。确认层新增“风险预演 + 快速模式策略”，执行层新增统一拒单翻译，图表层把当前快捷挂单线升级成独立的交易状态层，而不是继续混在 `PriceAnnotation` 里。所有快捷交易、旧交易弹窗、图表拖线都继续落到 `TradeExecutionCoordinator` 同一条执行链。

**Tech Stack:** Android Java、JUnit4、Source Test、Gradle `testDebugUnitTest`、`assembleDebug`、ADB 真机安装

---

## File Map

### Existing files to modify

- `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
  现有统一确认入口；本轮要从“固定一句话”升级成“动作摘要 + 风险摘要 + 快速模式决策”。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
  现有检查、提交、刷新闭环；本轮要接入统一拒单翻译，并把确认阶段的用户可读信息收口到统一入口。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
  图表页交易入口协调器；本轮要继续复用新确认内容和翻译结果，不单独拼第二套提示。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  图表页装配层；本轮要把快捷挂单线升级成草稿态输入，并接入图表交易状态层。
- `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
  图表主 View；本轮要新增“真实线 / 草稿线 / 选中态 / 拖拽态 / 提交中 / 回退态”相关绘制入口。
- `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java`
  快捷交易命令生成器；本轮要补快捷入口来源标记，并复用快速模式边界。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeCommandFactory.java`
  命令工厂；本轮要允许给快捷交易打来源标记，不改协议字段。
- `app/src/main/res/values/strings.xml`
  本轮补风险预演、拒单翻译、快速模式提示文案。
- `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`
  当前交易执行主链核心单测；本轮继续补预演、翻译、快速模式边界断言。
- `app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java`
  当前快捷交易单测；本轮补来源标记和策略分流断言。
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`
  当前图表交易源码结构测试；本轮补图表交易状态层和统一提示入口断言。
- `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`
  当前图表源码测试；本轮补交易状态层、草稿线和拖拽态入口断言。

### New files to create

- `app/src/main/java/com/binance/monitor/ui/trade/TradeRiskPreview.java`
  风险预演值对象，承载手数、保证金、可用保证金变化、止损金额、止盈金额和动作摘要。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeRiskPreviewResolver.java`
  从 `TradeCommand + TradeCheckResult` 的原始字段里提取风险预演，不改服务端协议。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeRejectMessageMapper.java`
  把 `ExecutionError.code / message / details` 翻译成统一中文提示。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeQuickModePolicy.java`
  定义哪些动作允许免确认，哪些动作必须强制确认。
- `app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLineState.java`
  图表交易线状态枚举：真实线、草稿线、选中态、拖拽态、提交中、回退态。
- `app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLine.java`
  图表交易线模型，专门承载 position / pending / tp / sl / draft 这类活动交易线。
- `app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLayerSnapshot.java`
  图表交易状态层快照，集中把真实线与草稿线交给 `KlineChartView`。
- `app/src/test/java/com/binance/monitor/ui/trade/TradeRiskPreviewResolverTest.java`
  风险预演解析单测。
- `app/src/test/java/com/binance/monitor/ui/trade/TradeRejectMessageMapperTest.java`
  拒单翻译单测。
- `app/src/test/java/com/binance/monitor/ui/trade/TradeQuickModePolicyTest.java`
  快速模式边界单测。
- `app/src/test/java/com/binance/monitor/ui/chart/ChartTradeLayerSnapshotTest.java`
  图表交易状态快照单测。

### Existing docs to modify

- `CONTEXT.md`
  记录第二阶段详细实施计划已写完、当前停点和后续执行顺序。

---

### Task 1: 补风险预演模型与统一确认内容

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeRiskPreview.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeRiskPreviewResolver.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradeRiskPreviewResolverTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`

- [ ] **Step 1: 写失败单测，锁定从 `TradeCheckResult.check` 里提取风险预演**

```java
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.json.JSONObject;
import org.junit.Test;

public class TradeRiskPreviewResolverTest {

    @Test
    public void shouldBuildRiskPreviewFromExecutableCheckPayload() throws Exception {
        JSONObject check = new JSONObject();
        check.put("margin", 120.5d);
        check.put("freeMargin", 880.0d);
        check.put("freeMarginAfter", 759.5d);
        check.put("stopLossAmount", 45.0d);
        check.put("takeProfitAmount", 80.0d);

        TradeCheckResult result = new TradeCheckResult(
                "req-risk",
                "OPEN_MARKET",
                "hedging",
                "EXECUTABLE",
                null,
                check,
                1713500000L
        );
        TradeCommand command = TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0.05d, 65000d, 64800d, 65400d);

        TradeRiskPreview preview = TradeRiskPreviewResolver.resolve(command, result);

        assertEquals("买入 BTCUSD 0.05 手", preview.getActionSummary());
        assertEquals("$120.50", preview.getMarginText());
        assertEquals("$759.50", preview.getFreeMarginAfterText());
        assertEquals("$45.00", preview.getStopLossAmountText());
        assertEquals("$80.00", preview.getTakeProfitAmountText());
        assertTrue(preview.hasAnyRiskMetric());
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeRiskPreviewResolverTest" -v
```

Expected:

```text
TradeRiskPreviewResolverTest > shouldBuildRiskPreviewFromExecutableCheckPayload FAILED
```

- [ ] **Step 3: 新建风险预演值对象和解析器**

`TradeRiskPreview.java`

```java
package com.binance.monitor.ui.trade;

public class TradeRiskPreview {
    private final String actionSummary;
    private final String marginText;
    private final String freeMarginAfterText;
    private final String stopLossAmountText;
    private final String takeProfitAmountText;

    public TradeRiskPreview(String actionSummary,
                            String marginText,
                            String freeMarginAfterText,
                            String stopLossAmountText,
                            String takeProfitAmountText) {
        this.actionSummary = actionSummary == null ? "" : actionSummary;
        this.marginText = marginText == null ? "" : marginText;
        this.freeMarginAfterText = freeMarginAfterText == null ? "" : freeMarginAfterText;
        this.stopLossAmountText = stopLossAmountText == null ? "" : stopLossAmountText;
        this.takeProfitAmountText = takeProfitAmountText == null ? "" : takeProfitAmountText;
    }

    public String getActionSummary() { return actionSummary; }
    public String getMarginText() { return marginText; }
    public String getFreeMarginAfterText() { return freeMarginAfterText; }
    public String getStopLossAmountText() { return stopLossAmountText; }
    public String getTakeProfitAmountText() { return takeProfitAmountText; }

    public boolean hasAnyRiskMetric() {
        return !marginText.isEmpty()
                || !freeMarginAfterText.isEmpty()
                || !stopLossAmountText.isEmpty()
                || !takeProfitAmountText.isEmpty();
    }
}
```

`TradeRiskPreviewResolver.java`

```java
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.util.FormatUtils;

import org.json.JSONObject;

public final class TradeRiskPreviewResolver {

    private TradeRiskPreviewResolver() {
    }

    @NonNull
    public static TradeRiskPreview resolve(@Nullable TradeCommand command, @Nullable TradeCheckResult checkResult) {
        JSONObject check = checkResult == null ? new JSONObject() : checkResult.getCheck();
        return new TradeRiskPreview(
                TradeCommandFactory.describe(command),
                formatMoney(check.optDouble("margin", 0d)),
                formatMoney(check.optDouble("freeMarginAfter",
                        check.optDouble("freeMargin", 0d))),
                formatMoney(check.optDouble("stopLossAmount", 0d)),
                formatMoney(check.optDouble("takeProfitAmount", 0d))
        );
    }

    @NonNull
    private static String formatMoney(double value) {
        if (!Double.isFinite(value) || value <= 0d) {
            return "";
        }
        return "$" + FormatUtils.formatPrice(value);
    }
}
```

- [ ] **Step 4: 把确认决策从单句提示升级成“动作摘要 + 风险摘要”**

在 `TradeConfirmDialogController.java` 中把 `Decision` 扩成可承载预演结果：

```java
public Decision buildDecision(TradeCommand command, @Nullable TradeCheckResult checkResult) {
    if (command == null) {
        return new Decision(true, false, "交易命令无效", null);
    }
    if (checkResult == null) {
        return new Decision(true, false, "检查结果缺失，请重新确认", null);
    }
    TradeRiskPreview preview = TradeRiskPreviewResolver.resolve(command, checkResult);
    StringBuilder builder = new StringBuilder();
    builder.append(preview.getActionSummary());
    if (preview.hasAnyRiskMetric()) {
        builder.append("\n\n风险摘要");
        if (!preview.getMarginText().isEmpty()) {
            builder.append("\n预计保证金：").append(preview.getMarginText());
        }
        if (!preview.getFreeMarginAfterText().isEmpty()) {
            builder.append("\n剩余可用保证金：").append(preview.getFreeMarginAfterText());
        }
        if (!preview.getStopLossAmountText().isEmpty()) {
            builder.append("\n止损金额：").append(preview.getStopLossAmountText());
        }
        if (!preview.getTakeProfitAmountText().isEmpty()) {
            builder.append("\n止盈金额：").append(preview.getTakeProfitAmountText());
        }
    }
    return new Decision(true, false, builder.toString(), preview);
}
```

同时把 `Decision` 构造函数改成：

```java
public Decision(boolean confirmationRequired,
                boolean oneClickTradingEnabled,
                String message,
                @Nullable TradeRiskPreview riskPreview) {
    this.confirmationRequired = confirmationRequired;
    this.oneClickTradingEnabled = oneClickTradingEnabled;
    this.message = message == null ? "" : message;
    this.riskPreview = riskPreview;
}
```

- [ ] **Step 5: 在执行链测试里锁定确认内容已升级**

在 `TradeExecutionCoordinatorTest.java` 的默认确认测试里补断言：

```java
assertTrue(prepared.getMessage().contains("买入 BTCUSD"));
assertTrue(prepared.getMessage().contains("风险摘要"));
```

- [ ] **Step 6: 运行通过风险预演相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeRiskPreviewResolverTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/trade/TradeRiskPreview.java \
        app/src/main/java/com/binance/monitor/ui/trade/TradeRiskPreviewResolver.java \
        app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeRiskPreviewResolverTest.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java
git commit -m "feat: add trade risk preview to confirmation flow"
```

### Task 2: 建统一拒单翻译层并接入执行结果

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeRejectMessageMapper.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradeRejectMessageMapperTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`

- [ ] **Step 1: 写失败单测，锁定错误码翻译合同**

```java
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.v2.trade.ExecutionError;

import org.junit.Test;

public class TradeRejectMessageMapperTest {

    @Test
    public void shouldTranslateKnownTradeCodes() {
        assertEquals("保证金不足，请降低手数或释放仓位后重试",
                TradeRejectMessageMapper.toUserMessage(ExecutionError.of("TRADE_INSUFFICIENT_MARGIN", "margin not enough")));
        assertEquals("当前市场暂不可交易，请稍后重试",
                TradeRejectMessageMapper.toUserMessage(ExecutionError.of("TRADE_MARKET_CLOSED", "market closed")));
        assertEquals("服务器报价已变化，请重新确认价格后再提交",
                TradeRejectMessageMapper.toUserMessage(ExecutionError.of("TRADE_REQUOTE", "requote")));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeRejectMessageMapperTest" -v
```

Expected:

```text
TradeRejectMessageMapperTest > shouldTranslateKnownTradeCodes FAILED
```

- [ ] **Step 3: 实现统一拒单翻译器**

`TradeRejectMessageMapper.java`

```java
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.ExecutionError;

public final class TradeRejectMessageMapper {

    private TradeRejectMessageMapper() {
    }

    @NonNull
    public static String toUserMessage(@Nullable ExecutionError error) {
        if (error == null) {
            return "交易被拒绝，请稍后重试";
        }
        String code = error.getCode() == null ? "" : error.getCode().trim();
        if ("TRADE_INSUFFICIENT_MARGIN".equals(code)) {
            return "保证金不足，请降低手数或释放仓位后重试";
        }
        if ("TRADE_MARKET_CLOSED".equals(code)) {
            return "当前市场暂不可交易，请稍后重试";
        }
        if ("TRADE_REQUOTE".equals(code)) {
            return "服务器报价已变化，请重新确认价格后再提交";
        }
        if ("TRADE_TIMEOUT".equals(code) || "TRADE_RESULT_UNKNOWN".equals(code)) {
            return "结果暂未确认，请等待账户同步后再判断";
        }
        String message = error.getMessage() == null ? "" : error.getMessage().trim();
        return message.isEmpty() ? "交易被拒绝，请稍后重试" : message;
    }
}
```

- [ ] **Step 4: 在 `TradeExecutionCoordinator` 里统一改用翻译后的消息**

把拒单与超时分支统一改成：

```java
if (stateMachine.getStep() == TradeCommandStateMachine.Step.REJECTED) {
    return new ExecutionResult(
            UiState.REJECTED,
            stateMachine,
            null,
            false,
            true,
            TradeRejectMessageMapper.toUserMessage(stateMachine.getError())
    );
}
if (stateMachine.getStep() == TradeCommandStateMachine.Step.TIMEOUT) {
    return new ExecutionResult(
            UiState.RESULT_UNCONFIRMED,
            stateMachine,
            null,
            false,
            false,
            TradeRejectMessageMapper.toUserMessage(stateMachine.getError())
    );
}
```

并把 `resolveCheckFailureMessage(...)` 收口成：

```java
private String resolveCheckFailureMessage(TradeCommandStateMachine stateMachine) {
    if (stateMachine != null && stateMachine.getStep() == TradeCommandStateMachine.Step.TIMEOUT) {
        return TradeRejectMessageMapper.toUserMessage(stateMachine.getError());
    }
    return TradeRejectMessageMapper.toUserMessage(stateMachine == null ? null : stateMachine.getError());
}
```

- [ ] **Step 5: 在执行链单测里补明确拒单与结果未确认区分**

向 `TradeExecutionCoordinatorTest.java` 增加断言：

```java
assertEquals("保证金不足，请降低手数或释放仓位后重试", result.getMessage());
```

以及：

```java
assertEquals("结果暂未确认，请等待账户同步后再判断", result.getMessage());
```

- [ ] **Step 6: 运行翻译与执行链回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeRejectMessageMapperTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/trade/TradeRejectMessageMapper.java \
        app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeRejectMessageMapperTest.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java
git commit -m "feat: add canonical trade reject message mapper"
```

### Task 3: 把图表快捷挂单线升级为正式交易状态层

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLineState.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLine.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLayerSnapshot.java`
- Create: `app/src/test/java/com/binance/monitor/ui/chart/ChartTradeLayerSnapshotTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`

- [ ] **Step 1: 写失败单测，锁定交易状态层快照结构**

```java
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class ChartTradeLayerSnapshotTest {

    @Test
    public void snapshotShouldSeparateLiveLinesAndDraftLines() {
        ChartTradeLine liveLine = new ChartTradeLine("pos-1", 65000d, "持仓", ChartTradeLineState.LIVE_POSITION);
        ChartTradeLine draftLine = new ChartTradeLine("draft-1", 65100d, "草稿挂单", ChartTradeLineState.DRAFT_PENDING);

        ChartTradeLayerSnapshot snapshot = new ChartTradeLayerSnapshot(
                Arrays.asList(liveLine),
                Arrays.asList(draftLine)
        );

        assertEquals(1, snapshot.getLiveLines().size());
        assertEquals(1, snapshot.getDraftLines().size());
        assertEquals(ChartTradeLineState.DRAFT_PENDING, snapshot.getDraftLines().get(0).getState());
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartTradeLayerSnapshotTest" -v
```

Expected:

```text
ChartTradeLayerSnapshotTest > snapshotShouldSeparateLiveLinesAndDraftLines FAILED
```

- [ ] **Step 3: 新建交易状态层模型**

`ChartTradeLineState.java`

```java
package com.binance.monitor.ui.chart;

enum ChartTradeLineState {
    LIVE_POSITION,
    LIVE_PENDING,
    LIVE_TP,
    LIVE_SL,
    DRAFT_PENDING,
    SELECTED,
    DRAGGING,
    SUBMITTING,
    REJECTED_ROLLBACK
}
```

`ChartTradeLine.java`

```java
package com.binance.monitor.ui.chart;

final class ChartTradeLine {
    private final String id;
    private final double price;
    private final String label;
    private final ChartTradeLineState state;

    ChartTradeLine(String id, double price, String label, ChartTradeLineState state) {
        this.id = id == null ? "" : id;
        this.price = price;
        this.label = label == null ? "" : label;
        this.state = state == null ? ChartTradeLineState.LIVE_PENDING : state;
    }

    String getId() { return id; }
    double getPrice() { return price; }
    String getLabel() { return label; }
    ChartTradeLineState getState() { return state; }
}
```

`ChartTradeLayerSnapshot.java`

```java
package com.binance.monitor.ui.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ChartTradeLayerSnapshot {
    private final List<ChartTradeLine> liveLines;
    private final List<ChartTradeLine> draftLines;

    ChartTradeLayerSnapshot(List<ChartTradeLine> liveLines, List<ChartTradeLine> draftLines) {
        this.liveLines = liveLines == null ? new ArrayList<>() : new ArrayList<>(liveLines);
        this.draftLines = draftLines == null ? new ArrayList<>() : new ArrayList<>(draftLines);
    }

    List<ChartTradeLine> getLiveLines() { return Collections.unmodifiableList(liveLines); }
    List<ChartTradeLine> getDraftLines() { return Collections.unmodifiableList(draftLines); }
}
```

- [ ] **Step 4: 在 `KlineChartView` 新增正式交易状态层入口，不再只靠 `quickPendingLine*`**

在 `KlineChartView.java` 新增字段和入口：

```java
private ChartTradeLayerSnapshot tradeLayerSnapshot = new ChartTradeLayerSnapshot(null, null);

public void setTradeLayerSnapshot(@Nullable ChartTradeLayerSnapshot snapshot) {
    tradeLayerSnapshot = snapshot == null ? new ChartTradeLayerSnapshot(null, null) : snapshot;
    invalidate();
}
```

在 `onDraw()` 中追加：

```java
drawTradeLayerSnapshot(canvas, visiblePriceMin, visiblePriceMax);
```

新增绘制入口：

```java
private void drawTradeLayerSnapshot(Canvas canvas, double min, double max) {
    for (ChartTradeLine line : tradeLayerSnapshot.getLiveLines()) {
        drawTradeLayerLine(canvas, line, min, max);
    }
    for (ChartTradeLine line : tradeLayerSnapshot.getDraftLines()) {
        drawTradeLayerLine(canvas, line, min, max);
    }
}
```

并让 `drawTradeLayerLine(...)` 按状态决定颜色和虚实线，而不是复用 `PriceAnnotation`。

- [ ] **Step 5: 在 `MarketChartScreen` 把快捷挂单线改成草稿线输入**

新增构建方法：

```java
private ChartTradeLayerSnapshot buildTradeLayerSnapshot() {
    List<ChartTradeLine> liveLines = new ArrayList<>();
    List<ChartTradeLine> draftLines = new ArrayList<>();
    if (quickTradeMode == ChartQuickTradeMode.PENDING && Double.isFinite(pendingLinePrice) && pendingLinePrice > 0d) {
        draftLines.add(new ChartTradeLine(
                "quick-pending-draft",
                pendingLinePrice,
                "草稿挂单",
                ChartTradeLineState.DRAFT_PENDING
        ));
    }
    return new ChartTradeLayerSnapshot(liveLines, draftLines);
}
```

在切换模式和拖动价格线后统一调用：

```java
binding.klineChartView.setTradeLayerSnapshot(buildTradeLayerSnapshot());
```

- [ ] **Step 6: 补源码测试，锁定不再把草稿态继续当 `PriceAnnotation` 混用**

在 `KlineChartViewSourceTest.java` 里补：

```java
assertTrue(source.contains("private ChartTradeLayerSnapshot tradeLayerSnapshot"));
assertTrue(source.contains("public void setTradeLayerSnapshot("));
assertTrue(source.contains("drawTradeLayerSnapshot(canvas"));
```

在 `MarketChartTradeSourceTest.java` 里补：

```java
assertTrue(screenSource.contains("binding.klineChartView.setTradeLayerSnapshot(buildTradeLayerSnapshot());"));
assertFalse(screenSource.contains("showQuickPendingLine(pendingLinePrice);"));
```

- [ ] **Step 7: 运行图表状态层测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartTradeLayerSnapshotTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLineState.java \
        app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLine.java \
        app/src/main/java/com/binance/monitor/ui/chart/ChartTradeLayerSnapshot.java \
        app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java \
        app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java \
        app/src/test/java/com/binance/monitor/ui/chart/ChartTradeLayerSnapshotTest.java \
        app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java \
        app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java
git commit -m "feat: add chart trade layer state snapshot"
```

### Task 4: 补快速模式边界并让快捷交易走白名单策略

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeQuickModePolicy.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradeQuickModePolicyTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeCommandFactory.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java`

- [ ] **Step 1: 写失败单测，锁定快速模式白名单**

```java
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.junit.Test;

public class TradeQuickModePolicyTest {

    @Test
    public void onlySmallQuickMarketOrdersShouldSkipConfirmation() {
        TradeCommand quickMarket = TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0.05d, 65000d, 0d, 0d);
        TradeCommand largeQuickMarket = TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 1.50d, 65000d, 0d, 0d);

        assertTrue(TradeQuickModePolicy.shouldAllowQuickMode(quickMarket, true));
        assertFalse(TradeQuickModePolicy.shouldAllowQuickMode(largeQuickMarket, true));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeQuickModePolicyTest" -v
```

Expected:

```text
TradeQuickModePolicyTest > onlySmallQuickMarketOrdersShouldSkipConfirmation FAILED
```

- [ ] **Step 3: 实现快速模式策略**

`TradeQuickModePolicy.java`

```java
package com.binance.monitor.ui.trade;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.TradeCommand;

public final class TradeQuickModePolicy {
    private static final double MAX_ONE_CLICK_VOLUME = 0.10d;

    private TradeQuickModePolicy() {
    }

    public static boolean shouldAllowQuickMode(@Nullable TradeCommand command, boolean quickModeEnabled) {
        if (!quickModeEnabled || command == null) {
            return false;
        }
        if (!"OPEN_MARKET".equalsIgnoreCase(command.getAction())) {
            return false;
        }
        return command.getVolume() > 0d && command.getVolume() <= MAX_ONE_CLICK_VOLUME;
    }
}
```

- [ ] **Step 4: 让快捷交易命令带来源标记，并让确认控制器按策略放行**

在 `TradeCommandFactory.java` 新增：

```java
public static TradeCommand withEntryMode(@NonNull TradeCommand command, @NonNull String entryMode) {
    JSONObject params = command.getParams();
    putQuietly(params, "entryMode", entryMode);
    return new TradeCommand(
            command.getRequestId(),
            command.getAccountId(),
            command.getSymbol(),
            command.getAction(),
            command.getVolume(),
            command.getPrice(),
            command.getSl(),
            command.getTp(),
            params
    );
}
```

在 `ChartQuickTradeCoordinator.java` 把执行改成：

```java
executor.execute(TradeCommandFactory.withEntryMode(
        TradeCommandFactory.openMarket(
                requireAccountId(),
                requireSymbol(),
                "buy",
                requireVolume(volumeText),
                requireCurrentPrice(),
                0d,
                0d
        ),
        "quick"
));
```

在 `TradeConfirmDialogController.java` 中改成：

```java
boolean quickModeEnabled = "quick".equalsIgnoreCase(command.getParams().optString("entryMode", ""));
boolean allowOneClick = TradeQuickModePolicy.shouldAllowQuickMode(command, quickModeEnabled);
return new Decision(!allowOneClick, allowOneClick, builder.toString(), preview);
```

- [ ] **Step 5: 在快捷交易单测里锁定来源标记**

在 `ChartQuickTradeCoordinatorTest.java` 追加：

```java
assertEquals("quick", command.getParams().optString("entryMode", ""));
```

- [ ] **Step 6: 运行快速模式相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeQuickModePolicyTest" --tests "com.binance.monitor.ui.chart.ChartQuickTradeCoordinatorTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/trade/TradeQuickModePolicy.java \
        app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java \
        app/src/main/java/com/binance/monitor/ui/trade/TradeCommandFactory.java \
        app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeQuickModePolicyTest.java \
        app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java
git commit -m "feat: add quick trade confirmation boundary"
```

### Task 5: 第二阶段总验收与文档同步

**Files:**
- Modify: `CONTEXT.md`

- [ ] **Step 1: 跑第二阶段最小总回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeRiskPreviewResolverTest" --tests "com.binance.monitor.ui.trade.TradeRejectMessageMapperTest" --tests "com.binance.monitor.ui.trade.TradeQuickModePolicyTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.ui.chart.ChartQuickTradeCoordinatorTest" --tests "com.binance.monitor.ui.chart.ChartTradeLayerSnapshotTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: 编译 Debug 包**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 安装到真机**

Run:

```powershell
adb -s 7fab54c4 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 7fab54c4 shell am start -W com.binance.monitor/.ui.main.MainActivity
```

Expected:

```text
Performing Streamed Install
Success
```

- [ ] **Step 4: 真机验收 checklist**

按下面顺序人工验证：

```text
1. 市价买入：确认弹窗出现动作摘要 + 风险摘要
2. 市价卖出：小手数快捷模式可直接提交，大手数仍要求确认
3. 挂单买入 / 卖出：图表显示草稿线，拖动后能更新价格
4. 拒单：保证金不足时显示统一中文，而不是底层原文
5. 已受理待同步：标题为“交易已受理”，不误报成失败
6. 收敛完成：账户页、图表页、挂单线、持仓线保持一致
```

- [ ] **Step 5: 更新 `CONTEXT.md`**

写入：

```md
## 当前正在做什么
- 第二阶段详细实施计划 `docs/superpowers/plans/2026-04-19-live-trading-phase2-detailed-implementation.md` 已执行完毕。
- 风险预演、统一拒单翻译、图表交易状态分层、快速模式边界已完成。
- 下一步若继续，才进入第三阶段复杂交易，不再回头补第二阶段主链能力。
```

- [ ] **Step 6: Commit**

```bash
git add CONTEXT.md
git commit -m "chore: sync phase2 implementation context"
```

## Self-Review

### Spec coverage

- 风险预演与确认内容升级：Task 1
- 统一拒单翻译：Task 2
- 图表交易状态分层：Task 3
- 快速模式边界：Task 4
- 第二阶段总验收与文档同步：Task 5

### Placeholder scan

- 计划中未使用 `TODO / TBD / implement later`
- 每个任务都给出文件、测试、命令和代码片段
- 每个阶段都包含独立 commit 点

### Type consistency

- 风险预演统一使用 `TradeRiskPreview / TradeRiskPreviewResolver`
- 拒单翻译统一使用 `TradeRejectMessageMapper`
- 快速模式边界统一使用 `TradeQuickModePolicy`
- 图表状态层统一使用 `ChartTradeLineState / ChartTradeLine / ChartTradeLayerSnapshot`

