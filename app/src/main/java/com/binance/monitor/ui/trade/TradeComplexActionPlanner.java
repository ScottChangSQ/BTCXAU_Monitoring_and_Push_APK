/*
 * 复杂交易规划器，负责把部分平仓、批量平仓、加仓、反手和 Close By 统一展开成正式批量计划。
 * 第三阶段只允许页面层传入当前上下文，由这里归一复杂语义，不再在页面层手写批量分支。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.domain.account.model.PositionItem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TradeComplexActionPlanner {

    private TradeComplexActionPlanner() {
    }

    // 规划部分平仓，必须显式传目标平仓手数。
    public static BatchTradePlan planPartialClose(@NonNull String accountId,
                                                  @Nullable String accountMode,
                                                  @NonNull PositionItem position,
                                                  double targetVolume) {
        double normalizedVolume = requirePositiveVolume(targetVolume, "targetVolume 必须大于 0");
        double currentVolume = Math.abs(position.getQuantity());
        if (currentVolume <= 0d || normalizedVolume > currentVolume) {
            throw new IllegalArgumentException("部分平仓手数不能超过当前持仓");
        }
        TradeCommand command = TradeCommandFactory.closePosition(
                accountId,
                resolveSymbol(position),
                requirePositionTicket(position),
                normalizedVolume,
                0d
        );
        return buildPlan(
                "partial-close",
                "BEST_EFFORT",
                accountMode,
                "部分平仓 " + resolveSymbol(position),
                Collections.singletonList(buildItem("close-1", TradeCommandFactory.describe(command), command, null))
        );
    }

    // 规划批量平仓，每个持仓生成一个独立 item。
    public static BatchTradePlan planBatchClose(@NonNull String accountId,
                                                @Nullable String accountMode,
                                                @NonNull List<PositionItem> positions) {
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("positions 不能为空");
        }
        List<BatchTradeItem> items = new ArrayList<>();
        int index = 1;
        for (PositionItem position : positions) {
            if (position == null) {
                continue;
            }
            double volume = requirePositiveVolume(Math.abs(position.getQuantity()), "持仓手数必须大于 0");
            TradeCommand command = TradeCommandFactory.closePosition(
                    accountId,
                    resolveSymbol(position),
                    requirePositionTicket(position),
                    volume,
                    0d
            );
            items.add(buildItem("close-" + index, TradeCommandFactory.describe(command), command, null));
            index++;
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("positions 不能为空");
        }
        return buildPlan("batch-close", "BEST_EFFORT", accountMode, "批量平仓", items);
    }

    // 规划加仓，始终展开为一笔明确方向的开仓命令。
    public static BatchTradePlan planAddPosition(@NonNull String accountId,
                                                 @Nullable String accountMode,
                                                 @NonNull String symbol,
                                                 @NonNull String side,
                                                 double volume,
                                                 double price,
                                                 double sl,
                                                 double tp) {
        TradeCommand command = TradeCommandFactory.openMarket(
                accountId,
                symbol,
                normalizeSide(side),
                requirePositiveVolume(volume, "volume 必须大于 0"),
                price,
                sl,
                tp
        );
        return buildPlan(
                "add-position",
                "BEST_EFFORT",
                accountMode,
                "加仓 " + symbol,
                Collections.singletonList(buildItem("open-1", TradeCommandFactory.describe(command), command, null))
        );
    }

    // 规划反手；netting 与 hedging 都必须显式保留“先平再开”的动作链，不做隐式净额推导。
    public static BatchTradePlan planReverse(@NonNull String accountId,
                                             @Nullable String accountMode,
                                             @NonNull PositionItem currentPosition,
                                             double targetVolume,
                                             double referencePrice) {
        double currentVolume = requirePositiveVolume(Math.abs(currentPosition.getQuantity()), "当前持仓手数必须大于 0");
        double normalizedTargetVolume = requirePositiveVolume(targetVolume, "targetVolume 必须大于 0");
        String symbol = resolveSymbol(currentPosition);
        String currentSide = normalizeSide(currentPosition.getSide());
        String reverseSide = "buy".equals(currentSide) ? "sell" : "buy";
        TradeCommand closeCommand = TradeCommandFactory.closePosition(
                accountId,
                symbol,
                requirePositionTicket(currentPosition),
                currentVolume,
                0d
        );
        TradeCommand openCommand = TradeCommandFactory.openMarket(
                accountId,
                symbol,
                reverseSide,
                normalizedTargetVolume,
                referencePrice,
                0d,
                0d
        );
        List<BatchTradeItem> items = new ArrayList<>();
        items.add(buildItem("close-1", TradeCommandFactory.describe(closeCommand), closeCommand, null));
        items.add(buildItem("open-2", TradeCommandFactory.describe(openCommand), openCommand, null));
        return buildPlan("reverse", "BEST_EFFORT", accountMode, "反手 " + symbol, items);
    }

    // 规划 Close By，只允许成对 opposite position 进入 grouped 批量计划。
    public static BatchTradePlan planCloseBy(@NonNull String accountId,
                                             @NonNull PositionItem firstPosition,
                                             @NonNull PositionItem oppositePosition) {
        String firstSymbol = resolveSymbol(firstPosition);
        String secondSymbol = resolveSymbol(oppositePosition);
        if (!firstSymbol.equalsIgnoreCase(secondSymbol)) {
            throw new IllegalArgumentException("Close By 只能用于同一品种");
        }
        String firstSide = normalizeSide(firstPosition.getSide());
        String secondSide = normalizeSide(oppositePosition.getSide());
        if (firstSide.equals(secondSide)) {
            throw new IllegalArgumentException("Close By 需要一买一卖成对持仓");
        }
        String groupKey = "pair-1";
        JSONObject firstExtras = new JSONObject();
        JSONObject secondExtras = new JSONObject();
        putQuietly(firstExtras, "groupKey", groupKey);
        putQuietly(secondExtras, "groupKey", groupKey);
        TradeCommand firstCommand = TradeCommandFactory.closeBy(
                accountId,
                firstSymbol,
                requirePositionTicket(firstPosition),
                requirePositionTicket(oppositePosition)
        );
        TradeCommand secondCommand = TradeCommandFactory.closeBy(
                accountId,
                secondSymbol,
                requirePositionTicket(oppositePosition),
                requirePositionTicket(firstPosition)
        );
        List<BatchTradeItem> items = new ArrayList<>();
        items.add(buildItem("pair-1a", TradeCommandFactory.describe(firstCommand), firstCommand, firstExtras));
        items.add(buildItem("pair-1b", TradeCommandFactory.describe(secondCommand), secondCommand, secondExtras));
        return buildPlan("close-by", "GROUPED", "hedging", "对锁平仓 " + firstSymbol, items);
    }

    // 构建标准批量计划。
    private static BatchTradePlan buildPlan(String prefix,
                                            String strategy,
                                            @Nullable String accountMode,
                                            String summary,
                                            List<BatchTradeItem> items) {
        return new BatchTradePlan(
                nextBatchId(prefix),
                strategy,
                normalizeAccountMode(accountMode),
                summary,
                items
        );
    }

    // 构建标准批量单项。
    private static BatchTradeItem buildItem(String itemId,
                                            String displayLabel,
                                            TradeCommand command,
                                            @Nullable JSONObject extras) {
        return new BatchTradeItem(itemId, displayLabel, command, extras);
    }

    // 统一解析品种。
    private static String resolveSymbol(@NonNull PositionItem position) {
        String code = position.getCode() == null ? "" : position.getCode().trim();
        if (!code.isEmpty()) {
            return code;
        }
        String productName = position.getProductName() == null ? "" : position.getProductName().trim();
        if (!productName.isEmpty()) {
            return productName;
        }
        throw new IllegalArgumentException("symbol 不能为空");
    }

    // 统一校验持仓票号。
    private static long requirePositionTicket(@NonNull PositionItem position) {
        if (position.getPositionTicket() <= 0L) {
            throw new IllegalArgumentException("positionTicket 不能为空");
        }
        return position.getPositionTicket();
    }

    // 统一校验正数手数。
    private static double requirePositiveVolume(double volume, String message) {
        if (!Double.isFinite(volume) || volume <= 0d) {
            throw new IllegalArgumentException(message);
        }
        return volume;
    }

    // 统一规整账户模式。
    private static String normalizeAccountMode(@Nullable String accountMode) {
        return accountMode == null ? "" : accountMode.trim().toLowerCase(Locale.ROOT);
    }

    // 统一规整方向。
    private static String normalizeSide(@Nullable String side) {
        String normalized = side == null ? "" : side.trim().toLowerCase(Locale.ROOT);
        if ("long".equals(normalized)) {
            return "buy";
        }
        if ("short".equals(normalized)) {
            return "sell";
        }
        return normalized;
    }

    // 生成标准 batchId。
    private static String nextBatchId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    // 安全写入 JSON。
    private static void putQuietly(JSONObject target, String key, Object value) {
        if (target == null || key == null || key.trim().isEmpty()) {
            return;
        }
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
