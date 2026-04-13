/*
 * 独立登录弹窗源码约束测试，锁定已保存账号入口必须和本地会话缓存共用同一套去重逻辑。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountSessionDialogControllerSourceTest {

    @Test
    public void sessionDialogControllerShouldDeduplicateSavedAccountsOnRestoreAndPersist() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java"
        );

        assertTrue(source.contains("import com.binance.monitor.data.model.v2.session.RemoteAccountProfileDeduplicationHelper;"));
        assertTrue(source.contains("savedSessionAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(\n                summary.getSavedAccountsSnapshot()\n        );"));
        assertTrue(source.contains("savedSessionAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(savedAccounts);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到 AccountSessionDialogController.java");
    }
}
