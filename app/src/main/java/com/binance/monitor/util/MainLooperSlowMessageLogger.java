package com.binance.monitor.util;

import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Printer;

import androidx.annotation.NonNull;

/**
 * Debug 包主线程消息耗时探针，用于区分卡顿来自输入等待还是业务消息阻塞。
 */
public final class MainLooperSlowMessageLogger {
    private static final String TAG = "MainLooperTrace";
    private static final long SLOW_MESSAGE_MS = 24L;

    private MainLooperSlowMessageLogger() {
    }

    public static void install() {
        Looper.getMainLooper().setMessageLogging(new Printer() {
            private long startedAtMs;
            private String dispatchLabel = "";

            @Override
            public void println(@NonNull String line) {
                if (line.startsWith(">>>>>")) {
                    startedAtMs = SystemClock.uptimeMillis();
                    dispatchLabel = line;
                    return;
                }
                if (!line.startsWith("<<<<<") || startedAtMs <= 0L) {
                    return;
                }
                long durationMs = SystemClock.uptimeMillis() - startedAtMs;
                if (durationMs >= SLOW_MESSAGE_MS) {
                    Log.i(TAG, "main_message durationMs=" + durationMs
                            + " dispatch=" + trimDispatchLabel(dispatchLabel)
                            + " finish=" + trimDispatchLabel(line));
                }
                startedAtMs = 0L;
                dispatchLabel = "";
            }
        });
    }

    @NonNull
    private static String trimDispatchLabel(@NonNull String label) {
        String compact = label.replace('\n', ' ').trim();
        if (compact.length() <= 220) {
            return compact;
        }
        return compact.substring(0, 220);
    }
}
