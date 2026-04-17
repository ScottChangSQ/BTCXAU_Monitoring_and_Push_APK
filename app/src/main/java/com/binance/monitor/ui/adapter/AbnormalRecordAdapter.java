package com.binance.monitor.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.databinding.ItemAbnormalRecordBinding;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;

public class AbnormalRecordAdapter extends RecyclerView.Adapter<AbnormalRecordAdapter.RecordViewHolder> {

    private final List<AbnormalRecord> items = new ArrayList<>();
    private final boolean showDetails;

    public AbnormalRecordAdapter() {
        this(false);
    }

    public AbnormalRecordAdapter(boolean showDetails) {
        this.showDetails = showDetails;
    }

    public void submitList(List<AbnormalRecord> records) {
        items.clear();
        if (records != null) {
            items.addAll(records);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RecordViewHolder(ItemAbnormalRecordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        holder.bind(items.get(position), showDetails);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {

        private final ItemAbnormalRecordBinding binding;

        RecordViewHolder(ItemAbnormalRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AbnormalRecord record, boolean showDetails) {
            UiPaletteManager.Palette palette = UiPaletteManager.resolve(binding.getRoot().getContext());
            binding.getRoot().setCardBackgroundColor(palette.card);
            binding.getRoot().setStrokeColor(palette.stroke);
            binding.getRoot().setStrokeWidth(UiPaletteManager.strokeWidthPx(binding.getRoot().getContext()));
            binding.getRoot().setRadius(UiPaletteManager.radiusMdPx(binding.getRoot().getContext(), palette));
            binding.tvTime.setTextColor(palette.textSecondary);
            binding.tvTrigger.setTextColor(palette.textPrimary);
            binding.tvDetails.setTextColor(palette.textSecondary);
            String symbol = record.getSymbol();
            if ("BOTH".equals(symbol)) {
                binding.tvSymbol.setText("BTC+XAU");
                binding.tvSymbol.setTextColor(palette.fall);
            } else if ("XAU".equals(symbol) || AppConstants.SYMBOL_XAU.equals(symbol)) {
                binding.tvSymbol.setText("XAU");
                binding.tvSymbol.setTextColor(palette.xau);
            } else {
                binding.tvSymbol.setText("BTC");
                binding.tvSymbol.setTextColor(palette.primary);
            }
            binding.tvTime.setText(FormatUtils.formatDateTime(record.getTimestamp()));
            binding.tvTrigger.setText(record.getTriggerSummary());
            if (showDetails) {
                String details = "开:" + FormatUtils.formatPrice(record.getOpenPrice())
                        + "  收:" + FormatUtils.formatPrice(record.getClosePrice())
                        + "  量:" + FormatUtils.formatVolume(record.getVolume())
                        + "  额:" + FormatUtils.formatAmount(record.getAmount());
                binding.tvDetails.setText(details);
                binding.tvDetails.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.tvDetails.setVisibility(android.view.View.GONE);
            }
        }
    }
}
