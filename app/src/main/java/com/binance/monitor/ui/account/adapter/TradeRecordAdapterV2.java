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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TradeRecordAdapterV2 extends RecyclerView.Adapter<TradeRecordAdapterV2.Holder> {
    private final List<TradeRecordItem> items = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();

    public void submitList(List<TradeRecordItem> data) {
        Set<String> next = new HashSet<>();
        if (data != null) {
            for (TradeRecordItem item : data) {
                String key = keyOf(item);
                if (expandedKeys.contains(key)) {
                    next.add(key);
                }
            }
        }
        expandedKeys.clear();
        expandedKeys.addAll(next);

        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemTradeRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TradeRecordItem item = items.get(position);
        boolean expanded = expandedKeys.contains(keyOf(item));
        holder.bind(item, expanded);
        holder.binding.layoutHeader.setOnClickListener(v -> {
            String key = keyOf(item);
            if (expandedKeys.contains(key)) {
                expandedKeys.remove(key);
            } else {
                expandedKeys.add(key);
            }
            notifyItemChanged(holder.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String keyOf(TradeRecordItem item) {
        return item.getTimestamp() + "|" + item.getCode() + "|" + item.getSide();
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
            String amount = "$" + FormatUtils.formatPrice(item.getAmount());
            String raw = String.format(Locale.getDefault(), "%s | %s | %.2f 手 | %s",
                    item.getProductName(), sideCn(item.getSide()), item.getQuantity(), amount);
            SpannableString span = new SpannableString(raw);
            int start = raw.lastIndexOf(amount);
            if (start >= 0) {
                span.setSpan(new ForegroundColorSpan(sideColor), start, raw.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvSummary.setText(span);
            binding.tvExpandHint.setText(expanded ? "收起" : "展开");
            binding.layoutDetail.setVisibility(expanded ? View.VISIBLE : View.GONE);

            binding.tvTime.setText("时间: " + FormatUtils.formatDateTime(item.getTimestamp()));
            binding.tvProduct.setText("产品: " + item.getProductName() + " (" + item.getCode() + ")");
            binding.tvSide.setText("方向: " + sideCn(item.getSide()));
            binding.tvSide.setTextColor(sideColor);
            binding.tvDetail.setText(String.format(Locale.getDefault(),
                    "价格 $%s | 数量 %.2f | 成交额 $%s | 手续费 $%s",
                    FormatUtils.formatPrice(item.getPrice()),
                    item.getQuantity(),
                    FormatUtils.formatPrice(item.getAmount()),
                    FormatUtils.formatPrice(item.getFee())));
            binding.tvRemark.setText("备注: " + item.getRemark());
        }

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }
    }
}
