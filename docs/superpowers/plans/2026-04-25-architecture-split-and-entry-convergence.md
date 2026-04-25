# Architecture Split And Entry Convergence Implementation Plan

**Execution Status:** 已执行完成。当前代码已完成单入口桥接与 launcher alias 收口、`MonitorService` 双协调器 seam 建立，以及页面层 `ScreenDependencyProvider` 依赖装配收口；fresh 验证已覆盖相关源码测试与 `:app:assembleDebug`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有功能目标的前提下，把入口、桥接页和超大编排类拆出明确边界，让主链只保留一个真实入口、一条真实页面链和一套可测试的依赖装配方式。

**Architecture:** 这份计划不做“全量重写”，而是先建 seam，再迁职责。先锁定目标边界，再从 `MonitorService`、`MainActivity`、`AccountStatsBridgeActivity`、页面直接 new 依赖这几块入手，把“桥接入口”和“业务装配”分开。

**Tech Stack:** Java, Android Activity/Fragment/Service, JUnit4, Gradle, source tests

---

## File Map

**核心修改文件**
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/ThemeLauncherIconManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`

**计划内新增文件**
- Create: `app/src/main/java/com/binance/monitor/service/MonitorStreamCoordinator.java`
- Create: `app/src/main/java/com/binance/monitor/service/MonitorAlertCoordinator.java`
- Create: `app/src/main/java/com/binance/monitor/ui/runtime/ScreenDependencyProvider.java`
- Create: `app/src/test/java/com/binance/monitor/ui/host/HostTabNavigatorSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/main/MainActivityBridgeSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/theme/ThemeLauncherIconManagerSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/service/MonitorServiceCoordinatorSourceTest.java`

---

### Task 1: 先锁入口和 alias 的目标边界

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/ThemeLauncherIconManager.java`
- Test: `app/src/test/java/com/binance/monitor/ui/main/MainActivityBridgeSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/ThemeLauncherIconManagerSourceTest.java`

- [ ] **Step 1: 写 MainActivity 边界测试**

```java
package com.binance.monitor.ui.main;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class MainActivityBridgeSourceTest {

    @Test
    public void mainActivityShouldOnlyBridgeToHostAndFinish() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/main/MainActivity.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(source.contains("HostNavigationIntentFactory.forTab(this, HostTab.MARKET_MONITOR)"));
        assertTrue(source.contains("finish();"));
    }
}
```

- [ ] **Step 2: 写 alias 收口测试**

```java
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class ThemeLauncherIconManagerSourceTest {

    @Test
    public void themeLauncherIconManagerShouldOwnAllLauncherAliases() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/theme/ThemeLauncherIconManager.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(source.contains("IconFinancialAlias"));
        assertTrue(source.contains("IconVintageAlias"));
        assertTrue(source.contains("IconBinanceAlias"));
        assertTrue(source.contains("IconTradingViewAlias"));
        assertTrue(source.contains("IconLightAlias"));
    }
}
```

- [ ] **Step 3: 运行测试确认边界未满足**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.main.MainActivityBridgeSourceTest" --tests "com.binance.monitor.ui.theme.ThemeLauncherIconManagerSourceTest"`
Expected: `ThemeLauncherIconManagerSourceTest` FAIL

- [ ] **Step 4: 最小实现入口与 alias 收口**

```java
private static final String[] ALIAS_SUFFIXES = new String[]{
        ".launcher.IconFinancialAlias",
        ".launcher.IconVintageAlias",
        ".launcher.IconBinanceAlias",
        ".launcher.IconTradingViewAlias",
        ".launcher.IconLightAlias"
};
```

```java
private static int resolveAliasIndex(int paletteId) {
    switch (paletteId) {
        case 1: return 1;
        case 2: return 2;
        case 3: return 3;
        case 4: return 4;
        default: return 0;
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.main.MainActivityBridgeSourceTest" --tests "com.binance.monitor.ui.theme.ThemeLauncherIconManagerSourceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/main/MainActivity.java app/src/main/java/com/binance/monitor/ui/theme/ThemeLauncherIconManager.java app/src/test/java/com/binance/monitor/ui/main/MainActivityBridgeSourceTest.java app/src/test/java/com/binance/monitor/ui/theme/ThemeLauncherIconManagerSourceTest.java
git commit -m "refactor: converge launcher entry and alias ownership"
```

### Task 2: 为 MonitorService 先切出两个明确协调器

**Files:**
- Create: `app/src/main/java/com/binance/monitor/service/MonitorStreamCoordinator.java`
- Create: `app/src/main/java/com/binance/monitor/service/MonitorAlertCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceCoordinatorSourceTest.java`

- [ ] **Step 1: 写失败的协调器边界测试**

```java
package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class MonitorServiceCoordinatorSourceTest {

    @Test
    public void monitorServiceShouldDelegateToDedicatedCoordinators() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/service/MonitorService.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(source.contains("MonitorStreamCoordinator"));
        assertTrue(source.contains("MonitorAlertCoordinator"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceCoordinatorSourceTest"`
Expected: FAIL

- [ ] **Step 3: 写最小协调器骨架**

```java
package com.binance.monitor.service;

final class MonitorStreamCoordinator {

    interface Host {
        void updateConnectionStatus();
        void applyRealtimeMessage(Object message);
    }
}
```

```java
package com.binance.monitor.service;

final class MonitorAlertCoordinator {

    interface Host {
        void dispatchParsedServerAlert(Object alert);
    }
}
```

- [ ] **Step 4: 在 MonitorService 中先只完成装配，不立即大搬逻辑**

```java
private MonitorStreamCoordinator streamCoordinator;
private MonitorAlertCoordinator alertCoordinator;
```

```java
streamCoordinator = new MonitorStreamCoordinator(...);
alertCoordinator = new MonitorAlertCoordinator(...);
```

- [ ] **Step 5: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceCoordinatorSourceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/service/MonitorStreamCoordinator.java app/src/main/java/com/binance/monitor/service/MonitorAlertCoordinator.java app/src/test/java/com/binance/monitor/service/MonitorServiceCoordinatorSourceTest.java
git commit -m "refactor: add service coordinators seam"
```

### Task 3: 为页面层建立依赖提供器，停止直接 new 底层对象

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/runtime/ScreenDependencyProvider.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Test: `app/src/test/java/com/binance/monitor/ui/host/HostTabNavigatorSourceTest.java`

- [ ] **Step 1: 写最小依赖提供器接口**

```java
package com.binance.monitor.ui.runtime;

import android.content.Context;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.remote.v2.GatewayV2SessionClient;
import java.util.concurrent.ExecutorService;

public interface ScreenDependencyProvider {
    AccountStorageRepository createAccountStorageRepository(Context context);
    GatewayV2SessionClient createSessionClient(Context context);
    ExecutorService createIoExecutor(String name);
}
```

- [ ] **Step 2: 在页面层先接 provider，不立刻删旧逻辑**

```java
private final ScreenDependencyProvider dependencyProvider;

accountStorageRepository = dependencyProvider.createAccountStorageRepository(getApplicationContext());
sessionClient = dependencyProvider.createSessionClient(getApplicationContext());
ioExecutor = dependencyProvider.createIoExecutor("account-stats-io");
```

- [ ] **Step 3: 增加源码约束测试，禁止页面继续裸 new 某些底层依赖**

```java
package com.binance.monitor.ui.host;

import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class HostTabNavigatorSourceTest {

    @Test
    public void accountStatsScreenShouldNotDirectlyNewGatewaySessionClient() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"),
                StandardCharsets.UTF_8
        );
        assertFalse(source.contains("new GatewayV2SessionClient("));
    }
}
```

- [ ] **Step 4: 运行测试并补齐最小实现**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.HostTabNavigatorSourceTest"`
Expected: FAIL -> 实现 provider 注入后 PASS

- [ ] **Step 5: 构建验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/runtime/ScreenDependencyProvider.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java app/src/test/java/com/binance/monitor/ui/host/HostTabNavigatorSourceTest.java
git commit -m "refactor: move screen dependency wiring behind provider"
```

## Self-Review

- 覆盖检查：这份计划覆盖了入口收口、alias 收口、MonitorService 拆 seam、页面直接 new 底层依赖这四类架构问题。
- 占位检查：没有使用“后续再补”“类似任务 N”这类占位语。
- 一致性检查：统一用 `ScreenDependencyProvider` 作为页面依赖装配抽象，没有混用第二个名字。

Plan complete and saved to `docs/superpowers/plans/2026-04-25-architecture-split-and-entry-convergence.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
