package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.databinding.ItemPositionBinding;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PendingOrderAdapter extends RecyclerView.Adapter<PendingOrderAdapter.Holder> {
    private final List<PositionItem> items = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();

    public void submitList(List<PositionItem> data) {
        Set<String> next = new HashSet<>();
        if (data != null) {
            for (PositionItem item : data) {
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
        return new Holder(ItemPositionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PositionItem item = items.get(position);
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

    private String keyOf(PositionItem item) {
        return item.getCode() + "|" + item.getSide();
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemPositionBinding binding;

        Holder(ItemPositionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PositionItem item, boolean expanded) {
            double pendingPrice = item.getPendingPrice() > 0d ? item.getPendingPrice() : item.getLatestPrice();
            binding.tvSummary.setText(String.format(Locale.getDefault(),
                    "%s | %s | %.2f 手 | 挂单价位 $%s",
                    item.getProductName(),
                    sideCn(item.getSide()),
                    item.getPendingLots(),
                    FormatUtils.formatPrice(pendingPrice)));

            binding.tvExpandHint.setText(expanded ? "收起" : "展开");
            binding.layoutDetail.setVisibility(expanded ? View.VISIBLE : View.GONE);

            binding.tvProduct.setText(String.format(Locale.getDefault(),
                    "产品 %s (%s)",
                    item.getProductName(),
                    item.getCode()));
            binding.tvBase.setText(String.format(Locale.getDefault(),
                    "方向 %s | 挂单手数 %.2f | 挂单笔数 %d",
                    sideCn(item.getSide()),
                    item.getPendingLots(),
                    item.getPendingCount()));
            binding.tvMetrics.setText(String.format(Locale.getDefault(),
                    "挂单价位 $%s | 参考现价 $%s",
                    FormatUtils.formatPrice(pendingPrice),
                    FormatUtils.formatPrice(item.getLatestPrice())));
            binding.tvPnL.setVisibility(View.GONE);
        }

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }
    }
}
