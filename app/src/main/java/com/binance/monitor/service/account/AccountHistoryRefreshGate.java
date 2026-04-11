/*
 * 账户历史补拉并发 gate，负责把“是否在补拉中”和“最新待续跑 revision”收口到同一个同步原语。
 * MonitorService 通过它避免 lock、标记位和待处理 revision 在多个位置散落。
 */
package com.binance.monitor.service.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AccountHistoryRefreshGate {

    private boolean inFlight;
    private String pendingRevision = "";

    // 尝试启动一次 history 补拉；若当前已有补拉在跑，则只更新待续跑 revision。
    @NonNull
    public synchronized StartDecision tryStart(@Nullable String revision) {
        String safeRevision = revision == null ? "" : revision.trim();
        if (safeRevision.isEmpty()) {
            return StartDecision.skip();
        }
        if (inFlight) {
            pendingRevision = safeRevision;
            return StartDecision.queued(safeRevision);
        }
        inFlight = true;
        pendingRevision = "";
        return StartDecision.started(safeRevision);
    }

    // 当前补拉结束后返回是否需要继续补下一版 revision。
    @NonNull
    public synchronized FinishDecision finish(@Nullable String completedRevision) {
        String safeCompletedRevision = completedRevision == null ? "" : completedRevision.trim();
        String nextRevision = pendingRevision;
        pendingRevision = "";
        inFlight = false;
        if (nextRevision.isEmpty() || nextRevision.equals(safeCompletedRevision)) {
            return FinishDecision.idle();
        }
        return FinishDecision.continueWith(nextRevision);
    }

    public static final class StartDecision {
        private final boolean shouldStart;
        private final String revision;

        private StartDecision(boolean shouldStart, @Nullable String revision) {
            this.shouldStart = shouldStart;
            this.revision = revision == null ? "" : revision.trim();
        }

        // 返回无需启动补拉的结果。
        @NonNull
        public static StartDecision skip() {
            return new StartDecision(false, "");
        }

        // 返回已接管本次补拉的结果。
        @NonNull
        public static StartDecision started(@Nullable String revision) {
            return new StartDecision(true, revision);
        }

        // 返回当前只排队、不立即启动的结果。
        @NonNull
        public static StartDecision queued(@Nullable String revision) {
            return new StartDecision(false, revision);
        }

        public boolean shouldStart() {
            return shouldStart;
        }

        @NonNull
        public String getRevision() {
            return revision;
        }
    }

    public static final class FinishDecision {
        private final boolean shouldContinue;
        private final String nextRevision;

        private FinishDecision(boolean shouldContinue, @Nullable String nextRevision) {
            this.shouldContinue = shouldContinue;
            this.nextRevision = nextRevision == null ? "" : nextRevision.trim();
        }

        // 返回当前无需续跑的结果。
        @NonNull
        public static FinishDecision idle() {
            return new FinishDecision(false, "");
        }

        // 返回当前应继续补拉下一版 revision 的结果。
        @NonNull
        public static FinishDecision continueWith(@Nullable String nextRevision) {
            return new FinishDecision(true, nextRevision);
        }

        public boolean shouldContinue() {
            return shouldContinue;
        }

        @NonNull
        public String getNextRevision() {
            return nextRevision;
        }
    }
}
