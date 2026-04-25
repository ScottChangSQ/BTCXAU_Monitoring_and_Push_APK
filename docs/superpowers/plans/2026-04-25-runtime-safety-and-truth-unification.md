# Runtime Safety And Truth Unification Implementation Plan

**Execution Status:** 已执行完成。当前代码已完成前台服务闭环、网关地址真值收口、图表初始化顺序修复、账户缓存清理边界修复和分析页销毁链修复；fresh 验证已覆盖相关源码测试与 `:app:assembleDebug`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复当前最危险的运行链问题，并把“网关地址 / 会话激活 / 页面销毁监听 / 图表初始化”这几条主链真值先收口，保证后续架构拆分不会建立在错误状态上。

**Architecture:** 不先大拆架构，先补当前运行闭环。第一阶段只改前台服务、页面初始化、缓存清理、会话与配置真值这几条高风险主链；不碰 UI 重设计，不引入新业务分支。所有改动优先通过源码约束测试和定向单测锁边界，再做最小实现。

**Tech Stack:** Java, Android Service/Activity/Fragment, OkHttp, SharedPreferences, JUnit4, Gradle

---

## File Map

**核心修改文件**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorServiceController.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/util/NotificationHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/ConfigManager.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`

**新增测试文件**
- Create: `app/src/test/java/com/binance/monitor/service/MonitorServiceForegroundSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/data/local/ConfigManagerGatewaySourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/runtime/account/AccountStatsPreloadManagerSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPageRuntimeSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartIntentOrderSourceTest.java`

---

### Task 1: 锁定前台服务闭环

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorServiceController.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/util/NotificationHelper.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceForegroundSourceTest.java`

- [ ] **Step 1: 写失败的源码约束测试**

```java
package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class MonitorServiceForegroundSourceTest {

    private String read(String relativePath) throws Exception {
        return Files.readString(Paths.get(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    public void monitorServiceControllerShouldUseForegroundServiceStart() throws Exception {
        String source = read("src/main/java/com/binance/monitor/service/MonitorServiceController.java");
        assertTrue(source.contains("ContextCompat.startForegroundService("));
    }

    @Test
    public void monitorServiceShouldCallStartForeground() throws Exception {
        String source = read("src/main/java/com/binance/monitor/service/MonitorService.java");
        assertTrue(source.contains("startForeground("));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceForegroundSourceTest"`
Expected: FAIL，提示未找到 `ContextCompat.startForegroundService(` 或 `startForeground(`

- [ ] **Step 3: 最小实现前台服务启动链**

```java
// MonitorServiceController.java
import androidx.core.content.ContextCompat;

ContextCompat.startForegroundService(appContext, intent);
```

```java
// MonitorService.java
private void ensureStartedAsForeground() {
    Notification notification = notificationHelper.buildServiceNotification(
            getCurrentConnectionStatus(),
            configManager != null && configManager.isMonitoringEnabled()
    );
    startForeground(AppConstants.SERVICE_NOTIFICATION_ID, notification);
}
```

```java
// MonitorForegroundNotificationCoordinator.java
interface Host {
    void startAsForeground(@NonNull String connectionState, boolean monitoringEnabled);
    void stopForegroundState();
}
```

- [ ] **Step 4: 再跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceForegroundSourceTest"`
Expected: PASS

- [ ] **Step 5: 跑一轮定向构建验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/binance/monitor/service/MonitorServiceController.java app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java app/src/main/java/com/binance/monitor/util/NotificationHelper.java app/src/test/java/com/binance/monitor/service/MonitorServiceForegroundSourceTest.java
git commit -m "fix: restore real foreground service startup chain"
```

### Task 2: 收口网关地址真值

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/data/local/ConfigManager.java`
- Test: `app/src/test/java/com/binance/monitor/data/local/ConfigManagerGatewaySourceTest.java`

- [ ] **Step 1: 写失败的源码约束测试**

```java
package com.binance.monitor.data.local;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class ConfigManagerGatewaySourceTest {

    @Test
    public void gatewayGetterShouldNotForceDefaultValue() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/data/local/ConfigManager.java"),
                StandardCharsets.UTF_8
        );
        assertFalse(source.contains("return AppConstants.MT5_GATEWAY_BASE_URL;"));
        assertTrue(source.contains("return preferences.getString(KEY_MT5_GATEWAY_URL"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.local.ConfigManagerGatewaySourceTest"`
Expected: FAIL，提示 getter 仍强制返回默认值

- [ ] **Step 3: 最小实现配置真值**

```java
public String getMt5GatewayBaseUrl() {
    return preferences.getString(KEY_MT5_GATEWAY_URL, AppConstants.MT5_GATEWAY_BASE_URL);
}

public void setMt5GatewayBaseUrl(String baseUrl) {
    String value = baseUrl == null || baseUrl.trim().isEmpty()
            ? AppConstants.MT5_GATEWAY_BASE_URL
            : baseUrl.trim();
    preferences.edit().putString(KEY_MT5_GATEWAY_URL, value).apply();
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.local.ConfigManagerGatewaySourceTest"`
Expected: PASS

- [ ] **Step 5: 回归配置相关测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.local.ConfigManagerSourceTest" --tests "com.binance.monitor.ui.settings.SettingsSectionActivitySourceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/binance/monitor/data/local/ConfigManager.java app/src/test/java/com/binance/monitor/data/local/ConfigManagerGatewaySourceTest.java
git commit -m "fix: make gateway base url follow stored config"
```

### Task 3: 修图表初始化与分析页销毁链

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartIntentOrderSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/account/AccountStatsPreloadManagerSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPageRuntimeSourceTest.java`

- [ ] **Step 1: 写图表初始化顺序测试**

```java
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class MarketChartIntentOrderSourceTest {

    @Test
    public void fragmentShouldAttachRuntimeBeforeConsumingIntent() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(source.indexOf("screen.attachPageRuntime(pageRuntime);")
                < source.indexOf("screen.onNewIntent(requireActivity().getIntent());"));
    }
}
```

- [ ] **Step 2: 写缓存清理与销毁链测试**

```java
package com.binance.monitor.runtime.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class AccountStatsPreloadManagerSourceTest {

    @Test
    public void preloadManagerShouldNotClearAllStorageWhenIdentityMissing() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java"),
                StandardCharsets.UTF_8
        );
        assertFalse(source.contains("clearStoredSnapshotForIdentity(\"\", \"\")"));
    }
}
```

```java
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class AccountStatsPageRuntimeSourceTest {

    @Test
    public void destroyPathShouldDetachForegroundRefresh() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(source.contains("host.detachForegroundRefresh();"));
    }
}
```

- [ ] **Step 3: 运行三组测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartIntentOrderSourceTest" --tests "com.binance.monitor.runtime.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsPageRuntimeSourceTest"`
Expected: FAIL

- [ ] **Step 4: 最小实现三条主链修复**

```java
// MarketChartFragment.java
screen.initialize();
pageRuntime = new MarketChartPageRuntime(...);
screen.attachPageRuntime(pageRuntime);
screen.onNewIntent(requireActivity().getIntent());
```

```java
// AccountStatsPreloadManager.java
private void clearStoredSnapshotForResolvedIdentity() {
    String[] identity = resolveStorageIdentity();
    if (identity == null) {
        return;
    }
    clearStoredSnapshotForIdentity(identity[0], identity[1]);
}
```

```java
// AccountStatsPageRuntime.java
public void onPageDestroyed() {
    disableSnapshotLoop();
    clearScheduledRefresh();
    host.dismissActiveLoginDialog();
    host.clearDestroyCallbacks();
    host.detachForegroundRefresh();
    host.shutdownExecutors();
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartIntentOrderSourceTest" --tests "com.binance.monitor.runtime.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsPageRuntimeSourceTest"`
Expected: PASS

- [ ] **Step 6: 跑核心回归测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsFragmentSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsPageRuntimeSourceTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartIntentOrderSourceTest.java app/src/test/java/com/binance/monitor/runtime/account/AccountStatsPreloadManagerSourceTest.java app/src/test/java/com/binance/monitor/ui/account/AccountStatsPageRuntimeSourceTest.java
git commit -m "fix: close runtime truth and lifecycle gaps"
```

## Self-Review

- 覆盖检查：本计划覆盖了前台服务闭环、网关地址真值、图表初始化顺序、identity 失败全量清缓存、分析页销毁链监听遗留这几条最高风险运行问题。
- 占位检查：无 `TODO/TBD`；每个任务都给了测试、实现、命令和期望结果。
- 一致性检查：统一使用 `MonitorServiceForegroundSourceTest`、`ConfigManagerGatewaySourceTest`、`AccountStatsPreloadManagerSourceTest`、`AccountStatsPageRuntimeSourceTest`、`MarketChartIntentOrderSourceTest` 作为计划内测试名。

Plan complete and saved to `docs/superpowers/plans/2026-04-25-runtime-safety-and-truth-unification.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
