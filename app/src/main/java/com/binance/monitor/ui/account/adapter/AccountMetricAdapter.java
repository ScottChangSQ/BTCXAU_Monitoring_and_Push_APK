package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemAccountKvBinding;
import com.binance.monitor.ui.account.MetricNameTranslator;
import com.binance.monitor.ui.account.model.AccountMetric;

import java.util.ArrayList;
import java.util.List;

public class AccountMetricAdapter extends RecyclerView.Adapter<AccountMetricAdapter.Holder> {

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
        return new Holder(ItemAccountKvBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemAccountKvBinding binding;

        Holder(ItemAccountKvBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountMetric item) {
            String nameCn = MetricNameTranslator.toChinese(item.getName());
            String value = item.getValue();
            binding.tvLabel.setText(nameCn);
            binding.tvValue.setText(value);
            applyProfitColor(binding.tvValue, nameCn, value);
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
                    || normalizedLabel.contains("结余");
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
