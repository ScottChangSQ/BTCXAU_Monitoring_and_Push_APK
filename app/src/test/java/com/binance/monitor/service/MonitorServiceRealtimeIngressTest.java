package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceRealtimeIngressTest {

    @Test
    public void realtimeIngressShouldNoLongerDependOnLocalDraftProgressHeuristics() throws Exception {
        String minuteBaseStoreSource = readUtf8(
                "app/src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java",
                "src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String monitorServiceSource = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("实时链不应再保留本地 isDraftProgressing 业务判定",
                minuteBaseStoreSource.contains("isDraftProgressing("));
        assertTrue("同一分钟新消息应直接覆盖当前草稿，不再要求量额必须增长",
                minuteBaseStoreSource.contains("return candidate.getOpenTime() < currentDraft.getOpenTime();"));
        assertTrue("服务层 marketTick 入口应只保留 marketSeq 这样的技术性校验",
                monitorServiceSource.contains("if (!v2StreamSequenceGuard.shouldApplyMarketSeq(marketSeq)) {\n            return;\n        }"));
        assertTrue("服务层应先按当前运行态决定是否推进 market snapshot，避免不可见场景继续重绘",
                monitorServiceSource.contains("boolean shouldApplyMarketSnapshot = MonitorStreamRuntimeModeHelper.shouldApplyMarketSnapshot(runtimeMode);"));
        assertTrue("服务层在允许推进 market snapshot 的运行态下再应用统一流快照",
                monitorServiceSource.contains("if (shouldApplyMarketSnapshot) {\n            marketApplied = applyMarketSnapshotFromStream(message.getMarketSnapshot());\n        }"));
        assertTrue("marketTick 成功应用后才推进 v2 stream 健康时间，健康状态必须来自已应用的统一流数据",
                monitorServiceSource.contains("if (marketApplied) {\n            lastV2StreamMessageAt = System.currentTimeMillis();\n        }"));
        int marketHandlerStart = monitorServiceSource.indexOf("private void handleMarketTickMessage");
        int marketHealthIndex = monitorServiceSource.indexOf("if (marketApplied) {\n            lastV2StreamMessageAt = System.currentTimeMillis();\n        }", marketHandlerStart);
        int marketFloatingIndex = monitorServiceSource.indexOf("floatingCoordinator.requestRefresh(false);", marketHandlerStart);
        assertTrue("marketTick 刷新悬浮窗前应先推进已应用时间，避免 UI 读取到旧的连接阶段",
                marketHealthIndex >= 0 && marketFloatingIndex > marketHealthIndex);
        assertTrue("账户运行态异步应用完成后应刷新连接状态，再刷新悬浮窗",
                monitorServiceSource.contains("mainHandler.post(() -> {\n                    updateConnectionStatus();\n                    floatingCoordinator.requestRefresh(false);\n                });"));
        assertTrue("实时市场 stream 应走独立实时执行器，不能再和补修共用后台线程",
                monitorServiceSource.contains("executeRealtimeMarket(() -> {")
                        && monitorServiceSource.contains("handleV2StreamMessage(message);"));
        assertFalse("收到消息但未成功应用前不应推进 v2 stream 健康时间",
                monitorServiceSource.contains("executeRealtimeMarket(() -> {\n                    lastV2StreamMessageAt = System.currentTimeMillis();"));
        assertTrue("市场补修应改走后台执行器，避免阻塞实时市场链",
                monitorServiceSource.contains("executeBackgroundWork(() -> {\n            try {\n                MarketTruthSnapshot snapshot = repository.getMarketTruthSnapshotLiveData().getValue();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到实时链源码");
    }
}
