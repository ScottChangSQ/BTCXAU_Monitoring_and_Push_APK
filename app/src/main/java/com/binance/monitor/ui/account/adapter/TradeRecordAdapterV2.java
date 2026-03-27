package com.binance.monitor.ui.account.adapter;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemTradeRecordBinding;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TradeRecordAdapterV2 extends RecyclerView.Adapter<TradeRecordAdapterV2.Holder> {
    private final List<TradeRecordItem> items = new ArrayList<>();
    private final List<String> rowKeys = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();

    public void submitList(List<TradeRecordItem> data) {
        List<String> nextRowKeys = buildRowKeys(data);
        Set<String> nextExpanded = new HashSet<>();
        for (String key : nextRowKeys) {
            if (expandedKeys.contains(key)) {
                nextExpanded.add(key);
            }
        }
        expandedKeys.clear();
        expandedKeys.addAll(nextExpanded);

        items.clear();
        rowKeys.clear();
        if (data != null) {
            items.addAll(data);
            rowKeys.addAll(nextRowKeys);
        }
        notifyDataSetChanged();
    }

    private List<String> buildRowKeys(List<TradeRecordItem> data) {
        List<String> keys = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return keys;
        }
        Map<String, Integer> occurrence = new HashMap<>();
        for (TradeRecordItem item : data) {
            String base = baseKeyOf(item);
            int index = occurrence.containsKey(base) ? occurrence.get(base) : 0;
            occurrence.put(base, index + 1);
            keys.add(base + "#" + index);
        }
        return keys;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemTradeRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TradeRecordItem item = items.get(position);
        String rowKey = rowKeys.get(position);
        boolean expanded = expandedKeys.contains(rowKey);
        holder.bind(item, expanded);
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
            notifyItemChanged(adapterPosition);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String baseKeyOf(TradeRecordItem item) {
        if (item == null) {
            return "";
        }
        long openTime = item.getOpenTime() > 0L ? item.getOpenTime() : item.getTimestamp();
        long closeTime = item.getCloseTime() > 0L ? item.getCloseTime() : item.getTimestamp();
        long qtyKey = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long priceKey = Math.round(item.getPrice() * 100d);
        long amountKey = Math.round(item.getAmount() * 100d);
        long profitKey = Math.round(item.getProfit() * 100d);
        long feeKey = Math.round(item.getFee() * 100d);
        long storageKey = Math.round(item.getStorageFee() * 100d);
        return item.getCode() + "|" + item.getSide() + "|" + openTime + "|" + closeTime + "|" + qtyKey + "|"
                + priceKey + "|" + amountKey + "|" + profitKey + "|" + feeKey + "|" + storageKey;
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemTradeRecordBinding binding;

        Holder(ItemTradeRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TradeRecordItem item, boolean expanded) {
            int sideColor = ContextCompat.getColor(binding.getRoot().getContext(),
                    "BUY".equalsIgnoreCase(item.getSide()) ? R.color.accent_green : R.color.accent_red);
            int pnlColor = ContextCompat.getColor(binding.getRoot().getContext(),
                    item.getProfit() >= 0d ? R.color.accent_green : R.color.accent_red);
            String amount = signedMoney(item.getProfit());
            String raw = String.format(Locale.getDefault(), "%s | %s | %.2f 手 | %s",
                    item.getProductName(), sideCn(item.getSide()), item.getQuantity(), amount);
            SpannableString span = new SpannableString(raw);
            int start = raw.lastIndexOf(amount);
            if (start >= 0) {
                span.setSpan(new ForegroundColorSpan(pnlColor), start, raw.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvSummary.setText(span);
            binding.tvExpandHint.setText(expanded ? "收起" : "展开");
            binding.layoutDetail.setVisibility(expanded ? View.VISIBLE : View.GONE);

            long openTime = item.getOpenTime() > 0L ? item.getOpenTime() : item.getTimestamp();
            long closeTime = item.getCloseTime() > 0L ? item.getCloseTime() : item.getTimestamp();
            binding.tvTime.setText("开仓时间: " + FormatUtils.formatDateTime(openTime));
            binding.tvProduct.setText("平仓时间: " + FormatUtils.formatDateTime(closeTime));
            binding.tvSide.setText("产品: " + item.getProductName() + " (" + item.getCode() + ") | 方向: " + sideCn(item.getSide()));
            binding.tvSide.setTextColor(sideColor);
            binding.tvDetail.setText(String.format(Locale.getDefault(),
                    "价格 $%s | 手数 %.2f | 盈亏 %s | 库存费 $%s",
                    FormatUtils.formatPrice(item.getPrice()),
                    item.getQuantity(),
                    signedMoney(item.getProfit()),
                    FormatUtils.formatPrice(item.getStorageFee())));
            binding.tvRemark.setVisibility(View.GONE);
        }

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }

        private static String signedMoney(double value) {
            return (value >= 0d ? "+" : "-") + "$" + FormatUtils.formatPrice(Math.abs(value));
        }
    }
}
