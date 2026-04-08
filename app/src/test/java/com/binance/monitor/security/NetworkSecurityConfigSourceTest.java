/*
 * 网络安全配置源码约束测试，确保正式入口不再对白名单本地明文地址放行。
 */
package com.binance.monitor.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NetworkSecurityConfigSourceTest {

    @Test
    public void configShouldDisableGlobalCleartextAndRemoveLoopbackWhitelist() throws Exception {
        String source = readUtf8(
                "app/src/main/res/xml/network_security_config.xml",
                "src/main/res/xml/network_security_config.xml"
        );
        assertTrue("正式入口应关闭全局明文流量", source.contains("<base-config cleartextTrafficPermitted=\"false\""));
        assertFalse("不应继续放行 10.0.2.2", source.contains("10.0.2.2"));
        assertFalse("不应继续放行 127.0.0.1", source.contains("127.0.0.1"));
        assertFalse("不应继续放行 localhost", source.contains("localhost"));
    }

    @Test
    public void manifestShouldNotDeclareGlobalCleartextTraffic() throws Exception {
        String source = readUtf8(
                "app/src/main/AndroidManifest.xml",
                "src/main/AndroidManifest.xml"
        );
        assertFalse("Manifest 不应继续声明全局明文流量",
                source.contains("android:usesCleartextTraffic=\"true\""));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 network_security_config.xml");
    }
}
