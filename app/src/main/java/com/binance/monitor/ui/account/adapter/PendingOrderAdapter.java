package com.binance.monitor.ui.account.adapter;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemPositionBinding;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PendingOrderAdapter extends RecyclerView.Adapter<PendingOrderAdapter.Holder> {
    private static final String PAYLOAD_EXPAND_STATE = "payload_expand_state";
    private static final String PAYLOAD_CONTENT_STATE = "payload_content_state";

    private final List<PositionItem> items = new ArrayList<>();
    private final List<String> rowKeys = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();
    private boolean masked;
    private ActionListener actionListener;

    public interface ActionListener {
        void onActionRequested(PositionItem item);
    }

    // 注册挂单操作回调，供图表页接入撤单入口。
    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submitList(List<PositionItem> data) {
        List<PositionItem> nextItems = data == null ? new ArrayList<>() : new ArrayList<>(data);
        List<String> nextRowKeys = buildRowKeys(nextItems);
        Set<String> nextExpanded = new HashSet<>();
        for (String key : nextRowKeys) {
            if (expandedKeys.contains(key)) {
                nextExpanded.add(key);
            }
        }

        List<PositionItem> previousItems = new ArrayList<>(items);
        List<String> previousRowKeys = new ArrayList<>(rowKeys);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new RowDiffCallback(
                previousItems,
                previousRowKeys,
                nextItems,
                nextRowKeys));

        expandedKeys.clear();
        expandedKeys.addAll(nextExpanded);
        items.clear();
        rowKeys.clear();
        items.addAll(nextItems);
        rowKeys.addAll(nextRowKeys);
        diffResult.dispatchUpdatesTo(this);
    }

    // 行情持仓页关闭隐私显示时，统一把挂单数量和价格打码。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        notifyDataSetChanged();
    }

    private List<String> buildRowKeys(List<PositionItem> data) {
        List<String> keys = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return keys;
        }
        Map<String, Integer> occurrence = new HashMap<>();
        for (PositionItem item : data) {
            String base = identityKeyOf(item);
            int index = occurrence.containsKey(base) ? occurrence.get(base) : 0;
            occurrence.put(base, index + 1);
            keys.add(base + "#" + index);
        }
        return keys;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPositionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PositionItem item = items.get(position);
        String rowKey = rowKeys.get(position);
        boolean expanded = expandedKeys.contains(rowKey);
        holder.bind(item, expanded, false, masked, actionListener != null);
        holder.binding.layoutHeader.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= rowKeys.size()) {
                return;
            }
            String key = rowKeys.get(adapterPosition);
            if (expandedKeys.contains(key)) {
                expandedKeys.remove(key);
            } else {
                expandedKeys.add(key);
            }
            notifyItemChanged(adapterPosition, PAYLOAD_EXPAND_STATE);
        });
        holder.binding.btnPositionAction.setOnClickListener(v -> {
            ActionListener listener = actionListener;
            if (listener == null) {
                return;
            }
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= items.size()) {
                return;
            }
            listener.onActionRequested(items.get(adapterPosition));
        });
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        PositionItem item = items.get(position);
        String rowKey = rowKeys.get(position);
        boolean expanded = expandedKeys.contains(rowKey);
        holder.bind(item, expanded, hasPayload(payloads, PAYLOAD_EXPAND_STATE), masked, actionListener != null);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String identityKeyOf(PositionItem item) {
        if (item == null) {
            return "";
        }
        if (item.getOrderId() > 0L) {
            return "order:" + item.getOrderId();
        }
        if (item.getPositionTicket() > 0L) {
            return "position:" + item.getPositionTicket();
        }
        long price = Math.round(item.getPendingPrice() * 100d);
        long tp = Math.round(item.getTakeProfit() * 100d);
        long sl = Math.round(item.getStopLoss() * 100d);
        return safe(item.getCode()) + "|" + safe(item.getSide()) + "|" + price + "|" + tp + "|" + sl
                + "|" + safe(item.getProductName());
    }

    private String contentKeyOf(PositionItem item) {
        if (item == null) {
            return "";
        }
        long lots = Math.round(Math.abs(item.getPendingLots()) * 10_000d);
        long latest = Math.round(item.getLatestPrice() * 100d);
        return identityKeyOf(item) + "|" + lots + "|" + latest + "|" + item.getPendingCount();
    }

    private boolean hasPayload(List<Object> payloads, String expected) {
        for (Object payload : payloads) {
            if (expected.equals(payload)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private final class RowDiffCallback extends DiffUtil.Callback {
        private final List<PositionItem> oldItems;
        private final List<String> oldKeys;
        private final List<PositionItem> newItems;
        private final List<String> newKeys;

        private RowDiffCallback(List<PositionItem> oldItems,
                                List<String> oldKeys,
                                List<PositionItem> newItems,
                                List<String> newKeys) {
            this.oldItems = oldItems;
            this.oldKeys = oldKeys;
            this.newItems = newItems;
            this.newKeys = newKeys;
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldKeys.get(oldItemPosition).equals(newKeys.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return contentKeyOf(oldItems.get(oldItemPosition))
                    .equals(contentKeyOf(newItems.get(newItemPosition)));
        }

        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            return PAYLOAD_CONTENT_STATE;
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemPositionBinding binding;

        Holder(ItemPositionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PositionItem item,
                  boolean expanded,
                  boolean animateExpand,
                  boolean masked,
                  boolean actionEnabled) {
            binding.btnPositionAction.setVisibility(actionEnabled ? View.VISIBLE : View.GONE);
            binding.btnPositionAction.setText("撤单");
            if (masked) {
                binding.tvSummary.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvSummary.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                updateExpandState(expanded, animateExpand);
                binding.tvProduct.setVisibility(View.GONE);
                binding.tvBase.setVisibility(View.GONE);
                binding.tvMetrics.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvMetrics.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
                binding.tvPnL.setVisibility(View.GONE);
                return;
            }
            String sideText = sideCn(item.getSide());
            int sideColor = resolveSideColor(binding.getRoot(), item.getSide());
            double pendingPrice = item.getPendingPrice() > 0d ? item.getPendingPrice() : item.getLatestPrice();
            double displayLots = Math.abs(item.getPendingLots()) > 1e-9
                    ? Math.abs(item.getPendingLots())
                    : Math.abs(item.getQuantity());
            String qtyText = displayLots > 1e-9
                    ? String.format(Locale.getDefault(), "%.2f 手", displayLots)
                    : (item.getPendingCount() > 0
                    ? (item.getPendingCount() + " 单")
                    : "0.00 手");
            String pendingPriceText = String.format(Locale.getDefault(), "%,.2f", pendingPrice);
            String summaryRaw = String.format(Locale.getDefault(),
                    "%s | %s | %s | %s",
                    item.getProductName(),
                    sideText,
                    qtyText,
                    "$" + pendingPriceText);
            SpannableStringBuilder summarySpan = new SpannableStringBuilder(summaryRaw);
            int sideStart = summaryRaw.indexOf(sideText);
            if (sideStart >= 0) {
                summarySpan.setSpan(new ForegroundColorSpan(sideColor),
                        sideStart,
                        sideStart + sideText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvSummary.setText(summarySpan);

            updateExpandState(expanded, animateExpand);

            binding.tvProduct.setVisibility(View.GONE);
            binding.tvBase.setVisibility(View.GONE);
            binding.tvMetrics.setText(String.format(Locale.getDefault(),
                    "价位 %s | 现价 %s\n止盈 %s | 止损 %s",
                    "$" + pendingPriceText,
                    "$" + FormatUtils.formatPrice(item.getLatestPrice()),
                    optionalPrice(item.getTakeProfit()),
                    optionalPrice(item.getStopLoss())));
            binding.tvPnL.setVisibility(View.GONE);
        }

        private void updateExpandState(boolean expanded, boolean animate) {
            View detail = binding.layoutDetail;
            detail.animate().cancel();
            if (!animate) {
                detail.setVisibility(expanded ? View.VISIBLE : View.GONE);
                detail.setAlpha(1f);
                detail.setTranslationY(0f);
                return;
            }
            if (expanded) {
                if (detail.getVisibility() == View.VISIBLE) {
                    return;
                }
                detail.setVisibility(View.VISIBLE);
                detail.setAlpha(0f);
                detail.setTranslationY(-8f);
                detail.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                if (detail.getVisibility() != View.VISIBLE) {
                    return;
                }
                detail.animate()
                        .alpha(0f)
                        .translationY(-6f)
                        .setDuration(140L)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            detail.setVisibility(View.GONE);
                            detail.setAlpha(1f);
                            detail.setTranslationY(0f);
                        })
                        .start();
            }
        }

        private static String optionalPrice(double value) {
            if (value <= 0d) {
                return "--";
            }
            return "$" + FormatUtils.formatPrice(value);
        }

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }

        private static int resolveSideColor(View root, String side) {
            return ContextCompat.getColor(root.getContext(),
                    "buy".equalsIgnoreCase(side) ? R.color.accent_green : R.color.accent_red);
        }
    }
}
