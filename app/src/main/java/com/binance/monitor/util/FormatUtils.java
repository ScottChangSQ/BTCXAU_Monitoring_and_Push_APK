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

    public static String formatPriceNoDecimal(double value) {
        return decimal("#,##0", value);
    }

    public static String formatAmount(double value) {
        return decimal("#,##0.00", value / 1_000_000d) + "M$";
    }

    public static String formatAmountWithChineseUnit(double value) {
        double abs = Math.abs(value);
        if (abs >= 100_000_000d) {
            return decimal("0.00", value / 100_000_000d) + "亿$";
        }
        return decimal("0.00", value / 10_000d) + "万$";
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
        return formatSignedCurrency(value, "#,##0.00", true);
    }

    public static String formatSignedMoneyNoDecimal(double value) {
        return formatSignedCurrency(value, "#,##0", false);
    }

    public static String formatSignedMoneyOneDecimal(double value) {
        return formatSignedCurrency(value, "#,##0.0", true);
    }

    public static String formatPriceWithUnit(double value) {
        return "$" + formatPrice(value);
    }

    public static String formatPriceOneDecimalWithUnit(double value) {
        return "$" + decimal("#,##0.0", value);
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

    private static String formatSignedCurrency(double value, String pattern, boolean keepDecimal) {
        String zeroText = decimal(pattern, 0d);
        String amountText = decimal(pattern, Math.abs(value));
        if (zeroText.equals(amountText)) {
            return "$" + amountText;
        }
        String sign = value >= 0d ? "+$" : "-$";
        return sign + amountText;
    }
}
