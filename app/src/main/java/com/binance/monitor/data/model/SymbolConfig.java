package com.binance.monitor.data.model;

import com.binance.monitor.constants.AppConstants;

public class SymbolConfig {

    private final String symbol;
    private double volumeThreshold;
    private double amountThreshold;
    private double priceChangeThreshold;
    private boolean volumeEnabled;
    private boolean amountEnabled;
    private boolean priceChangeEnabled;

    public SymbolConfig(String symbol,
                        double volumeThreshold,
                        double amountThreshold,
                        double priceChangeThreshold,
                        boolean volumeEnabled,
                        boolean amountEnabled,
                        boolean priceChangeEnabled) {
        this.symbol = symbol;
        this.volumeThreshold = volumeThreshold;
        this.amountThreshold = amountThreshold;
        this.priceChangeThreshold = priceChangeThreshold;
        this.volumeEnabled = volumeEnabled;
        this.amountEnabled = amountEnabled;
        this.priceChangeEnabled = priceChangeEnabled;
    }

    public static SymbolConfig createDefault(String symbol) {
        if (AppConstants.SYMBOL_XAU.equals(symbol)) {
            return new SymbolConfig(symbol,
                    AppConstants.XAU_DEFAULT_VOLUME,
                    AppConstants.XAU_DEFAULT_AMOUNT,
                    AppConstants.XAU_DEFAULT_PRICE_CHANGE,
                    true,
                    true,
                    true);
        }
        return new SymbolConfig(symbol,
                AppConstants.BTC_DEFAULT_VOLUME,
                AppConstants.BTC_DEFAULT_AMOUNT,
                AppConstants.BTC_DEFAULT_PRICE_CHANGE,
                true,
                true,
                true);
    }

    public SymbolConfig copy() {
        return new SymbolConfig(symbol, volumeThreshold, amountThreshold, priceChangeThreshold,
                volumeEnabled, amountEnabled, priceChangeEnabled);
    }

    public String getSymbol() {
        return symbol;
    }

    public double getVolumeThreshold() {
        return volumeThreshold;
    }

    public void setVolumeThreshold(double volumeThreshold) {
        this.volumeThreshold = volumeThreshold;
    }

    public double getAmountThreshold() {
        return amountThreshold;
    }

    public void setAmountThreshold(double amountThreshold) {
        this.amountThreshold = amountThreshold;
    }

    public double getPriceChangeThreshold() {
        return priceChangeThreshold;
    }

    public void setPriceChangeThreshold(double priceChangeThreshold) {
        this.priceChangeThreshold = priceChangeThreshold;
    }

    public boolean isVolumeEnabled() {
        return volumeEnabled;
    }

    public void setVolumeEnabled(boolean volumeEnabled) {
        this.volumeEnabled = volumeEnabled;
    }

    public boolean isAmountEnabled() {
        return amountEnabled;
    }

    public void setAmountEnabled(boolean amountEnabled) {
        this.amountEnabled = amountEnabled;
    }

    public boolean isPriceChangeEnabled() {
        return priceChangeEnabled;
    }

    public void setPriceChangeEnabled(boolean priceChangeEnabled) {
        this.priceChangeEnabled = priceChangeEnabled;
    }
}
