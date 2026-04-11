/*
 * 审计护栏源码约束测试，锁定关键静态检查入口与配置文件存在。
 */
package com.binance.monitor.architecture;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AuditStaticGuardSourceTest {

    @Test
    public void appBuildShouldExposeAuditCriticalCheckstyleTask() throws Exception {
        String source = readUtf8(
                "app/build.gradle.kts",
                "build.gradle.kts"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("id(\"checkstyle\")"));
        assertTrue(source.contains("tasks.register<Checkstyle>(\"auditCriticalCheckstyle\")"));
        assertTrue(source.contains("tasks.named(\"check\")"));
        assertTrue(source.contains("dependsOn(\"auditCriticalCheckstyle\")"));
        assertTrue(source.contains("config/checkstyle/audit-critical.xml"));
    }

    @Test
    public void auditCriticalCheckstyleConfigShouldExist() {
        assertTrue(exists(
                "config/checkstyle/audit-critical.xml",
                "../config/checkstyle/audit-critical.xml"
        ));
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
        throw new IllegalStateException("找不到 build.gradle.kts");
    }
}
