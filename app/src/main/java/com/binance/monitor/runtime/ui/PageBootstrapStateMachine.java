/*
 * 页面首帧状态机，负责锁定本地优先启动的合法流转。
 */
package com.binance.monitor.runtime.ui;

public final class PageBootstrapStateMachine {
    private PageBootstrapSnapshot snapshot = PageBootstrapSnapshot.initial();

    public PageBootstrapSnapshot getSnapshot() {
        return snapshot;
    }

    public PageBootstrapSnapshot onMemoryDataReady(String revision) {
        if (snapshot.getState() == PageBootstrapState.REMOTE_READY) {
            return snapshot;
        }
        snapshot = snapshot.next(PageBootstrapState.MEMORY_READY, revision);
        return snapshot;
    }

    public PageBootstrapSnapshot onStorageRestoreStarted() {
        if (snapshot.hasRenderableContent() || snapshot.getState() == PageBootstrapState.REMOTE_READY) {
            return snapshot;
        }
        snapshot = snapshot.next(PageBootstrapState.STORAGE_RESTORING, snapshot.getActiveRevision());
        return snapshot;
    }

    public PageBootstrapSnapshot onStorageDataReady(String revision) {
        if (snapshot.getState() == PageBootstrapState.REMOTE_READY) {
            return snapshot;
        }
        snapshot = snapshot.next(PageBootstrapState.LOCAL_READY_REMOTE_SYNCING, revision);
        return snapshot;
    }

    public PageBootstrapSnapshot onStorageMiss() {
        if (snapshot.getState() == PageBootstrapState.MEMORY_READY
                || snapshot.getState() == PageBootstrapState.LOCAL_READY_REMOTE_SYNCING
                || snapshot.getState() == PageBootstrapState.REMOTE_READY) {
            return snapshot;
        }
        snapshot = snapshot.next(PageBootstrapState.TRUE_EMPTY, snapshot.getActiveRevision());
        return snapshot;
    }

    public PageBootstrapSnapshot onRemoteDataReady(String revision) {
        snapshot = snapshot.next(PageBootstrapState.REMOTE_READY, revision);
        return snapshot;
    }
}
