package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemStatsMetricBinding;
import com.binance.monitor.ui.account.AccountValueStyleHelper;
import com.binance.monitor.runtime.account.MetricNameTranslator;
import com.binance.monitor.ui.account.TradeStatsMetricStyleHelper;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.util.SensitiveDisplayMasker;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.List;
public class StatsMetricAdapter extends RecyclerView.Adapter<StatsMetricAdapter.Holder> {

    private final List<AccountMetric> items = new ArrayList<>();
    private boolean masked;

    public void submitList(List<AccountMetric> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

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
        return new Holder(ItemStatsMetricBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemStatsMetricBinding binding;

        Holder(ItemStatsMetricBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountMetric item, boolean masked) {
            String nameCn = MetricNameTranslator.toChinese(item.getName());
            String value = masked
                    ? SensitiveDisplayMasker.maskValue(item.getValue(), true)
                    : item.getValue();
            binding.tvName.setText(nameCn);
            if (masked) {
                binding.tvValue.setText(value);
                binding.tvValue.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                return;
            }
            if (isStreakMetric(nameCn)) {
                binding.tvValue.setText(buildStreakSpan(nameCn, value));
                binding.tvValue.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                return;
            }
            if (isTradeRatioMetric(nameCn)) {
                binding.tvValue.setText(buildTradeRatioSpan(nameCn, value));
                binding.tvValue.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                return;
            }
            if (isGrossPairMetric(nameCn)) {
                binding.tvValue.setText(buildGrossPairSpan(value));
                binding.tvValue.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                return;
            }
            binding.tvValue.setText(value);
            applyProfitColor(binding.tvValue, nameCn, value);
        }

        private boolean isStreakMetric(String label) {
            if (label == null) {
                return false;
            }
            String normalized = label.replace(" ", "");
            return normalized.contains("最大连续盈利") || normalized.contains("最大连续亏损");
        }

        private boolean isTradeRatioMetric(String label) {
            if (label == null) {
                return false;
            }
            String normalized = label.replace(" ", "");
            return normalized.contains("盈利交易") || normalized.contains("亏损交易");
        }

        private boolean isGrossPairMetric(String label) {
            if (label == null) {
                return false;
            }
            return label.replace(" ", "").contains("毛利/毛损");
        }

        private CharSequence buildStreakSpan(String label, String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return "--";
            }
            SpannableString span = new SpannableString(raw);
            int tintStart = TradeStatsMetricStyleHelper.resolveStreakTintStart(label, raw);
            if (tintStart >= raw.length()) {
                return span;
            }
            int color = resolveMetricColor(label, raw, R.color.text_primary);
            span.setSpan(new ForegroundColorSpan(color),
                    tintStart,
                    raw.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return span;
        }

        private CharSequence buildTradeRatioSpan(String label, String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return "--";
            }
            SpannableString span = new SpannableString(raw);
            int divider = raw.indexOf(") ");
            if (divider < 0 || divider >= raw.length() - 2) {
                return span;
            }
            int color = ContextCompat.getColor(binding.getRoot().getContext(),
                    label != null && label.contains("亏损")
                            ? R.color.accent_red
                            : R.color.accent_green);
            span.setSpan(new ForegroundColorSpan(color),
                    divider + 2,
                    raw.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return span;
        }

        private CharSequence buildGrossPairSpan(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return "--";
            }
            SpannableString span = new SpannableString(raw);
            int divider = raw.indexOf(" / ");
            if (divider <= 0 || divider >= raw.length() - 3) {
                return span;
            }
            int positiveColor = ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_green);
            int negativeColor = ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_red);
            span.setSpan(new ForegroundColorSpan(positiveColor),
                    0,
                    divider,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new ForegroundColorSpan(negativeColor),
                    divider + 3,
                    raw.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return span;
        }

        private void applyProfitColor(TextView textView, String label, String value) {
            int defaultColor = ContextCompat.getColor(textView.getContext(), R.color.text_primary);
            int color = resolveProfitColor(label, value, defaultColor);
            textView.setTextColor(color);
        }

        private int resolveProfitColor(String label, String value, int defaultColor) {
            AccountValueStyleHelper.Direction direction =
                    AccountValueStyleHelper.resolveMetricDirection(label, value);
            if (direction == AccountValueStyleHelper.Direction.POSITIVE) {
                return ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_green);
            }
            if (direction == AccountValueStyleHelper.Direction.NEGATIVE) {
                return ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_red);
            }
            return defaultColor;
        }

        private int resolveMetricColor(String label, String value, int neutralColorRes) {
            AccountValueStyleHelper.Direction direction =
                    AccountValueStyleHelper.resolveMetricDirection(label, value);
            if (direction == AccountValueStyleHelper.Direction.POSITIVE) {
                return ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_green);
            }
            if (direction == AccountValueStyleHelper.Direction.NEGATIVE) {
                return ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_red);
            }
            return ContextCompat.getColor(binding.getRoot().getContext(), neutralColorRes);
        }
    }
}
