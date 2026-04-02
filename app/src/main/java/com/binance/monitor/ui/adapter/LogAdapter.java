/*
 * 日志列表适配器，负责把日志记录渲染到列表并同步当前选中状态。
 * 关联 LogActivity 和日志条目布局 item_log。
 */
package com.binance.monitor.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.data.model.AppLogEntry;
import com.binance.monitor.databinding.ItemLogBinding;
import com.binance.monitor.ui.theme.UiPaletteManager;
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
    private UiPaletteManager.Palette palette;

    // 创建日志适配器，并保留复制回调供长按日志时复用。
    public LogAdapter(Callback callback) {
        this.callback = callback;
    }

    // 提交最新日志列表，同时清理已经失效的选中项。
    public void submitList(List<AppLogEntry> logs) {
        items.clear();
        if (logs != null) {
            items.addAll(logs);
        }
        selectedIds.retainAll(extractIds());
        notifyDataSetChanged();
    }

    // 同步当前主题色，保证切换主题后已显示条目也会刷新。
    public void setPalette(UiPaletteManager.Palette palette) {
        this.palette = palette;
        notifyDataSetChanged();
    }

    // 返回当前被选中的日志 ID 集合。
    public Set<String> getSelectedIds() {
        return new LinkedHashSet<>(selectedIds);
    }

    // 清空列表选中状态。
    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
    }

    // 一次性选中当前全部日志条目。
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

    // 提取当前列表中全部日志 ID，供全选和删选逻辑复用。
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

        // 绑定单条日志并同步选中和主题状态。
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
            applyPalette();
        }

        // 根据日志级别绘制不同提示色，方便快速区分严重程度。
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
            if (badge instanceof TextView) {
                ((TextView) badge).setTextColor(ContextCompat.getColor(badge.getContext(), R.color.white));
            }
        }

        // 把当前主题同步到日志条目，避免列表内容残留默认配色。
        private void applyPalette() {
            UiPaletteManager.Palette activePalette = palette;
            if (activePalette == null) {
                return;
            }
            Context context = binding.getRoot().getContext();
            binding.layoutItemRow.setBackground(UiPaletteManager.createListRowBackground(
                    context,
                    activePalette.surfaceEnd,
                    activePalette.stroke
            ));
            binding.tvTime.setTextColor(activePalette.textSecondary);
            binding.tvMessage.setTextColor(activePalette.textPrimary);
            binding.viewDivider.setBackgroundColor(activePalette.stroke);
        }
    }
}
