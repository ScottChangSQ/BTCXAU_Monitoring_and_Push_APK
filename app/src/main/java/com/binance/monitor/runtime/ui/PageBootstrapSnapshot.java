/*
 * 页面首帧状态快照，显式表达当前是否可渲染、是否处于恢复中。
 */
package com.binance.monitor.runtime.ui;

public final class PageBootstrapSnapshot {
    private final PageBootstrapState state;
    private final long transitionRevision;
    private final String activeRevision;

    private PageBootstrapSnapshot(PageBootstrapState state,
                                  long transitionRevision,
                                  String activeRevision) {
        this.state = state == null ? PageBootstrapState.TRUE_EMPTY : state;
        this.transitionRevision = Math.max(0L, transitionRevision);
        this.activeRevision = activeRevision == null ? "" : activeRevision;
    }

    public static PageBootstrapSnapshot initial() {
        return new PageBootstrapSnapshot(PageBootstrapState.TRUE_EMPTY, 0L, "");
    }

    public static PageBootstrapSnapshot of(PageBootstrapState state,
                                           long transitionRevision,
                                           String activeRevision) {
        return new PageBootstrapSnapshot(state, transitionRevision, activeRevision);
    }

    public PageBootstrapState getState() {
        return state;
    }

    public long getTransitionRevision() {
        return transitionRevision;
    }

    public String getActiveRevision() {
        return activeRevision;
    }

    public boolean hasRenderableContent() {
        return state == PageBootstrapState.MEMORY_READY
                || state == PageBootstrapState.LOCAL_READY_REMOTE_SYNCING
                || state == PageBootstrapState.REMOTE_READY;
    }

    public boolean shouldShowRestoreHint() {
        return state == PageBootstrapState.STORAGE_RESTORING
                || state == PageBootstrapState.LOCAL_READY_REMOTE_SYNCING;
    }

    public boolean shouldShowTrueEmpty() {
        return state == PageBootstrapState.TRUE_EMPTY;
    }

    PageBootstrapSnapshot next(PageBootstrapState nextState, String nextRevision) {
        String safeRevision = nextRevision == null ? "" : nextRevision;
        if (state == nextState && activeRevision.equals(safeRevision)) {
            return this;
        }
        return new PageBootstrapSnapshot(nextState, transitionRevision + 1L, safeRevision);
    }
}
