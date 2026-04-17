/*
 * OkHttp 传输层重置辅助器，统一把旧客户端资源释放挪到后台线程，避免前台切换时触发主线程网络异常。
 */
package com.binance.monitor.data.remote;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public final class OkHttpTransportResetHelper {
    private static final ExecutorService CLOSE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "okhttp-transport-close");
        thread.setDaemon(true);
        return thread;
    });

    private OkHttpTransportResetHelper() {
    }

    // 在后台线程关闭旧连接池与调度线程，避免主线程前后台切换时同步触网。
    public static void closeClientAsync(@Nullable OkHttpClient previous) {
        if (previous == null) {
            return;
        }
        CLOSE_EXECUTOR.execute(() -> closeClient(previous));
    }

    // 统一释放旧 transport 资源，防止频繁重建后残留连接和工作线程。
    private static void closeClient(@Nullable OkHttpClient previous) {
        if (previous == null) {
            return;
        }
        previous.connectionPool().evictAll();
        previous.dispatcher().executorService().shutdown();
    }
}
