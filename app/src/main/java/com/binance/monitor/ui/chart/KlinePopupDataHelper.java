/*
 * K线十字光标弹窗数据辅助，负责把基础 OHLC 与主图指标整理成统一行结构。
 * 供 KlineChartView 和对应单测复用。
 */
package com.binance.monitor.ui.chart;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.util.FormatUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class KlinePopupDataHelper {

    private static final SimpleDateFormat POPUP_TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    static final class Row {
        final String label;
        final String value;

        Row(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private KlinePopupDataHelper() {
    }

    // 按当前已启用的主图指标生成长按弹窗行，避免 UI 层自己拼接散乱文案。
    static List<Row> buildRows(CandleEntry candle,
                               boolean showBoll,
                               int bollPeriod,
                               int bollStdMultiplier,
                               double bollUp,
                               double bollMid,
                               double bollDn,
                               boolean showMa,
                               int maPeriod,
                               double maValue,
                               boolean showEma,
                               int emaPeriod,
                               double emaValue,
                               boolean showSra,
                               int sraPeriod,
                               double sraValue) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("时间", candle == null ? "-" : POPUP_TIME_FORMAT.format(new Date(candle.getOpenTime()))));
        rows.add(new Row("O", candle == null ? "-" : FormatUtils.formatPrice(candle.getOpen())));
        rows.add(new Row("H", candle == null ? "-" : FormatUtils.formatPrice(candle.getHigh())));
        rows.add(new Row("L", candle == null ? "-" : FormatUtils.formatPrice(candle.getLow())));
        rows.add(new Row("C", candle == null ? "-" : FormatUtils.formatPrice(candle.getClose())));
        rows.add(new Row("价格变动", candle == null ? "-" : formatSignedDelta(candle.getClose() - candle.getOpen())));
        rows.add(new Row("VOL", candle == null ? "-" : formatVolumeNumber(candle.getVolume())));
        rows.add(new Row("TOV", candle == null ? "-" : FormatUtils.formatAmountWithChineseUnit(candle.getQuoteVolume())));
        if (showBoll) {
            appendPriceRow(rows, "BOLL UP", bollUp);
            appendPriceRow(rows, "BOLL MB", bollMid);
            appendPriceRow(rows, "BOLL DN", bollDn);
        }
        if (showMa) {
            appendPriceRow(rows, "MA(" + maPeriod + ")", maValue);
        }
        if (showEma) {
            appendPriceRow(rows, "EMA(" + emaPeriod + ")", emaValue);
        }
        if (showSra) {
            appendPriceRow(rows, "SRA(" + sraPeriod + ")", sraValue);
        }
        return rows;
    }

    // 只在指标值有效时追加，避免弹窗里出现一串空白占位。
    private static void appendPriceRow(List<Row> rows, String label, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return;
        }
        rows.add(new Row(label, FormatUtils.formatPrice(value)));
    }

    private static String formatSignedDelta(double value) {
        return new DecimalFormat("+#,##0.00;-#,##0.00").format(value);
    }

    private static String formatVolumeNumber(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000d) {
            return compact(value / 1_000_000_000d) + "b";
        }
        if (abs >= 1_000_000d) {
            return compact(value / 1_000_000d) + "m";
        }
        if (abs >= 1_000d) {
            return compact(value / 1_000d) + "k";
        }
        return new DecimalFormat("#,##0.##").format(value);
    }

    private static String compact(double value) {
        return new DecimalFormat("0.##").format(value);
    }
}
