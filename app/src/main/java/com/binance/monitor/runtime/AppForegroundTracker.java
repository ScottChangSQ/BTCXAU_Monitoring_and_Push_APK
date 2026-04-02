/*
 * 应用前后台状态跟踪器，负责基于进程生命周期统一广播当前是否处于前台。
 * 供 BinanceMonitorApp、MonitorService 等模块复用运行态节奏。
 */
package com.binance.monitor.runtime;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class AppForegroundTracker implements DefaultLifecycleObserver {

    public interface ForegroundStateListener {
        void onForegroundStateChanged(boolean foreground);
    }

    private static final AppForegroundTracker INSTANCE = new AppForegroundTracker();

    private final Set<ForegroundStateListener> listeners = new CopyOnWriteArraySet<>();
    private volatile boolean foreground;
    private volatile boolean initialized;

    private AppForegroundTracker() {
    }

    // 初始化应用级前后台监听，避免各模块重复注册进程生命周期。
    public static void init(Application application) {
        INSTANCE.register(application);
    }

    // 返回当前是否有可见页面在前台。
    public static AppForegroundTracker getInstance() {
        return INSTANCE;
    }

    // 读取当前前后台状态，供运行策略快速判断。
    public boolean isForeground() {
        return foreground;
    }

    // 注册状态监听，并立即同步一次当前状态。
    public void addListener(ForegroundStateListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        listener.onForegroundStateChanged(foreground);
    }

    // 移除状态监听，避免页面或服务销毁后继续回调。
    public void removeListener(ForegroundStateListener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        updateForeground(true);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        updateForeground(false);
    }

    // 只注册一次进程生命周期观察者。
    private synchronized void register(Application application) {
        if (initialized || application == null) {
            return;
        }
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        initialized = true;
    }

    // 状态变化后统一分发给订阅方。
    private void updateForeground(boolean targetForeground) {
        if (foreground == targetForeground) {
            return;
        }
        foreground = targetForeground;
        for (ForegroundStateListener listener : listeners) {
            listener.onForegroundStateChanged(targetForeground);
        }
    }
}
