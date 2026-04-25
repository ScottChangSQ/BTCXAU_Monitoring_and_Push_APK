package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsDeferredVisibilitySourceTest {

    @Test
    public void coordinatorShouldKeepPendingDiffAndOnlyBindVisibleSections() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java");

        assertTrue(source.contains("AccountStatsSectionDiff sectionDiff = mergeSectionDiff(host.getPendingSectionDiff(), computedDiff);"));
        assertTrue(source.contains("AccountStatsSectionDiff visibleSectionDiff = intersectWithVisibleSections(sectionDiff);"));
        assertTrue(source.contains("host.setPendingSectionDiff(remainingSectionDiff.isEmpty() ? null : remainingSectionDiff);"));
        assertTrue(source.contains("sectionDiff.refreshTradeRecordsSection && host.isTradeRecordsSectionVisible()"));
    }

    @Test
    public void sharedScreenShouldRetryDeferredSectionsWhenPendingBlocksBecomeVisible() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");

        assertTrue(source.contains("private final ViewTreeObserver.OnScrollChangedListener deferredSecondaryVisibilityListener ="));
        assertTrue(source.contains("observer.addOnScrollChangedListener(deferredSecondaryVisibilityListener);"));
        assertTrue(source.contains("binding.scrollAccountStats.post(pendingVisibleSecondaryRenderRunnable);"));
        assertTrue(source.contains("private boolean isViewMeaningfullyVisible(@Nullable View view) {"));
    }

    @Test
    public void bridgeActivityShouldMirrorDeferredVisibilityHooks() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");

        assertTrue(source.contains("private final ViewTreeObserver.OnScrollChangedListener deferredSecondaryVisibilityListener ="));
        assertTrue(source.contains("observer.addOnScrollChangedListener(deferredSecondaryVisibilityListener);"));
        assertTrue(source.contains("binding.scrollAccountStats.removeCallbacks(pendingVisibleSecondaryRenderRunnable);"));
        assertTrue(source.contains("private boolean isViewMeaningfullyVisible(@Nullable View view) {"));
    }

    private String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
