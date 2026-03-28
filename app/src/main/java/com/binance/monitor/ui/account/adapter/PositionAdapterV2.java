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
    private final List<PositionItem> items = new ArrayList<>();
    private final List<String> rowKeys = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();

    public void submitList(List<PositionItem> data) {
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

    private List<String> buildRowKeys(List<PositionItem> data) {
        List<String> keys = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return keys;
        }
        Map<String, Integer> occurrence = new HashMap<>();
        for (PositionItem item : data) {
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
        return new Holder(ItemPositionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PositionItem item = items.get(position);
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

    private String baseKeyOf(PositionItem item) {
        if (item == null) {
            return "";
        }
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long cost = Math.round(item.getCostPrice() * 100d);
        long tp = Math.round(item.getTakeProfit() * 100d);
        long sl = Math.round(item.getStopLoss() * 100d);
        return item.getCode() + "|" + item.getSide() + "|" + qty + "|" + cost + "|" + tp + "|" + sl;
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemPositionBinding binding;

        Holder(ItemPositionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PositionItem item, boolean expanded) {
            double summaryPnl = item.getTotalPnL() + item.getStorageFee();
            int pnlColor = ContextCompat.getColor(binding.getRoot().getContext(),
                    summaryPnl >= 0d ? R.color.accent_green : R.color.accent_red);
            String pnlText = signedMoney(summaryPnl);
            String raw = String.format(Locale.getDefault(), "%s | %s | %.2f 手 | %s",
                    item.getProductName(), sideCn(item.getSide()), item.getQuantity(), pnlText);
            SpannableString span = new SpannableString(raw);
            int start = raw.lastIndexOf(pnlText);
            if (start >= 0) {
                span.setSpan(new ForegroundColorSpan(pnlColor), start, raw.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvSummary.setText(span);

            binding.tvExpandHint.setText(expanded ? "收起" : "展开");
            binding.layoutDetail.setVisibility(expanded ? View.VISIBLE : View.GONE);

            binding.tvProduct.setVisibility(View.GONE);
            binding.tvBase.setText(String.format(Locale.getDefault(),
                    "成本 $%s | 最新 $%s",
                    FormatUtils.formatPrice(item.getCostPrice()),
                    FormatUtils.formatPrice(item.getLatestPrice())));
            binding.tvMetrics.setText(String.format(Locale.getDefault(),
                    "止盈 %s | 止损 %s | 库存费 %s",
                    optionalPrice(item.getTakeProfit()),
                    optionalPrice(item.getStopLoss()),
                    signedMoney(item.getStorageFee())));
            binding.tvPnL.setText(String.format(Locale.getDefault(),
                    "当日 %s | 累计 %s | 收益率 %+.2f%%",
                    signedMoney(item.getDayPnL()),
                    signedMoney(item.getTotalPnL()),
                    item.getReturnRate() * 100d));
            binding.tvPnL.setTextColor(pnlColor);
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
    }
}
