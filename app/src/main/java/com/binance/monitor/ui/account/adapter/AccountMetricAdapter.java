package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemAccountKvBinding;
import com.binance.monitor.ui.account.AccountValueStyleHelper;
import com.binance.monitor.runtime.account.MetricNameTranslator;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.ArrayList;
import java.util.List;

public class AccountMetricAdapter extends RecyclerView.Adapter<AccountMetricAdapter.Holder> {

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
        return new Holder(ItemAccountKvBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemAccountKvBinding binding;

        Holder(ItemAccountKvBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountMetric item, boolean masked) {
            String nameCn = MetricNameTranslator.toChinese(item.getName());
            String value = masked
                    ? SensitiveDisplayMasker.maskValue(item.getValue(), true)
                    : item.getValue();
            binding.tvLabel.setText(nameCn);
            binding.tvValue.setText(value);
            if (masked) {
                binding.tvValue.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                return;
            }
            applyProfitColor(binding.tvValue, nameCn, value);
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
    }
}
