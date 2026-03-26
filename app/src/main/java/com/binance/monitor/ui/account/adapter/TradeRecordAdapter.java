package com.binance.monitor.ui.account.adapter;

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
import java.util.List;
import java.util.Locale;

public class TradeRecordAdapter extends RecyclerView.Adapter<TradeRecordAdapter.Holder> {

    private final List<TradeRecordItem> items = new ArrayList<>();

    public void submitList(List<TradeRecordItem> data) {
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
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemTradeRecordBinding binding;

        Holder(ItemTradeRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TradeRecordItem item) {
            int sideColor = "BUY".equalsIgnoreCase(item.getSide()) ? R.color.accent_green : R.color.accent_red;
            binding.tvSummary.setText(String.format(Locale.getDefault(),
                    "%s | %s | %.2f 手 | $%s",
                    item.getProductName(),
                    item.getSide(),
                    item.getQuantity(),
                    FormatUtils.formatPrice(item.getAmount())));
            binding.tvExpandHint.setText("详情");
            binding.layoutDetail.setVisibility(View.VISIBLE);

            binding.tvTime.setText("时间: " + FormatUtils.formatDateTime(item.getTimestamp()));
            binding.tvProduct.setText("产品: " + item.getProductName() + " (" + item.getCode() + ")");
            binding.tvSide.setText("方向: " + item.getSide());
            binding.tvSide.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), sideColor));
            binding.tvDetail.setText(String.format(Locale.getDefault(),
                    "价格 $%s | 数量 %.2f | 成交额 $%s | 手续费 $%s",
                    FormatUtils.formatPrice(item.getPrice()),
                    item.getQuantity(),
                    FormatUtils.formatPrice(item.getAmount()),
                    FormatUtils.formatPrice(item.getFee())));
            binding.tvRemark.setText("备注: " + item.getRemark());
        }
    }
}
