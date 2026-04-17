/*
 * 架构源码约束测试，锁定共享账户模型下沉与安全会话存储解耦。
 */
package com.binance.monitor.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountDomainDependencySourceTest {

    @Test
    public void sharedAccountModelsShouldLiveOutsideUiPackage() throws Exception {
        assertTrue("AccountTimeRange 应下沉到领域包",
                exists("app/src/main/java/com/binance/monitor/domain/account/AccountTimeRange.java",
                        "src/main/java/com/binance/monitor/domain/account/AccountTimeRange.java"));
        assertTrue("AccountMetric 应下沉到领域包",
                exists("app/src/main/java/com/binance/monitor/domain/account/model/AccountMetric.java",
                        "src/main/java/com/binance/monitor/domain/account/model/AccountMetric.java"));
        assertTrue("AccountSnapshot 应下沉到领域包",
                exists("app/src/main/java/com/binance/monitor/domain/account/model/AccountSnapshot.java",
                        "src/main/java/com/binance/monitor/domain/account/model/AccountSnapshot.java"));
        assertTrue("PositionItem 应下沉到领域包",
                exists("app/src/main/java/com/binance/monitor/domain/account/model/PositionItem.java",
                        "src/main/java/com/binance/monitor/domain/account/model/PositionItem.java"));
        assertTrue("TradeRecordItem 应下沉到领域包",
                exists("app/src/main/java/com/binance/monitor/domain/account/model/TradeRecordItem.java",
                        "src/main/java/com/binance/monitor/domain/account/model/TradeRecordItem.java"));
        assertTrue("CurvePoint 应下沉到领域包",
                exists("app/src/main/java/com/binance/monitor/domain/account/model/CurvePoint.java",
                        "src/main/java/com/binance/monitor/domain/account/model/CurvePoint.java"));
    }

    @Test
    public void runtimeAndDataLayersShouldDependOnDomainAccountPackage() throws Exception {
        String accountStorageRepository = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java",
                "src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java"
        );
        assertTrue("持久层仓库应依赖领域账户模型",
                accountStorageRepository.contains("import com.binance.monitor.domain.account.model.PositionItem;"));
        assertFalse("持久层仓库不应依赖 UI 账户模型",
                accountStorageRepository.contains("import com.binance.monitor.ui.account.model."));

        String gatewayV2Client = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java"
        );
        assertTrue("网关客户端应依赖领域时间范围枚举",
                gatewayV2Client.contains("import com.binance.monitor.domain.account.AccountTimeRange;"));
        assertFalse("网关客户端不应依赖 UI 时间范围枚举",
                gatewayV2Client.contains("import com.binance.monitor.ui.account.AccountTimeRange;"));

        String preloadManager = readUtf8(
                "app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java",
                "src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java"
        );
        assertTrue("运行时预加载管理器应依赖领域账户模型",
                preloadManager.contains("import com.binance.monitor.domain.account.model.AccountSnapshot;"));
        assertFalse("运行时预加载管理器不应依赖 UI 账户模型",
                preloadManager.contains("import com.binance.monitor.ui.account.model.AccountSnapshot;"));

        String monitorService = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );
        assertFalse("服务层不应再引用 UI 持仓模型",
                monitorService.contains("com.binance.monitor.ui.account.model."));
    }

    @Test
    public void secureSessionPrefsShouldDependOnDedicatedSessionStoreContract() throws Exception {
        assertTrue("安全会话存储契约应独立于 UI 协调器",
                exists("app/src/main/java/com/binance/monitor/security/SessionSummaryStore.java",
                        "src/main/java/com/binance/monitor/security/SessionSummaryStore.java"));

        String secureSessionPrefs = readUtf8(
                "app/src/main/java/com/binance/monitor/security/SecureSessionPrefs.java",
                "src/main/java/com/binance/monitor/security/SecureSessionPrefs.java"
        );
        assertTrue("安全会话偏好应实现独立契约",
                secureSessionPrefs.contains("implements SessionSummaryStore"));
        assertFalse("安全会话偏好不应再反向依赖 UI 会话协调器",
                secureSessionPrefs.contains("AccountRemoteSessionCoordinator.SessionSummaryStore"));
        assertFalse("安全会话偏好不应再导入 UI 会话协调器",
                secureSessionPrefs.contains("import com.binance.monitor.ui.account.AccountRemoteSessionCoordinator;"));
    }

    @Test
    public void auditHelpersShouldLiveInDedicatedPackages() throws Exception {
        assertTrue("账户页会话恢复助手应落到 session 子包",
                exists("app/src/main/java/com/binance/monitor/ui/account/session/AccountSessionRestoreHelper.java",
                        "src/main/java/com/binance/monitor/ui/account/session/AccountSessionRestoreHelper.java"));
        assertTrue("账户页次级渲染助手应保留在账户页包内，避免为整理包结构而打破包级职责边界",
                exists("app/src/main/java/com/binance/monitor/ui/account/AccountDeferredSnapshotRenderHelper.java",
                        "src/main/java/com/binance/monitor/ui/account/AccountDeferredSnapshotRenderHelper.java"));
        assertTrue("账户历史补拉 gate 应落到 service.account 子包",
                exists("app/src/main/java/com/binance/monitor/service/account/AccountHistoryRefreshGate.java",
                        "src/main/java/com/binance/monitor/service/account/AccountHistoryRefreshGate.java"));
        assertTrue("stream 顺序守卫应落到 service.stream 子包",
                exists("app/src/main/java/com/binance/monitor/service/stream/V2StreamSequenceGuard.java",
                        "src/main/java/com/binance/monitor/service/stream/V2StreamSequenceGuard.java"));
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
        throw new IllegalStateException("找不到源码文件");
    }
}
