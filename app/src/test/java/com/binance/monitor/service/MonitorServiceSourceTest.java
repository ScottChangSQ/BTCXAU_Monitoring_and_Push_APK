/*
 * 监控服务源码约束测试，确保进入应用和回到前台时走统一主链刷新。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceSourceTest {

    @Test
    public void foregroundEntryShouldRefreshAccountAndFloating() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("前台切回时应走统一前台状态处理入口",
                source.contains("foreground -> mainHandler.post(() -> handleForegroundStateChanged(foreground))"));
        assertTrue("前台切回时应先重建 v2 stream 主链连接，避免息屏后僵死连接继续占位",
                source.contains("restartV2Stream(\"foreground_resume\");"));
        assertTrue("前台切回时应主动刷新账户数据",
                source.contains("requestAccountRefreshFromV2();"));
        assertTrue("前台切回时应主动刷新悬浮窗消费层",
                source.contains("requestFloatingWindowRefresh(true);"));
    }

    @Test
    public void bootstrapShouldRefreshAccountOnce() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("新进入 APP 时应主动触发一次全局刷新",
                source.contains("requestForegroundEntryRefresh();"));
    }

    @Test
    public void staleWatchdogShouldRestartV2StreamInsteadOfOnlyChangingStatusText() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("watchdog 发现 v2 stream 失活时，应直接重建主链连接",
                source.contains("restartV2Stream(\"stale_watchdog\");"));
    }

    @Test
    public void floatingSnapshotShouldUseDisplayResolverSnapshotPositions() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("悬浮窗不应再回读本地恢复快照解析器",
                source.contains("AccountSnapshotDisplayResolver.resolve("));
        assertFalse("悬浮窗不应再直接读取数据库持仓列表",
                source.contains("accountStorageRepository == null ? new ArrayList<>() : accountStorageRepository.loadPositions()"));
    }

    @Test
    public void startupShouldLogResolvedGatewayAddresses() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("服务启动时应打印构建默认 MT5 网关地址",
                source.contains("APP诊断 BuildConfig MT5="));
        assertTrue("服务启动时应打印运行时解析后的 MT5 网关地址",
                source.contains("APP诊断 Runtime MT5="));
        assertTrue("服务启动时应打印运行时解析后的 Binance WS 地址",
                source.contains("APP诊断 Runtime BinanceWS="));
        assertTrue("服务启动时应统一走地址诊断入口",
                source.contains("logResolvedGatewayAddresses();"));
    }

    @Test
    public void abnormalHandlingShouldRespectMonitoringSwitchAndDispatchAlerts() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("本地异常命中前应先判断监控开关是否开启",
                source.contains("if (!Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue()))"));
        assertTrue("本地新异常应发消息提醒",
                source.contains("dispatchLocalAbnormalNotification(record);"));
        assertTrue("服务端 alerts 也应进入补发提醒链路",
                source.contains("dispatchServerAlertIfNeeded(alert);"));
    }

    @Test
    public void closedMarketSeriesShouldStillTriggerLocalAbnormalEvaluation() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("闭合 1m K 线写入主链后，仍应触发本地异常判定",
                source.contains("handleClosedKline(closedData);"));
    }

    @Test
    public void accountRefreshShouldClearStreamSnapshotWhenSessionBecomesInactive() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("账户补拉返回空缓存时应清理 stream 持仓快照，避免悬浮窗残留旧仓位",
                source.contains("if (cache == null) {\n                    clearStreamAccountSnapshot();"));
    }

    @Test
    public void floatingSnapshotShouldKeepAllPositionsInsteadOfOverwritingByCode() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("stream 持仓快照应保留完整列表，避免同产品多笔仓位被覆盖",
                source.contains("private final List<com.binance.monitor.ui.account.model.PositionItem> streamPositionSnapshot = new ArrayList<>();"));
        assertTrue("stream 账户快照应用时应追加所有仓位，而不是按 code 覆盖",
                source.contains("streamPositionSnapshot.add(item);"));
        assertFalse("不应按 code 覆盖 stream 持仓快照",
                source.contains("streamPositionSnapshot.put(item.getCode().trim().toUpperCase(), item);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MonitorService.java");
    }
}
