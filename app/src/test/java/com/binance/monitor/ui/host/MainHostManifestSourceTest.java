package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainHostManifestSourceTest {

    @Test
    public void manifestShouldRegisterMainHostActivityForBridgeRouting() throws Exception {
        String manifest = new String(Files.readAllBytes(
                Paths.get("src/main/AndroidManifest.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(manifest.contains("android:name=\".ui.host.MainHostActivity\""));
        assertTrue(manifest.contains("android:exported=\"true\""));
    }
}
