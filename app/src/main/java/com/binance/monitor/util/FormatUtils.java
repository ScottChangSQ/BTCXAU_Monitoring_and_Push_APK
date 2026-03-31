package com.binance.monitor.util;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class FormatUtils {

    private FormatUtils() {
    }

    public static String formatPrice(double value) {
        return decimal("#,##0.00", value);
    }

    public static String formatAmount(double value) {
        return decimal("#,##0.00", value / 1_000_000d) + "M$";
    }

    public static String formatVolume(double value) {
        return decimal("#,##0.00", value);
    }

    public static String formatPercent(double value) {
        return decimal("#,##0.00", value) + "%";
    }

    public static String formatSigned(double value) {
        DecimalFormat format = new DecimalFormat("+#,##0.00;-#,##0.00");
        return format.format(value);
    }

    public static String formatSignedMoney(double value) {
        return (value >= 0d ? "+$" : "-$") + formatPrice(Math.abs(value));
    }

    public static String formatSignedMoneyNoDecimal(double value) {
        return (value >= 0d ? "+$" : "-$") + decimal("#,##0", Math.abs(value));
    }

    public static String formatPriceWithUnit(double value) {
        return "$" + formatPrice(value);
    }

    public static String formatPriceNoDecimalWithUnit(double value) {
        return "$" + decimal("#,##0", value);
    }

    public static String formatSignedPriceWithUnit(double value) {
        return formatSignedMoney(value);
    }

    public static String formatVolumeWithUnit(double value, String unit) {
        return formatVolume(value) + " " + unit;
    }

    public static String formatDateTime(long timestamp) {
        if (timestamp <= 0L) {
            return "--";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(timestamp));
    }

    public static String formatTime(long timestamp) {
        if (timestamp <= 0L) {
            return "--";
        }
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private static String decimal(String pattern, double value) {
        return new DecimalFormat(pattern).format(value);
    }
}
