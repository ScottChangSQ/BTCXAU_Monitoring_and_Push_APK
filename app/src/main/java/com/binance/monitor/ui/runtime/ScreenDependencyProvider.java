/*
 * 页面层依赖提供器，统一管理页面控制器和 Screen 直接创建的底层依赖。
 * 当前先收口仓储、会话客户端和线程池，避免页面类继续直接 new 这些基础对象。
 */
package com.binance.monitor.ui.runtime;

import android.content.Context;

import androidx.annotation.NonNull;

import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.remote.v2.GatewayV2SessionClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public interface ScreenDependencyProvider {

    @NonNull
    static ScreenDependencyProvider defaultProvider() {
        return DefaultHolder.INSTANCE;
    }

    @NonNull
    AccountStorageRepository createAccountStorageRepository(@NonNull Context context);

    @NonNull
    GatewayV2SessionClient createSessionClient(@NonNull Context context);

    @NonNull
    ExecutorService createSingleThreadExecutor(@NonNull String name);

    @NonNull
    ExecutorService createFixedThreadPool(@NonNull String name, int threadCount);

    final class DefaultHolder {
        private static final ScreenDependencyProvider INSTANCE = new DefaultScreenDependencyProvider();

        private DefaultHolder() {
        }
    }

    final class DefaultScreenDependencyProvider implements ScreenDependencyProvider {

        @NonNull
        @Override
        public AccountStorageRepository createAccountStorageRepository(@NonNull Context context) {
            return new AccountStorageRepository(context.getApplicationContext());
        }

        @NonNull
        @Override
        public GatewayV2SessionClient createSessionClient(@NonNull Context context) {
            return new GatewayV2SessionClient(context.getApplicationContext());
        }

        @NonNull
        @Override
        public ExecutorService createSingleThreadExecutor(@NonNull String name) {
            return Executors.newSingleThreadExecutor(createNamedThreadFactory(name));
        }

        @NonNull
        @Override
        public ExecutorService createFixedThreadPool(@NonNull String name, int threadCount) {
            return Executors.newFixedThreadPool(threadCount, createNamedThreadFactory(name));
        }

        @NonNull
        private ThreadFactory createNamedThreadFactory(@NonNull String name) {
            AtomicInteger counter = new AtomicInteger(1);
            return runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName(name + "-" + counter.getAndIncrement());
                return thread;
            };
        }
    }
}
