/*
 * 账户指标键值模型，供账户页、运行时缓存和本地持久层复用。
 */
package com.binance.monitor.domain.account.model;

public class AccountMetric {
    private final String name;
    private final String value;

    public AccountMetric(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
