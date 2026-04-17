/*
 * 账户页拆分源码约束测试，锁定快照刷新链下沉。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeCoordinatorSourceTest {

    @Test
    public void accountStatsBridgeActivityShouldDelegateSnapshotRefreshChain() throws Exception {
        assertTrue("账户快照刷新协调器文件应存在",
                exists("app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                        "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"));

        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        assertTrue("账户页应持有快照刷新协调器",
                source.contains("private AccountSnapshotRefreshCoordinator snapshotRefreshCoordinator;"));
        assertTrue("账户页初始化时应装配快照刷新协调器",
                source.contains("snapshotRefreshCoordinator = new AccountSnapshotRefreshCoordinator("));
        assertTrue("账户页主动前台拉取应委托协调器处理",
                source.contains("snapshotRefreshCoordinator.requestForegroundEntrySnapshot();"));
        assertTrue("账户页不应再保留纯转发的 requestSnapshot 包装方法",
                !source.contains("private void requestSnapshot()"));
    }

    @Test
    public void accountStatsBridgeActivityPageRuntimeHostShouldTreatSharedScreenAsOnlyRealHost() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("桥接页 pageRuntime 宿主应只把前台刷新交给共享 screen",
                source.contains("public void attachForegroundRefresh() {\n                if (screen != null) {\n                    screen.attachForegroundRefresh();\n                }\n            }"));
        assertTrue("桥接页 pageRuntime 宿主应只把进入页面交给共享 screen",
                source.contains("public void enterAccountScreen(boolean coldStart) {\n                if (screen != null) {\n                    screen.enterAccountScreen(coldStart);\n                }\n            }"));
        assertTrue("桥接页 pageRuntime 宿主应只把定时快照请求交给共享 screen",
                source.contains("public void requestScheduledSnapshot() {\n                if (screen != null) {\n                    screen.requestScheduledSnapshot();\n                }\n            }"));
        assertTrue("桥接页不应再把真实页面前台刷新回退到 Activity 自己的 preload 分支",
                !source.contains("preloadManager.addCacheListener(preloadCacheListener);"));
        assertTrue("桥接页不应再把真实页面进入逻辑回退到 Activity 自己的 snapshot 协调器分支",
                !source.contains("snapshotRefreshCoordinator.enterAccountScreen(coldStart);"));
    }

    private static boolean exists(String... candidates) {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            if (Files.exists(workingDir.resolve(candidate).normalize())) {
                return true;
            }
        }
        return false;
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountStatsBridgeActivity.java");
    }
}
