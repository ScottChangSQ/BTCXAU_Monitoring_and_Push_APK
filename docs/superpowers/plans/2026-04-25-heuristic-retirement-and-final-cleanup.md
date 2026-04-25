# Heuristic Retirement And Final Cleanup Implementation Plan

**Execution Status:** 已执行完成。当前代码已移除 MT5 网关字符串探测与旧 snapshot 回退链、交易动作 `contains` 分类、计划边界内的吞异常点，并已标记旧 `PositionAdapter` 为 `@Deprecated`；fresh 验证已覆盖相关源码测试与 `:app:assembleDebug`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 逐步移除项目里最容易制造“看起来稳定、实际不可验证”的启发式、fallback、吞异常和残留入口，并在主链稳定后做最后一轮安全清理。

**Architecture:** 这份计划必须排在运行安全和真值收口之后执行。它不先删大块代码，而是先把启发式逻辑替换成正式规则，再回收旧桥接、旧适配器、旧资源和误导性入口，避免误删现行主链。

**Tech Stack:** Java, Android, JUnit4, XML resources, Gradle

---

## File Map

**核心修改文件**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`

**新增测试文件**
- Create: `app/src/test/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClientSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/service/MonitorServiceParsingSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowManagerSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/main/MainActivityExportSourceTest.java`

---

### Task 1: 替换 MT5 网关探测启发式

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClientSourceTest.java`

- [ ] **Step 1: 写失败的源码约束测试**

```java
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class Mt5BridgeGatewayClientSourceTest {

    @Test
    public void mt5BridgeGatewayClientShouldNotUseStringProbeHeuristics() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java"),
                StandardCharsets.UTF_8
        );
        assertFalse(source.contains("body.contains("));
        assertFalse(source.contains("fallbackToSnapshot"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.Mt5BridgeGatewayClientSourceTest"`
Expected: FAIL

- [ ] **Step 3: 最小替换方向**

```java
private boolean isGatewayHealthy(@NonNull GatewayHealthPayload payload) {
    return payload.isOk() && payload.getApiVersion() >= 2;
}
```

```java
private String resolveConfiguredGatewayBaseUrl() {
    String configured = configManager == null ? "" : configManager.getMt5GatewayBaseUrl();
    return configured == null ? "" : configured.trim();
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.Mt5BridgeGatewayClientSourceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java app/src/test/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClientSourceTest.java
git commit -m "refactor: retire heuristic mt5 gateway probing"
```

### Task 2: 替换交易协调器字符串判定

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorSourceTest.java`

- [ ] **Step 1: 写失败的源码约束测试**

```java
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class TradeExecutionCoordinatorSourceTest {

    @Test
    public void tradeExecutionCoordinatorShouldNotClassifyActionsByContains() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java"),
                StandardCharsets.UTF_8
        );
        assertFalse(source.contains("action.contains("));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorSourceTest"`
Expected: FAIL

- [ ] **Step 3: 最小实现为显式枚举/分类器**

```java
enum TradeActionType {
    POSITION_CLOSE,
    POSITION_MODIFY,
    PENDING_MODIFY,
    PENDING_CANCEL,
    UNKNOWN
}
```

```java
private TradeActionType resolveActionType(@Nullable String action) {
    if ("close_position".equals(action)) return TradeActionType.POSITION_CLOSE;
    if ("modify_position".equals(action)) return TradeActionType.POSITION_MODIFY;
    if ("modify_pending".equals(action)) return TradeActionType.PENDING_MODIFY;
    if ("cancel_pending".equals(action)) return TradeActionType.PENDING_CANCEL;
    return TradeActionType.UNKNOWN;
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorSourceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorSourceTest.java
git commit -m "refactor: replace trade action heuristics with explicit types"
```

### Task 3: 去掉吞异常并回收残留入口

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapter.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceParsingSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowManagerSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/main/MainActivityExportSourceTest.java`

- [ ] **Step 1: 写吞异常测试**

```java
package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class MonitorServiceParsingSourceTest {

    @Test
    public void monitorServiceShouldNotSilentlyIgnoreAbnormalPayloadParsingErrors() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/service/MonitorService.java"),
                StandardCharsets.UTF_8
        );
        assertFalse(source.contains("catch (Exception ignored)"));
    }
}
```

```java
package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class FloatingWindowManagerSourceTest {

    @Test
    public void floatingWindowManagerShouldNotSilentlyIgnoreWindowRemovalFailure() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java"),
                StandardCharsets.UTF_8
        );
        assertFalse(source.contains("catch (Exception ignored)"));
    }
}
```

- [ ] **Step 2: 写入口残留测试**

```java
package com.binance.monitor.ui.main;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class MainActivityExportSourceTest {

    @Test
    public void mainActivityShouldStayAsThinBridgeOnlyUntilManifestIsConverged() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/main/MainActivity.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(source.contains("startActivity("));
        assertTrue(source.contains("finish();"));
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceParsingSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest"`
Expected: FAIL

- [ ] **Step 4: 最小实现为可观测日志**

```java
catch (Exception exception) {
    logManager.warn("abnormal payload parse failed: " + exception.getMessage());
}
```

```java
catch (Exception exception) {
    Log.w("FloatingWindowManager", "removeViewImmediate failed", exception);
}
```

- [ ] **Step 5: 处理残留入口与残留类**

```java
// MainActivity 保持薄桥，不增加任何业务逻辑
startActivity(HostNavigationIntentFactory.forTab(this, HostTab.MARKET_MONITOR));
finish();
```

```java
// PositionAdapter 先加 Deprecated 标记，待清理轮彻底移除
@Deprecated
public class PositionAdapter extends RecyclerView.Adapter<PositionAdapter.Holder> {
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceParsingSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest" --tests "com.binance.monitor.ui.main.MainActivityExportSourceTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java app/src/main/java/com/binance/monitor/ui/main/MainActivity.java app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapter.java app/src/test/java/com/binance/monitor/service/MonitorServiceParsingSourceTest.java app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowManagerSourceTest.java app/src/test/java/com/binance/monitor/ui/main/MainActivityExportSourceTest.java
git commit -m "refactor: make heuristic failures observable and mark legacy entry remnants"
```

## Self-Review

- 覆盖检查：本计划覆盖了 MT5 探测启发式、交易动作字符串启发式、吞异常、残留入口与残留适配器这几类最后阶段问题。
- 占位检查：没有使用“后续再实现”“类似上一步”这种占位写法。
- 一致性检查：统一用“先写源码约束测试，再做最小实现，再做构建/回归”的节奏。

Plan complete and saved to `docs/superpowers/plans/2026-04-25-heuristic-retirement-and-final-cleanup.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
