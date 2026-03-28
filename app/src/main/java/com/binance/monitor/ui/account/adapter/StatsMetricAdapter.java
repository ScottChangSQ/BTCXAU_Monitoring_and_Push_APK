package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemStatsMetricBinding;
import com.binance.monitor.ui.account.MetricNameTranslator;
import com.binance.monitor.ui.account.model.AccountMetric;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatsMetricAdapter extends RecyclerView.Adapter<StatsMetricAdapter.Holder> {

    private final List<AccountMetric> items = new ArrayList<>();

    public void submitList(List<AccountMetric> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemStatsMetricBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemStatsMetricBinding binding;

        Holder(ItemStatsMetricBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountMetric item) {
            String nameCn = MetricNameTranslator.toChinese(item.getName());
            String value = item.getValue();
            binding.tvName.setText(nameCn);
            if (isStreakMetric(nameCn)) {
                binding.tvValue.setText(buildStreakSpan(value));
                binding.tvValue.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                return;
            }
            if (isTradeRatioMetric(nameCn)) {
                binding.tvValue.setText(buildTradeRatioSpan(nameCn, value));
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

        private CharSequence buildStreakSpan(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return "--";
            }
            SpannableString span = new SpannableString(raw);
            int signPos = raw.indexOf('+');
            if (signPos < 0) {
                signPos = raw.indexOf('-');
            }
            if (signPos < 0) {
                return span;
            }
            int color = ContextCompat.getColor(binding.getRoot().getContext(),
                    raw.charAt(signPos) == '+' ? R.color.accent_green : R.color.accent_red);
            span.setSpan(new ForegroundColorSpan(color),
                    signPos,
                    raw.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return span;
        }

        private CharSequence buildTradeRatioSpan(String label, String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return "--";
            }
            SpannableString span = new SpannableString(raw);
            int lineBreak = raw.indexOf('\n');
            if (lineBreak < 0 || lineBreak >= raw.length() - 1) {
                return span;
            }
            int color = ContextCompat.getColor(binding.getRoot().getContext(),
                    label != null && label.contains("亏损")
                            ? R.color.accent_red
                            : R.color.accent_green);
            span.setSpan(new ForegroundColorSpan(color),
                    lineBreak + 1,
                    raw.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return span;
        }

        private void applyProfitColor(TextView textView, String label, String value) {
            int defaultColor = ContextCompat.getColor(textView.getContext(), R.color.text_primary);
            int color = resolveProfitColor(label, value, defaultColor,
                    ContextCompat.getColor(textView.getContext(), R.color.accent_green),
                    ContextCompat.getColor(textView.getContext(), R.color.accent_red));
            textView.setTextColor(color);
        }

        private int resolveProfitColor(String label,
                                       String value,
                                       int defaultColor,
                                       int positiveColor,
                                       int negativeColor) {
            if (label == null || value == null) {
                return defaultColor;
            }
            String normalizedLabel = label.replace(" ", "");
            boolean profitField = normalizedLabel.contains("盈亏")
                    || normalizedLabel.contains("收益")
                    || normalizedLabel.contains("利润")
                    || normalizedLabel.contains("回撤")
                    || normalizedLabel.contains("净值")
                    || normalizedLabel.contains("结余")
                    || normalizedLabel.contains("毛利")
                    || normalizedLabel.contains("毛损")
                    || normalizedLabel.contains("最好交易")
                    || normalizedLabel.contains("最差交易");
            if (!profitField) {
                String normalizedLower = normalizedLabel.toLowerCase(Locale.ROOT);
                profitField = normalizedLower.contains("consecutive")
                        || normalizedLower.contains("streak")
                        || normalizedLabel.contains("最大连续盈利")
                        || normalizedLabel.contains("最大连续亏损");
            }
            if (!profitField) {
                return defaultColor;
            }

            if (value.contains("+")) {
                return positiveColor;
            }
            if (value.contains("-")) {
                return negativeColor;
            }
            if (normalizedLabel.contains("亏") || normalizedLabel.contains("损") || normalizedLabel.contains("回撤")) {
                return negativeColor;
            }
            return defaultColor;
        }
    }
}
