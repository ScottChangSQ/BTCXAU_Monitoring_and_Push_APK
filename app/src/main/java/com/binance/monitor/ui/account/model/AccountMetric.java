package com.binance.monitor.ui.account.model;

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
