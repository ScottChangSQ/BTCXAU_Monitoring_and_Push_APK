package com.binance.monitor.ui.account.adapter;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemPositionBinding;
import com.binance.monitor.ui.account.AccountValueStyleHelper;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStore;
import com.binance.monitor.runtime.state.model.ProductRuntimeSnapshot;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PositionAdapterV2 extends RecyclerView.Adapter<PositionAdapterV2.Holder> {
    private static final String PAYLOAD_EXPAND_STATE = "payload_expand_state";
    private static final String PAYLOAD_CONTENT_STATE = "payload_content_state";

    private final UnifiedRuntimeSnapshotStore runtimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();
    private final List<PositionItem> items = new ArrayList<>();
    private final List<String> rowKeys = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();
    private final Map<String, Integer> productCounts = new HashMap<>();
    private boolean masked;
    private ActionListener actionListener;

    public interface ActionListener {
        void onCloseRequested(PositionItem item);

        void onModifyRequested(PositionItem item);
    }

    // 注册持仓操作回调，供图表页接入平仓和改单入口。
    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submitList(List<PositionItem> data) {
        List<PositionItem> nextItems = data == null ? new ArrayList<>() : new ArrayList<>(data);
        List<String> nextRowKeys = buildRowKeys(nextItems);
        Map<String, Integer> previousProductCounts = new HashMap<>(productCounts);
        Map<String, Integer> nextProductCounts = buildProductCounts(nextItems);
        Set<String> nextExpanded = new HashSet<>();
        for (String key : nextRowKeys) {
            if (expandedKeys.contains(key)) {
                nextExpanded.add(key);
            }
        }

        List<PositionItem> previousItems = new ArrayList<>(items);
        List<String> previousRowKeys = new ArrayList<>(rowKeys);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new RowDiffCallback(
                previousItems,
                previousRowKeys,
                previousProductCounts,
                nextItems,
                nextRowKeys,
                nextProductCounts));

        expandedKeys.clear();
        expandedKeys.addAll(nextExpanded);
        items.clear();
        rowKeys.clear();
        productCounts.clear();
        productCounts.putAll(buildProductCounts(nextItems));
        items.addAll(nextItems);
        rowKeys.addAll(nextRowKeys);
        diffResult.dispatchUpdatesTo(this);
    }

    // 账户持仓相关页面关闭隐私显示时，统一把持仓数量、价格、盈亏和收益率打码。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        notifyDataSetChanged();
    }

    private List<String> buildRowKeys(List<PositionItem> data) {
        List<String> keys = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return keys;
        }
        Map<String, Integer> occurrence = new HashMap<>();
        for (PositionItem item : data) {
            String base = identityKeyOf(item);
            int index = occurrence.containsKey(base) ? occurrence.get(base) : 0;
            occurrence.put(base, index + 1);
            keys.add(base + "#" + index);
        }
        return keys;
    }

    private Map<String, Integer> buildProductCounts(List<PositionItem> data) {
        Map<String, Integer> counts = new HashMap<>();
        if (data == null || data.isEmpty()) {
            return counts;
        }
        for (PositionItem item : data) {
            String productSymbol = buildProductSymbolKey(item);
            if (productSymbol.isEmpty()) {
                continue;
            }
            counts.put(productSymbol, counts.getOrDefault(productSymbol, 0) + 1);
        }
        return counts;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPositionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PositionItem item = items.get(position);
        String rowKey = rowKeys.get(position);
        boolean expanded = expandedKeys.contains(rowKey);
        ProductRuntimeSnapshot runtimeSnapshot = runtimeSnapshotStore.selectProduct(buildProductSymbolKey(item));
        int productCount = resolveProductCount(productCounts, item);
        holder.bind(item, expanded, false, masked, actionListener != null, productCount, runtimeSnapshot);
        holder.binding.layoutHeader.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= rowKeys.size()) {
                return;
            }
            String key = rowKeys.get(adapterPosition);
            if (expandedKeys.contains(key)) {
                expandedKeys.remove(key);
            } else {
                expandedKeys.add(key);
            }
            notifyItemChanged(adapterPosition, PAYLOAD_EXPAND_STATE);
        });
        holder.binding.btnPositionCloseAction.setOnClickListener(v -> {
            ActionListener listener = actionListener;
            if (listener == null) {
                return;
            }
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= items.size()) {
                return;
            }
            listener.onCloseRequested(items.get(adapterPosition));
        });
        holder.binding.btnPositionModifyAction.setOnClickListener(v -> {
            ActionListener listener = actionListener;
            if (listener == null) {
                return;
            }
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= items.size()) {
                return;
            }
            listener.onModifyRequested(items.get(adapterPosition));
        });
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        PositionItem item = items.get(position);
        String rowKey = rowKeys.get(position);
        boolean expanded = expandedKeys.contains(rowKey);
        ProductRuntimeSnapshot runtimeSnapshot = runtimeSnapshotStore.selectProduct(buildProductSymbolKey(item));
        int productCount = resolveProductCount(productCounts, item);
        holder.bind(item,
                expanded,
                hasPayload(payloads, PAYLOAD_EXPAND_STATE),
                masked,
                actionListener != null,
                productCount,
                runtimeSnapshot);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String identityKeyOf(PositionItem item) {
        if (item == null) {
            return "";
        }
        if (item.getPositionTicket() > 0L) {
            return "position:" + item.getPositionTicket();
        }
        if (item.getOrderId() > 0L) {
            return "order:" + item.getOrderId();
        }
        long cost = Math.round(item.getCostPrice() * 100d);
        long tp = Math.round(item.getTakeProfit() * 100d);
        long sl = Math.round(item.getStopLoss() * 100d);
        return safe(item.getCode()) + "|" + safe(item.getSide()) + "|" + cost + "|" + tp + "|" + sl
                + "|" + safe(item.getProductName());
    }

    private String contentKeyOf(PositionItem item, @NonNull Map<String, Integer> counts) {
        if (item == null) {
            return "";
        }
        String productName = safe(item.getProductName());
        String side = safe(item.getSide());
        long openTime = item.getOpenTime();
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long sellable = Math.round(Math.abs(item.getSellableQuantity()) * 10_000d);
        long costPrice = Math.round(item.getCostPrice() * 100d);
        long latest = Math.round(item.getLatestPrice() * 100d);
        long marketValue = Math.round(item.getMarketValue() * 100d);
        long positionRatio = Math.round(item.getPositionRatio() * 1_000_000d);
        long dayPnl = Math.round(item.getDayPnL() * 100d);
        long totalPnl = Math.round(item.getTotalPnL() * 100d);
        long returnRate = Math.round(item.getReturnRate() * 1_000_000d);
        long takeProfit = Math.round(item.getTakeProfit() * 100d);
        long stopLoss = Math.round(item.getStopLoss() * 100d);
        long storage = Math.round(item.getStorageFee() * 100d);
        ProductRuntimeSnapshot runtimeSnapshot = runtimeSnapshotStore.selectProduct(buildProductSymbolKey(item));
        int productCount = resolveProductCount(counts, item);
        String runtimePositionSignature = productCount == 1 && runtimeSnapshot.getPositionCount() == 1
                ? runtimeSnapshot.getSymbol() + "|" + runtimeSnapshot.getProductRevision()
                + "|" + Math.round(runtimeSnapshot.getTotalLots() * 10_000d)
                + "|" + Math.round(runtimeSnapshot.getNetPnl() * 100d)
                : "";
        return identityKeyOf(item) + "|" + qty + "|" + sellable + "|" + latest + "|" + marketValue + "|"
                + positionRatio + "|" + dayPnl + "|" + totalPnl + "|" + returnRate + "|"
                + openTime + "|" + costPrice + "|" + takeProfit + "|" + stopLoss + "|" + storage
                + "|" + productName + "|" + side + "|" + runtimePositionSignature;
    }

    private boolean hasPayload(List<Object> payloads, String expected) {
        for (Object payload : payloads) {
            if (expected.equals(payload)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @NonNull
    private String buildProductSymbolKey(@Nullable PositionItem item) {
        if (item == null) {
            return "";
        }
        String symbol = safe(item.getCode()).trim();
        if (symbol.isEmpty()) {
            symbol = safe(item.getProductName()).trim();
        }
        return symbol.toUpperCase(Locale.US);
    }

    private int resolveProductCount(@NonNull Map<String, Integer> counts, @Nullable PositionItem item) {
        String productSymbol = buildProductSymbolKey(item);
        if (productSymbol.isEmpty()) {
            return 0;
        }
        Integer count = counts.get(productSymbol);
        return count == null ? 0 : count;
    }

    private final class RowDiffCallback extends DiffUtil.Callback {
        private final List<PositionItem> oldItems;
        private final List<String> oldKeys;
        private final Map<String, Integer> oldProductCounts;
        private final List<PositionItem> newItems;
        private final List<String> newKeys;
        private final Map<String, Integer> newProductCounts;

        private RowDiffCallback(List<PositionItem> oldItems,
                                List<String> oldKeys,
                                Map<String, Integer> oldProductCounts,
                                List<PositionItem> newItems,
                                List<String> newKeys,
                                Map<String, Integer> newProductCounts) {
            this.oldItems = oldItems;
            this.oldKeys = oldKeys;
            this.oldProductCounts = oldProductCounts;
            this.newItems = newItems;
            this.newKeys = newKeys;
            this.newProductCounts = newProductCounts;
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldKeys.get(oldItemPosition).equals(newKeys.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return contentKeyOf(oldItems.get(oldItemPosition), oldProductCounts)
                    .equals(contentKeyOf(newItems.get(newItemPosition), newProductCounts));
        }

        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            return PAYLOAD_CONTENT_STATE;
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemPositionBinding binding;

        Holder(ItemPositionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PositionItem item,
                  boolean expanded,
                  boolean animateExpand,
                  boolean masked,
                  boolean actionEnabled,
                  int productCount,
                  @NonNull ProductRuntimeSnapshot runtimeSnapshot) {
            enforceSummarySingleLine();
            binding.btnPositionCloseAction.setVisibility(actionEnabled ? View.VISIBLE : View.GONE);
            binding.btnPositionModifyAction.setVisibility(actionEnabled ? View.VISIBLE : View.GONE);
            binding.btnPositionDeleteAction.setVisibility(View.GONE);
            if (masked) {
                binding.tvSummary.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvSummary.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                binding.layoutHeader.setBackgroundResource(expanded
                        ? R.drawable.bg_position_row_expanded
                        : R.drawable.bg_position_row_collapsed);
                updateExpandState(expanded, animateExpand);
                binding.tvProduct.setVisibility(View.GONE);
                binding.tvBase.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvMetrics.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvPnL.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvBase.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
                binding.tvMetrics.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
                binding.tvPnL.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
                return;
            }
            String sideText = sideCn(item.getSide());
            int sideColor = resolveSideColor(binding.getRoot(), item.getSide());
            boolean useRuntimeSummary = shouldUseRuntimePositionSummary(productCount, runtimeSnapshot);
            double displayPnl = item.getTotalPnL() + item.getStorageFee();
            if (useRuntimeSummary) {
                displayPnl = runtimeSnapshot.getNetPnl();
            }
            int pnlColor = resolveAmountColor(binding.getRoot(), displayPnl, R.color.text_primary);
            String pnlText = signedMoney(displayPnl);
            double displayQty = useRuntimeSummary
                    ? runtimeSnapshot.getTotalLots()
                    : Math.abs(item.getQuantity());
            String qtyText = String.format(Locale.getDefault(), "%.2f 手", displayQty);
            String raw = String.format(Locale.getDefault(), "%s | %s | %s | %s",
                    resolveDisplayProductName(item, runtimeSnapshot), sideText, qtyText, pnlText);
            SpannableStringBuilder span = new SpannableStringBuilder(raw);
            int sideStart = raw.indexOf(sideText);
            if (sideStart >= 0) {
                span.setSpan(new ForegroundColorSpan(sideColor),
                        sideStart,
                        sideStart + sideText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int summaryStart = raw.lastIndexOf(pnlText);
            if (summaryStart >= 0) {
                span.setSpan(new ForegroundColorSpan(pnlColor),
                        summaryStart,
                        summaryStart + pnlText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvSummary.setText(span);
            binding.layoutHeader.setBackgroundResource(expanded
                    ? R.drawable.bg_position_row_expanded
                    : R.drawable.bg_position_row_collapsed);
            updateExpandState(expanded, animateExpand);

            binding.tvProduct.setVisibility(View.VISIBLE);
            binding.tvProduct.setText(String.format(Locale.getDefault(),
                    "开仓时间 %s",
                    formatOpenTime(item.getOpenTime())));
            String costText = "$" + FormatUtils.formatPrice(item.getCostPrice());
            String latestText = "$" + FormatUtils.formatPrice(item.getLatestPrice());
            binding.tvBase.setText(String.format(Locale.getDefault(),
                    "成本 %s | 最新 %s",
                    costText,
                    latestText));
            String storageFeeText = signedMoney(item.getStorageFee());
            String metricsRaw = String.format(Locale.getDefault(),
                    "止盈 %s | 止损 %s | 库存费 %s",
                    optionalPrice(item.getTakeProfit()),
                    optionalPrice(item.getStopLoss()),
                    storageFeeText);
            SpannableStringBuilder metricsSpan = new SpannableStringBuilder(metricsRaw);
            int storageStart = metricsRaw.lastIndexOf(storageFeeText);
            if (storageStart >= 0) {
                metricsSpan.setSpan(new ForegroundColorSpan(resolveAmountColor(binding.getRoot(), item.getStorageFee(), R.color.text_secondary)),
                        storageStart,
                        storageStart + storageFeeText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvMetrics.setText(metricsSpan);
            String totalPnlText = signedMoney(displayPnl);
            double displayReturnRate = resolveDisplayReturnRate(item);
            String returnRateText = String.format(Locale.getDefault(), "%+.2f%%", displayReturnRate * 100d);
            String pnlRaw = String.format(Locale.getDefault(),
                    "持仓盈亏 %s | 收益率 %s",
                    totalPnlText,
                    returnRateText);
            SpannableStringBuilder pnlSpan = new SpannableStringBuilder(pnlRaw);
            int totalPnlStart = pnlRaw.indexOf(totalPnlText);
            if (totalPnlStart >= 0) {
                pnlSpan.setSpan(new ForegroundColorSpan(resolveAmountColor(binding.getRoot(), displayPnl, R.color.text_secondary)),
                        totalPnlStart,
                        totalPnlStart + totalPnlText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int returnRateStart = pnlRaw.lastIndexOf(returnRateText);
            if (returnRateStart >= 0) {
                pnlSpan.setSpan(new ForegroundColorSpan(resolveAmountColor(binding.getRoot(), displayReturnRate, R.color.text_secondary)),
                        returnRateStart,
                        returnRateStart + returnRateText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvPnL.setText(pnlSpan);
            binding.tvPnL.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
        }

        private static boolean shouldUseRuntimePositionSummary(int productCount,
                                                               @NonNull ProductRuntimeSnapshot runtimeSnapshot) {
            return productCount == 1 && runtimeSnapshot.getPositionCount() == 1;
        }

        @NonNull
        private static String resolveDisplayProductName(@NonNull PositionItem item,
                                                        @NonNull ProductRuntimeSnapshot runtimeSnapshot) {
            String productName = safe(item.getProductName()).trim();
            if (!productName.isEmpty()) {
                return productName;
            }
            if (!runtimeSnapshot.getDisplayLabel().isEmpty()) {
                return runtimeSnapshot.getDisplayLabel();
            }
            if (!runtimeSnapshot.getSymbol().isEmpty()) {
                return runtimeSnapshot.getSymbol();
            }
            return safe(item.getCode()).trim();
        }

        // 每次绑定时重新锁定摘要文本单行约束，避免 RecyclerView 复用后出现错误换行或多行高度。
        private void enforceSummarySingleLine() {
            binding.tvSummary.setIncludeFontPadding(false);
            binding.tvSummary.setSingleLine(true);
            binding.tvSummary.setLines(1);
            binding.tvSummary.setMinLines(1);
            binding.tvSummary.setMaxLines(1);
            binding.tvSummary.setHorizontallyScrolling(true);
            binding.tvSummary.setEllipsize(TextUtils.TruncateAt.END);
        }

        private void updateExpandState(boolean expanded, boolean animate) {
            View detail = binding.layoutDetail;
            detail.animate().cancel();
            if (!animate) {
                detail.setVisibility(expanded ? View.VISIBLE : View.GONE);
                detail.setAlpha(1f);
                detail.setTranslationY(0f);
                return;
            }
            if (expanded) {
                if (detail.getVisibility() == View.VISIBLE) {
                    return;
                }
                detail.setVisibility(View.VISIBLE);
                detail.setAlpha(0f);
                detail.setTranslationY(-8f);
                detail.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                if (detail.getVisibility() != View.VISIBLE) {
                    return;
                }
                detail.animate()
                        .alpha(0f)
                        .translationY(-6f)
                        .setDuration(140L)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            detail.setVisibility(View.GONE);
                            detail.setAlpha(1f);
                            detail.setTranslationY(0f);
                        })
                        .start();
            }
        }

        private static String signedMoney(double value) {
            return (value >= 0d ? "+" : "-") + "$" + FormatUtils.formatPrice(Math.abs(value));
        }

        private static String formatOpenTime(long openTime) {
            if (openTime <= 0L) {
                return "--";
            }
            return FormatUtils.formatDateTime(openTime);
        }

        private static String optionalPrice(double value) {
            if (value <= 0d) {
                return "--";
            }
            return "$" + FormatUtils.formatPrice(value);
        }

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }

        // 收益率优先按持仓成本重算，避免网关侧方向口径差异导致盈亏正负号颠倒。
        private static double resolveDisplayReturnRate(PositionItem item) {
            if (item == null) {
                return 0d;
            }
            double qty = Math.abs(item.getQuantity());
            double cost = Math.abs(item.getCostPrice());
            double pnl = item.getTotalPnL() + item.getStorageFee();
            if (qty > 1e-9 && cost > 1e-9) {
                return pnl / (qty * cost);
            }
            double source = item.getReturnRate();
            if (Math.abs(pnl) > 1e-9 && Math.abs(source) > 1e-9 && Math.signum(source) != Math.signum(pnl)) {
                return Math.copySign(Math.abs(source), pnl);
            }
            return source;
        }

        private static int resolveSideColor(View root, String side) {
            return ContextCompat.getColor(root.getContext(),
                    "buy".equalsIgnoreCase(side) ? R.color.accent_green : R.color.accent_red);
        }

        private static int resolveAmountColor(View root, double value, int neutralColorRes) {
            AccountValueStyleHelper.Direction direction = AccountValueStyleHelper.resolveNumericDirection(value);
            if (direction == AccountValueStyleHelper.Direction.POSITIVE) {
                return ContextCompat.getColor(root.getContext(), R.color.accent_green);
            }
            if (direction == AccountValueStyleHelper.Direction.NEGATIVE) {
                return ContextCompat.getColor(root.getContext(), R.color.accent_red);
            }
            return ContextCompat.getColor(root.getContext(), neutralColorRes);
        }
    }
}
