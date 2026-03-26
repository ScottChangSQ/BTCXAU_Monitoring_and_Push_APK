package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
            binding.tvLabel.setText(MetricNameTranslator.toChinese(item.getName()));
            binding.tvValue.setText(item.getValue());
        }
    }
}
