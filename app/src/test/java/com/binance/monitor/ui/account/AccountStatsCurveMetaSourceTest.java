package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsCurveMetaSourceTest {

    @Test
    public void accountStatsShouldKeepCurveMetaHiddenAcrossScreenAndBridgePaths() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
        String bridgeSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");

        assertTrue(screenSource.contains("binding.tvCurveMeta.setVisibility(View.GONE);"));
        assertTrue(bridgeSource.contains("binding.tvCurveMeta.setVisibility(View.GONE);"));
        assertFalse(screenSource.contains("binding.tvCurveMeta.setVisibility(View.VISIBLE);"));
        assertFalse(bridgeSource.contains("binding.tvCurveMeta.setVisibility(View.VISIBLE);"));
    }

    @Test
    public void curveRenderHelperShouldNotBuildRuntimeDescriptionText() throws Exception {
        String helperSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java");

        assertFalse(helperSource.contains("区间净值 $"));
        assertFalse(helperSource.contains("当前净值 $"));
        assertFalse(helperSource.contains("当前结余 $"));
        assertFalse(helperSource.contains("最大回撤区间"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
