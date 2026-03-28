package com.binance.monitor.ui.account.adapter;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemPositionBinding;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.util.FormatUtils;

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

    private final List<PositionItem> items = new ArrayList<>();
    private final List<String> rowKeys = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();

    public void submitList(List<PositionItem> data) {
        List<PositionItem> nextItems = data == null ? new ArrayList<>() : new ArrayList<>(data);
        List<String> nextRowKeys = buildRowKeys(nextItems);
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
                nextItems,
                nextRowKeys));

        expandedKeys.clear();
        expandedKeys.addAll(nextExpanded);
        items.clear();
        rowKeys.clear();
        items.addAll(nextItems);
        rowKeys.addAll(nextRowKeys);
        diffResult.dispatchUpdatesTo(this);
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
        holder.bind(item, expanded, false);
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
        holder.bind(item, expanded, hasPayload(payloads, PAYLOAD_EXPAND_STATE));
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

    private String contentKeyOf(PositionItem item) {
        if (item == null) {
            return "";
        }
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long sellable = Math.round(Math.abs(item.getSellableQuantity()) * 10_000d);
        long latest = Math.round(item.getLatestPrice() * 100d);
        long marketValue = Math.round(item.getMarketValue() * 100d);
        long positionRatio = Math.round(item.getPositionRatio() * 1_000_000d);
        long dayPnl = Math.round(item.getDayPnL() * 100d);
        long totalPnl = Math.round(item.getTotalPnL() * 100d);
        long returnRate = Math.round(item.getReturnRate() * 1_000_000d);
        long storage = Math.round(item.getStorageFee() * 100d);
        return identityKeyOf(item) + "|" + qty + "|" + sellable + "|" + latest + "|" + marketValue + "|"
                + positionRatio + "|" + dayPnl + "|" + totalPnl + "|" + returnRate + "|" + storage;
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

    private final class RowDiffCallback extends DiffUtil.Callback {
        private final List<PositionItem> oldItems;
        private final List<String> oldKeys;
        private final List<PositionItem> newItems;
        private final List<String> newKeys;

        private RowDiffCallback(List<PositionItem> oldItems,
                                List<String> oldKeys,
                                List<PositionItem> newItems,
                                List<String> newKeys) {
            this.oldItems = oldItems;
            this.oldKeys = oldKeys;
            this.newItems = newItems;
            this.newKeys = newKeys;
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
            return contentKeyOf(oldItems.get(oldItemPosition))
                    .equals(contentKeyOf(newItems.get(newItemPosition)));
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

        void bind(PositionItem item, boolean expanded, boolean animateExpand) {
            String sideText = sideCn(item.getSide());
            int sideColor = resolveSideColor(binding.getRoot(), item.getSide());
            double summaryPnl = item.getTotalPnL() + item.getStorageFee();
            int pnlColor = ContextCompat.getColor(binding.getRoot().getContext(),
                    summaryPnl >= 0d ? R.color.accent_green : R.color.accent_red);
            String pnlText = signedMoney(summaryPnl);
            String raw = String.format(Locale.getDefault(), "%s | %s | %.2f 手 | %s",
                    item.getProductName(), sideText, item.getQuantity(), pnlText);
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
            updateExpandState(expanded, animateExpand);

            binding.tvProduct.setVisibility(View.GONE);
            binding.tvBase.setText(String.format(Locale.getDefault(),
                    "成本 $%s | 最新 $%s",
                    FormatUtils.formatPrice(item.getCostPrice()),
                    FormatUtils.formatPrice(item.getLatestPrice())));
            String storageFeeText = signedMoney(item.getStorageFee());
            String metricsRaw = String.format(Locale.getDefault(),
                    "止盈 %s | 止损 %s | 库存费 %s",
                    optionalPrice(item.getTakeProfit()),
                    optionalPrice(item.getStopLoss()),
                    storageFeeText);
            SpannableStringBuilder metricsSpan = new SpannableStringBuilder(metricsRaw);
            int storageStart = metricsRaw.lastIndexOf(storageFeeText);
            if (storageStart >= 0) {
                metricsSpan.setSpan(new ForegroundColorSpan(resolveValueColor(binding.getRoot(), item.getStorageFee())),
                        storageStart,
                        storageStart + storageFeeText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvMetrics.setText(metricsSpan);
            String totalPnlText = signedMoney(item.getTotalPnL());
            String returnRateText = String.format(Locale.getDefault(), "%+.2f%%", item.getReturnRate() * 100d);
            String pnlRaw = String.format(Locale.getDefault(),
                    "盈亏 %s | 收益率 %s",
                    totalPnlText,
                    returnRateText);
            SpannableStringBuilder pnlSpan = new SpannableStringBuilder(pnlRaw);
            int totalPnlStart = pnlRaw.indexOf(totalPnlText);
            if (totalPnlStart >= 0) {
                pnlSpan.setSpan(new ForegroundColorSpan(resolveValueColor(binding.getRoot(), item.getTotalPnL())),
                        totalPnlStart,
                        totalPnlStart + totalPnlText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int returnRateStart = pnlRaw.lastIndexOf(returnRateText);
            if (returnRateStart >= 0) {
                pnlSpan.setSpan(new ForegroundColorSpan(resolveRatioColor(binding.getRoot(), item.getReturnRate())),
                        returnRateStart,
                        returnRateStart + returnRateText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvPnL.setText(pnlSpan);
            binding.tvPnL.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
        }

        private void updateExpandState(boolean expanded, boolean animate) {
            binding.tvExpandHint.setText(expanded ? "收起" : "展开");
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

        private static String optionalPrice(double value) {
            if (value <= 0d) {
                return "--";
            }
            return "$" + FormatUtils.formatPrice(value);
        }

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }

        private static int resolveSideColor(View root, String side) {
            return ContextCompat.getColor(root.getContext(),
                    "buy".equalsIgnoreCase(side) ? R.color.accent_green : R.color.accent_red);
        }

        private static int resolveValueColor(View root, double value) {
            int colorRes = value > 0d
                    ? R.color.accent_green
                    : (value < 0d ? R.color.accent_red : R.color.text_secondary);
            return ContextCompat.getColor(root.getContext(), colorRes);
        }

        private static int resolveRatioColor(View root, double value) {
            int colorRes = value > 0d
                    ? R.color.accent_green
                    : (value < 0d ? R.color.accent_red : R.color.text_secondary);
            return ContextCompat.getColor(root.getContext(), colorRes);
        }
    }
}
