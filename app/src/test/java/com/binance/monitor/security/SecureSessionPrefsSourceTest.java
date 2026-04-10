package com.binance.monitor.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SecureSessionPrefsSourceTest {

    @Test
    public void sessionCacheShouldUseCanonicalActiveFieldName() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/security/SecureSessionPrefs.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("object.put(\"active\", profile.isActive());"));
        assertTrue(source.contains("RemoteAccountProfile.resolveActiveFlag(object.optBoolean(\"active\", false), state)"));
        assertFalse(source.contains("object.put(\"isActive\", profile.isActive());"));
        assertFalse(source.contains("object.optBoolean(\"isActive\", false)"));
    }

    @Test
    public void secureSessionPrefsShouldExposeStorageFailureInsteadOfSilentlyDeletingCiphertext() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/security/SecureSessionPrefs.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private volatile String lastStorageError = \"\";"));
        assertTrue(source.contains("public boolean hasStorageFailure() {"));
        assertTrue(source.contains("public String getLastStorageError() {"));
        assertFalse(source.contains("catch (Exception ignored)"));
        assertFalse(source.contains("preferences.edit().remove(KEY_PAYLOAD).remove(KEY_IV).apply();"));
    }
}
