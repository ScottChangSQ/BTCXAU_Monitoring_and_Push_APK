package com.binance.monitor.ui.account.adapter;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.databinding.ItemPositionBinding;
import com.binance.monitor.ui.account.AccountValueStyleHelper;
import com.binance.monitor.ui.account.PositionAggregateItem;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.SensitiveDisplayMasker;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PositionAggregateAdapter extends RecyclerView.Adapter<PositionAggregateAdapter.Holder> {
    private final List<PositionAggregateItem> items = new ArrayList<>();
    private boolean masked;

    public void submitList(List<PositionAggregateItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    // 账户持仓相关页面关闭隐私显示时，用于统一把数量、成本和盈亏替换为星号。
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

        void bind(PositionAggregateItem item, boolean masked) {
            UiPaletteManager.Palette palette = UiPaletteManager.resolve(binding.getRoot().getContext());
            binding.btnPositionCloseAction.setVisibility(View.GONE);
            binding.btnPositionModifyAction.setVisibility(View.GONE);
            binding.btnPositionDeleteAction.setVisibility(View.GONE);
            if (masked) {
                binding.tvSummary.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvSummary.setTextColor(palette.textPrimary);
                binding.layoutHeader.setBackground(UiPaletteManager.createListRowBackground(
                        binding.getRoot().getContext(),
                        palette.card,
                        palette.stroke
                ));
                binding.tvProduct.setVisibility(View.GONE);
                binding.tvBase.setVisibility(View.GONE);
                binding.tvMetrics.setVisibility(View.GONE);
                binding.tvPnL.setVisibility(View.GONE);
                binding.layoutDetail.setVisibility(View.GONE);
                return;
            }
            int pnlColor = resolveAmountColor(palette, item.getTotalPnl());
            String pnlText = signedMoney(item.getTotalPnl());
            String qtyText = String.format(Locale.getDefault(), "%.2f 手", item.getQuantity());
            String costText = "$" + FormatUtils.formatPrice(item.getAverageCostPrice());
            String sideText = sideCn(item.getSide());
            String raw = String.format(Locale.getDefault(),
                    "%s | %s | %s | 成本 %s | %s",
                    item.getProductName(),
                    sideText,
                    qtyText,
                    costText,
                    pnlText);
            SpannableString span = new SpannableString(raw);
            int sideStart = raw.indexOf(sideText);
            if (sideStart >= 0) {
                span.setSpan(new ForegroundColorSpan(resolveSideColor(palette, sideText)),
                        sideStart,
                        sideStart + sideText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int start = raw.lastIndexOf(pnlText);
            if (start >= 0) {
                span.setSpan(new ForegroundColorSpan(pnlColor), start, raw.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvSummary.setTextColor(palette.textPrimary);
            binding.tvSummary.setText(span);
            binding.layoutHeader.setBackground(UiPaletteManager.createListRowBackground(
                    binding.getRoot().getContext(),
                    palette.card,
                    palette.stroke
            ));
            binding.tvProduct.setVisibility(View.GONE);
            binding.tvBase.setVisibility(View.GONE);
            binding.tvMetrics.setVisibility(View.GONE);
            binding.tvPnL.setVisibility(View.GONE);
            binding.layoutDetail.setVisibility(View.GONE);
        }

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }

        private int resolveSideColor(@NonNull UiPaletteManager.Palette palette, String sideText) {
            return "买入".equals(sideText) ? palette.rise : palette.fall;
        }

        private static String signedMoney(double value) {
            return (value >= 0d ? "+" : "-") + "$" + FormatUtils.formatPrice(Math.abs(value));
        }

        private int resolveAmountColor(@NonNull UiPaletteManager.Palette palette, double value) {
            AccountValueStyleHelper.Direction direction = AccountValueStyleHelper.resolveNumericDirection(value);
            if (direction == AccountValueStyleHelper.Direction.POSITIVE) {
                return palette.rise;
            }
            if (direction == AccountValueStyleHelper.Direction.NEGATIVE) {
                return palette.fall;
            }
            return palette.textPrimary;
        }
    }

}
