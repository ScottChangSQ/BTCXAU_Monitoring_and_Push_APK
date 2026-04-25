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
import com.binance.monitor.ui.account.PositionAggregateItem;
import com.binance.monitor.ui.rules.IndicatorFormatterCenter;
import com.binance.monitor.ui.rules.IndicatorId;
import com.binance.monitor.ui.rules.IndicatorPresentationPolicy;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.SensitiveDisplayMasker;

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
            double displayLots = resolveDisplayLots(item);
            int directionColor = resolveDirectionColor(palette, displayLots);
            String titleText = item.getDisplayLabel().trim().isEmpty()
                    ? item.getCompactDisplayLabel().trim()
                    : item.getDisplayLabel().trim();
            String directionText = resolveDirectionText(item.getSignedLots());
            String lotsText = IndicatorFormatterCenter.formatQuantity(displayLots, 2, "手");
            String pnlText = formatSignedPnlValue(item.getNetPnl());
            String raw = String.format(Locale.getDefault(),
                    "%s | %s | %s | %s",
                    titleText,
                    directionText,
                    lotsText,
                    pnlText);
            SpannableString span = new SpannableString(raw);
            int directionStart = raw.indexOf(directionText);
            if (directionStart >= 0) {
                span.setSpan(new ForegroundColorSpan(directionColor),
                        directionStart,
                        directionStart + directionText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int start = raw.lastIndexOf(pnlText);
            if (start >= 0) {
                IndicatorPresentationPolicy.applyDirectionalSpanForExactToken(
                        span,
                        binding.getRoot().getContext(),
                        raw,
                        pnlText,
                        IndicatorId.ACCOUNT_POSITION_PNL,
                        com.binance.monitor.R.color.text_primary,
                        true
                );
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

        // 账户页按产品统计的手数必须展示买卖相抵后的净手数，不能继续显示绝对值简单相加。
        private double resolveDisplayLots(@NonNull PositionAggregateItem item) {
            return isZero(item.getSignedLots()) ? 0d : item.getSignedLots();
        }

        private int resolveDirectionColor(@NonNull UiPaletteManager.Palette palette,
                                          double displayLots) {
            if (isZero(displayLots)) {
                return palette.textSecondary;
            }
            return displayLots > 0d ? palette.rise : palette.fall;
        }

        @NonNull
        private String resolveDirectionText(double displayLots) {
            if (isZero(displayLots)) {
                return "--";
            }
            if (displayLots > 0d) {
                return "买入";
            }
            return "卖出";
        }

        private boolean isZero(double value) {
            return Math.abs(value) < 1e-9;
        }

        // 按产品统计里的盈亏只保留正负数字，不再带货币符号，避免摘要过长挤压右侧按钮区。
        @NonNull
        private String formatSignedPnlValue(double value) {
            if (isZero(value)) {
                return "0.00";
            }
            return String.format(Locale.getDefault(), "%+.2f", value);
        }
    }

}
