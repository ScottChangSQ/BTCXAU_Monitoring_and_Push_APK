/*
 * 首帧状态机测试，锁定本地优先启动的合法状态流转。
 */
package com.binance.monitor.runtime.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PageBootstrapStateMachineTest {

    @Test
    public void storageRestoringShouldNotExposeTrueEmpty() {
        PageBootstrapStateMachine machine = new PageBootstrapStateMachine();

        PageBootstrapSnapshot snapshot = machine.onStorageRestoreStarted();

        assertEquals(PageBootstrapState.STORAGE_RESTORING, snapshot.getState());
        assertTrue(snapshot.shouldShowRestoreHint());
        assertFalse(snapshot.shouldShowTrueEmpty());
        assertFalse(snapshot.hasRenderableContent());
    }

    @Test
    public void storageMissShouldNotOverrideLocalReadyRemoteSyncing() {
        PageBootstrapStateMachine machine = new PageBootstrapStateMachine();
        machine.onStorageRestoreStarted();
        machine.onStorageDataReady("local-1");

        PageBootstrapSnapshot snapshot = machine.onStorageMiss();

        assertEquals(PageBootstrapState.LOCAL_READY_REMOTE_SYNCING, snapshot.getState());
        assertTrue(snapshot.shouldShowRestoreHint());
        assertFalse(snapshot.shouldShowTrueEmpty());
        assertEquals("local-1", snapshot.getActiveRevision());
    }

    @Test
    public void remoteReadyShouldNotRollbackToLocalSnapshot() {
        PageBootstrapStateMachine machine = new PageBootstrapStateMachine();
        machine.onStorageRestoreStarted();
        machine.onRemoteDataReady("remote-2");

        PageBootstrapSnapshot snapshot = machine.onStorageDataReady("local-1");

        assertEquals(PageBootstrapState.REMOTE_READY, snapshot.getState());
        assertFalse(snapshot.shouldShowRestoreHint());
        assertEquals("remote-2", snapshot.getActiveRevision());
    }

    @Test
    public void storageRestoringShouldNotReplaceVisibleMemoryContent() {
        PageBootstrapStateMachine machine = new PageBootstrapStateMachine();
        machine.onMemoryDataReady("memory-1");

        PageBootstrapSnapshot snapshot = machine.onStorageRestoreStarted();

        assertEquals(PageBootstrapState.MEMORY_READY, snapshot.getState());
        assertTrue(snapshot.hasRenderableContent());
        assertFalse(snapshot.shouldShowRestoreHint());
        assertEquals("memory-1", snapshot.getActiveRevision());
    }

    @Test
    public void remoteReadyShouldWinWhenArrivingBeforeStorageMiss() {
        PageBootstrapStateMachine machine = new PageBootstrapStateMachine();
        machine.onStorageRestoreStarted();
        machine.onRemoteDataReady("remote-1");

        PageBootstrapSnapshot snapshot = machine.onStorageMiss();

        assertEquals(PageBootstrapState.REMOTE_READY, snapshot.getState());
        assertFalse(snapshot.shouldShowTrueEmpty());
        assertEquals("remote-1", snapshot.getActiveRevision());
    }
}
