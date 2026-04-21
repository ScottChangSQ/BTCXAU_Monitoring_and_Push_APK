/*
 * 核心统计展开区双列适配器，把详细统计按两项一排渲染成与摘要区一致的块状样式。
 * 只服务核心统计展开区，不影响其他单列统计列表。
 */
package com.binance.monitor.ui.account.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.databinding.ItemStatsSummaryDetailRowBinding;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.runtime.account.MetricNameTranslator;
import com.binance.monitor.ui.rules.IndicatorPresentation;
import com.binance.monitor.ui.rules.IndicatorPresentationPolicy;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.ArrayList;
import java.util.List;

public class StatsSummaryDetailAdapter extends RecyclerView.Adapter<StatsSummaryDetailAdapter.Holder> {

    private final List<DetailRow> rows = new ArrayList<>();
    private boolean masked;

    public void submitList(List<AccountMetric> data) {
        rows.clear();
        if (data != null) {
            for (int i = 0; i < data.size(); i += 2) {
                rows.add(new DetailRow(
                        data.get(i),
                        i + 1 < data.size() ? data.get(i + 1) : null
                ));
            }
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
        return new Holder(ItemStatsSummaryDetailRowBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(rows.get(position), masked, position == 0);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemStatsSummaryDetailRowBinding binding;

        Holder(ItemStatsSummaryDetailRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DetailRow row, boolean masked, boolean firstRow) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.getRoot().getLayoutParams();
            if (params != null) {
                params.topMargin = firstRow
                        ? 0
                        : binding.getRoot().getResources().getDimensionPixelSize(com.binance.monitor.R.dimen.space_12);
                binding.getRoot().setLayoutParams(params);
            }

            bindMetric(binding.tvMetricLeftLabel, binding.tvMetricLeftValue, row.left, masked);
            if (row.right == null) {
                binding.layoutMetricRight.setVisibility(View.INVISIBLE);
                binding.tvMetricRightLabel.setText("");
                binding.tvMetricRightValue.setText("");
            } else {
                binding.layoutMetricRight.setVisibility(View.VISIBLE);
                bindMetric(binding.tvMetricRightLabel, binding.tvMetricRightValue, row.right, masked);
            }
        }

        // 展开区保留双列块状布局，但盈亏/收益类数字仍复用统一红绿规则。
        private void bindMetric(@NonNull android.widget.TextView labelView,
                                @NonNull android.widget.TextView valueView,
                                @NonNull AccountMetric metric,
                                boolean masked) {
            String labelText = MetricNameTranslator.toChinese(metric.getName());
            IndicatorPresentation presentation = IndicatorPresentationPolicy.presentText(labelText, metric.getValue(), masked);
            labelView.setText(presentation.getLabel());
            String value = metric.getValue();
            if (value == null || value.trim().isEmpty()) {
                value = "--";
            }
            if (masked && !"--".equals(value)) {
                valueView.setText(presentation.getFormattedValue());
                return;
            }
            valueView.setText(IndicatorPresentationPolicy.buildValueSpan(
                    valueView.getContext(),
                    presentation.getLabel(),
                    value,
                    com.binance.monitor.R.color.text_primary
            ));
        }
    }

    private static class DetailRow {
        private final AccountMetric left;
        private final AccountMetric right;

        private DetailRow(AccountMetric left, AccountMetric right) {
            this.left = left;
            this.right = right;
        }
    }
}
