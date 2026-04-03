/*
 * 行情持仓页渲染辅助，负责生成稳定签名并判断是否真的需要重绘行情卡片。
 * 供 MainActivity 在切页恢复和实时观察器回调时复用，减少重复渲染带来的卡顿。
 */
package com.binance.monitor.ui.main;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.KlineData;

final class MainMarketRenderHelper {

    private MainMarketRenderHelper() {
    }

    // 生成当前行情卡片的稳定签名；只有签名变化时才需要整块重绘。
    static String buildRenderSignature(@Nullable String symbol,
                                       @Nullable Double latestPrice,
                                       @Nullable KlineData latestKline) {
        String safeSymbol = symbol == null ? "" : symbol.trim().toUpperCase();
        long openTime = latestKline == null ? 0L : latestKline.getOpenTime();
        long closeTime = latestKline == null ? 0L : latestKline.getCloseTime();
        double open = latestKline == null ? 0d : latestKline.getOpenPrice();
        double close = latestKline == null ? 0d : latestKline.getClosePrice();
        double high = latestKline == null ? 0d : latestKline.getHighPrice();
        double low = latestKline == null ? 0d : latestKline.getLowPrice();
        double volume = latestKline == null ? 0d : latestKline.getVolume();
        double amount = latestKline == null ? 0d : latestKline.getQuoteAssetVolume();
        double price = latestPrice == null ? Double.NaN : latestPrice;
        return safeSymbol
                + "|" + price
                + "|" + openTime
                + "|" + closeTime
                + "|" + open
                + "|" + close
                + "|" + high
                + "|" + low
                + "|" + volume
                + "|" + amount;
    }

    // 当签名未变化时，说明整块行情卡片内容没变，不必重复刷新。
    static boolean shouldRender(@Nullable String previousSignature,
                                @Nullable String nextSignature) {
        if (nextSignature == null) {
            return false;
        }
        return !nextSignature.equals(previousSignature);
    }
}
