package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemPositionBinding;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PositionAdapter extends RecyclerView.Adapter<PositionAdapter.Holder> {

    private final List<PositionItem> items = new ArrayList<>();

    public void submitList(List<PositionItem> data) {
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
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemPositionBinding binding;

        Holder(ItemPositionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PositionItem item) {
            binding.tvSummary.setText(String.format(Locale.getDefault(),
                    "%s | %s | %.2f 手 | %s",
                    item.getProductName(),
                    item.getSide(),
                    item.getQuantity(),
                    signedMoney(item.getTotalPnL())));
            binding.btnPositionAction.setVisibility(View.GONE);
            binding.layoutDetail.setVisibility(View.VISIBLE);
            binding.tvProduct.setText(item.getProductName() + " (" + item.getCode() + ")");
            binding.tvBase.setText(String.format(Locale.getDefault(),
                    "持仓 %.2f | 可卖 %.2f | 成本 $%s | 最新 $%s",
                    item.getQuantity(),
                    item.getSellableQuantity(),
                    FormatUtils.formatPrice(item.getCostPrice()),
                    FormatUtils.formatPrice(item.getLatestPrice())));
            binding.tvMetrics.setText(String.format(Locale.getDefault(),
                    "市值 $%s | 占比 %.2f%% | 挂单 %d 笔 %.2f 手",
                    FormatUtils.formatPrice(item.getMarketValue()),
                    item.getPositionRatio() * 100d,
                    item.getPendingCount(),
                    item.getPendingLots()));
            binding.tvPnL.setText(String.format(Locale.getDefault(),
                    "当日 %s | 累计 %s | 收益率 %+.2f%%",
                    signedMoney(item.getDayPnL()),
                    signedMoney(item.getTotalPnL()),
                    item.getReturnRate() * 100d));
            int color = item.getTotalPnL() >= 0d ? R.color.accent_green : R.color.accent_red;
            binding.tvPnL.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), color));
        }

        private String signedMoney(double value) {
            String sign = value >= 0d ? "+" : "-";
            return sign + "$" + FormatUtils.formatPrice(Math.abs(value));
        }
    }
}
