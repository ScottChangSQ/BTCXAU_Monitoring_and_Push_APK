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

        assertTrue(source.contains("public SessionSummarySnapshot loadSessionSummary() {"));
        assertTrue(source.contains("return readSessionSummary();"));
        assertTrue(source.contains("private SessionSummarySnapshot readSessionSummary() {"));
        assertTrue(source.contains("return SessionSummarySnapshot.empty();"));
        assertTrue(source.contains("return SessionSummarySnapshot.storageFailure(lastStorageError);"));
        assertFalse(source.contains("catch (Exception ignored)"));
        assertFalse(source.contains("return new SessionCache();"));
        assertFalse(source.contains("preferences.edit().remove(KEY_PAYLOAD).remove(KEY_IV).apply();"));
    }

    @Test
    public void secureSessionPrefsShouldBlockWritesWhenCiphertextCannotBeRead() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/security/SecureSessionPrefs.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("SessionCache cache = requireWritableCache();"));
        assertTrue(source.contains("if (cache == null) {"));
        assertTrue(source.contains("private SessionCache requireWritableCache() {")
                || source.contains("private SessionCache requireWritableCache()")
                || source.contains("private SessionCache requireWritableCache("));
        assertTrue(source.contains("if (snapshot.hasStorageFailure()) {"));
        assertTrue(source.contains("SessionCache writableCache = SessionCache.fromSnapshot(snapshot);"));
    }

    @Test
    public void secureSessionPrefsShouldDeduplicateSavedAccountsBeforePersistAndRestore() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/security/SecureSessionPrefs.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("import com.binance.monitor.data.model.v2.session.RemoteAccountProfileDeduplicationHelper;"));
        assertTrue(source.contains("cache.savedAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(savedAccounts);"));
        assertTrue(source.contains("cache.savedAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(cache.savedAccounts);"));
        assertTrue(source.contains("cache.savedAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(\n                    snapshot.getSavedAccountsSnapshot()\n            );"));
    }
}
