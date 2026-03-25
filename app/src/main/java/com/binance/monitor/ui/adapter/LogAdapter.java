package com.binance.monitor.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.data.model.AppLogEntry;
import com.binance.monitor.databinding.ItemLogBinding;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    public interface Callback {
        void onCopy(AppLogEntry entry);
    }

    private final List<AppLogEntry> items = new ArrayList<>();
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final Callback callback;

    public LogAdapter(Callback callback) {
        this.callback = callback;
    }

    public void submitList(List<AppLogEntry> logs) {
        items.clear();
        if (logs != null) {
            items.addAll(logs);
        }
        selectedIds.retainAll(extractIds());
        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        return new LinkedHashSet<>(selectedIds);
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public void selectAll() {
        selectedIds.clear();
        selectedIds.addAll(extractIds());
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new LogViewHolder(ItemLogBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private Set<String> extractIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (AppLogEntry item : items) {
            ids.add(item.getId());
        }
        return ids;
    }

    class LogViewHolder extends RecyclerView.ViewHolder {

        private final ItemLogBinding binding;

        LogViewHolder(ItemLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AppLogEntry entry) {
            binding.tvLevel.setText(entry.getLevel());
            binding.tvTime.setText(FormatUtils.formatDateTime(entry.getTimestamp()));
            binding.tvMessage.setText(entry.getMessage());
            binding.checkSelect.setOnCheckedChangeListener(null);
            binding.checkSelect.setChecked(selectedIds.contains(entry.getId()));
            binding.checkSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedIds.add(entry.getId());
                } else {
                    selectedIds.remove(entry.getId());
                }
            });
            binding.getRoot().setOnLongClickListener(v -> {
                callback.onCopy(entry);
                return true;
            });
            styleLevel(entry.getLevel(), binding.tvLevel);
        }

        private void styleLevel(String level, View badge) {
            int background;
            switch (level) {
                case "WARN":
                    background = R.drawable.bg_level_warn;
                    break;
                case "ERROR":
                    background = R.drawable.bg_level_error;
                    break;
                case "INFO":
                default:
                    background = R.drawable.bg_level_info;
                    break;
            }
            badge.setBackgroundResource(background);
        }
    }
}
