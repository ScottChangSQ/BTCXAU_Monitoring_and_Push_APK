package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.databinding.ItemStatsMetricBinding;
import com.binance.monitor.ui.account.model.AccountMetric;

import java.util.ArrayList;
import java.util.List;

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
            binding.tvName.setText(item.getName());
            binding.tvValue.setText(item.getValue());
        }
    }
}
