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
import com.binance.monitor.ui.account.AccountValueStyleHelper;
import com.binance.monitor.util.SensitiveDisplayMasker;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PositionAggregateAdapter extends RecyclerView.Adapter<PositionAggregateAdapter.Holder> {
    private final List<AggregateItem> items = new ArrayList<>();
    private boolean masked;

    public void submitList(List<AggregateItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    // 行情持仓页关闭隐私显示时，用于统一把数量、成本和盈亏替换为星号。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPositionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position), masked);
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

        void bind(AggregateItem item, boolean masked) {
            if (masked) {
                binding.tvSummary.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvSummary.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                binding.tvExpandHint.setVisibility(View.GONE);
                binding.layoutDetail.setVisibility(View.GONE);
                return;
            }
            int pnlColor = resolveAmountColor(item.totalPnl);
            String pnlText = signedMoney(item.totalPnl);
            String qtyText = String.format(Locale.getDefault(), "%.2f 手", item.quantity);
            String costText = "$" + String.format(Locale.getDefault(), "%,.0f", item.avgCostPrice);
            String raw = String.format(Locale.getDefault(),
                    "%s | %s | 成本 %s | %s",
                    item.productName,
                    qtyText,
                    costText,
                    pnlText);
            SpannableString span = new SpannableString(raw);
            int start = raw.lastIndexOf(pnlText);
            if (start >= 0) {
                span.setSpan(new ForegroundColorSpan(pnlColor), start, raw.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvSummary.setText(span);

            binding.tvExpandHint.setVisibility(View.GONE);
            binding.layoutDetail.setVisibility(View.GONE);
        }

        private static String signedMoney(double value) {
            return (value >= 0d ? "+" : "-") + "$" + FormatUtils.formatPrice(Math.abs(value));
        }

        private int resolveAmountColor(double value) {
            AccountValueStyleHelper.Direction direction = AccountValueStyleHelper.resolveNumericDirection(value);
            if (direction == AccountValueStyleHelper.Direction.POSITIVE) {
                return ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_green);
            }
            if (direction == AccountValueStyleHelper.Direction.NEGATIVE) {
                return ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_red);
            }
            return ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary);
        }
    }

    public static class AggregateItem {
        public final String productName;
        public final String side;
        public final double quantity;
        public final double avgCostPrice;
        public final double totalPnl;

        public AggregateItem(String productName, String side, double quantity, double avgCostPrice, double totalPnl) {
            this.productName = productName;
            this.side = side;
            this.quantity = quantity;
            this.avgCostPrice = avgCostPrice;
            this.totalPnl = totalPnl;
        }
    }
}
