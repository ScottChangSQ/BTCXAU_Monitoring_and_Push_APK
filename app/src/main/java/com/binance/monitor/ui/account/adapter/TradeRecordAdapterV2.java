package com.binance.monitor.ui.account.adapter;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemTradeRecordBinding;
import com.binance.monitor.ui.account.AccountValueStyleHelper;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TradeRecordAdapterV2 extends RecyclerView.Adapter<TradeRecordAdapterV2.Holder> {
    private static final String PAYLOAD_EXPAND_STATE = "payload_expand_state";
    private static final String PAYLOAD_CONTENT_STATE = "payload_content_state";

    private final List<TradeRecordItem> items = new ArrayList<>();
    private final List<String> rowKeys = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();
    private boolean masked;

    public void submitList(List<TradeRecordItem> data) {
        List<TradeRecordItem> nextItems = data == null ? new ArrayList<>() : new ArrayList<>(data);
        List<String> nextRowKeys = buildRowKeys(nextItems);
        Set<String> nextExpanded = new HashSet<>();
        for (String key : nextRowKeys) {
            if (expandedKeys.contains(key)) {
                nextExpanded.add(key);
            }
        }

        List<TradeRecordItem> previousItems = new ArrayList<>(items);
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

    // 在排序切换时清空全部展开状态，避免旧明细跟着新顺序保留下来。
    public void collapseAllExpandedRows() {
        expandedKeys.clear();
    }

    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        notifyDataSetChanged();
    }

    private List<String> buildRowKeys(List<TradeRecordItem> data) {
        List<String> keys = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return keys;
        }
        Map<String, Integer> occurrence = new HashMap<>();
        for (TradeRecordItem item : data) {
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
        return new Holder(ItemTradeRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TradeRecordItem item = items.get(position);
        String rowKey = rowKeys.get(position);
        boolean expanded = expandedKeys.contains(rowKey);
        holder.bind(item, expanded, false, masked);
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
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        TradeRecordItem item = items.get(position);
        String rowKey = rowKeys.get(position);
        boolean expanded = expandedKeys.contains(rowKey);
        holder.bind(item, expanded, hasPayload(payloads, PAYLOAD_EXPAND_STATE), masked);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String identityKeyOf(TradeRecordItem item) {
        if (item == null) {
            return "";
        }
        if (item.getDealTicket() > 0L) {
            return "deal:" + item.getDealTicket();
        }
        if (item.getOrderId() > 0L || item.getPositionId() > 0L) {
            return "trade:" + item.getOrderId() + "|" + item.getPositionId() + "|" + item.getEntryType()
                    + "|" + item.getOpenTime()
                    + "|" + item.getCloseTime();
        }
        long openTime = item.getOpenTime();
        long closeTime = item.getCloseTime();
        long qtyKey = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        return safe(item.getCode()) + "|" + safe(item.getSide()) + "|" + openTime + "|" + closeTime + "|" + qtyKey;
    }

    private String contentKeyOf(TradeRecordItem item) {
        if (item == null) {
            return "";
        }
        long priceKey = Math.round(item.getPrice() * 100d);
        long amountKey = Math.round(item.getAmount() * 100d);
        long profitKey = Math.round(item.getProfit() * 100d);
        long feeKey = Math.round(item.getFee() * 100d);
        long storageKey = Math.round(item.getStorageFee() * 100d);
        long openPriceKey = Math.round(item.getOpenPrice() * 100d);
        long closePriceKey = Math.round(item.getClosePrice() * 100d);
        return identityKeyOf(item) + "|" + safe(item.getProductName()) + "|" + priceKey + "|" + amountKey
                + "|" + profitKey + "|" + feeKey + "|" + storageKey + "|" + openPriceKey
                + "|" + closePriceKey + "|" + safe(item.getRemark());
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
        private final List<TradeRecordItem> oldItems;
        private final List<String> oldKeys;
        private final List<TradeRecordItem> newItems;
        private final List<String> newKeys;

        private RowDiffCallback(List<TradeRecordItem> oldItems,
                                List<String> oldKeys,
                                List<TradeRecordItem> newItems,
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
        private final ItemTradeRecordBinding binding;

        Holder(ItemTradeRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TradeRecordItem item, boolean expanded, boolean animateExpand, boolean masked) {
            if (masked) {
                binding.tvSummary.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvSummary.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_primary));
                binding.tvTime.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvProduct.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvSide.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvDetail.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvTime.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
                binding.tvProduct.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
                binding.tvSide.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
                binding.tvDetail.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
                binding.tvRemark.setVisibility(View.GONE);
                updateExpandState(expanded, animateExpand);
                return;
            }
            String sideText = sideCn(item.getSide());
            int sideColor = resolveSideColor(binding.getRoot(), item.getSide());
            double summaryProfit = item.getProfit() + item.getStorageFee();
            int pnlColor = resolveAmountColor(binding.getRoot(), summaryProfit, R.color.text_primary);
            String amount = signedMoney(summaryProfit);
            String raw = String.format(Locale.getDefault(), "%s | %s | %.2f 手 | %s",
                    item.getProductName(), sideText, item.getQuantity(), amount);
            SpannableStringBuilder span = new SpannableStringBuilder(raw);
            int sideStart = raw.indexOf(sideText);
            if (sideStart >= 0) {
                span.setSpan(new ForegroundColorSpan(sideColor),
                        sideStart,
                        sideStart + sideText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int start = raw.lastIndexOf(amount);
            if (start >= 0) {
                span.setSpan(new ForegroundColorSpan(pnlColor), start, raw.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvSummary.setText(span);
            updateExpandState(expanded, animateExpand);

            long openTime = item.getOpenTime();
            long closeTime = item.getCloseTime();
            binding.tvTime.setText("开仓时间: " + FormatUtils.formatDateTime(openTime));
            binding.tvProduct.setText("平仓时间: " + FormatUtils.formatDateTime(closeTime));
            binding.tvSide.setText(String.format(Locale.getDefault(),
                    "开仓价格 $%s | 平仓价格 $%s",
                    FormatUtils.formatPrice(item.getOpenPrice()),
                    FormatUtils.formatPrice(item.getClosePrice())));
            binding.tvSide.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
            String profitText = signedMoney(item.getProfit());
            String storageFeeText = "$" + FormatUtils.formatPrice(item.getStorageFee());
            String detailRaw = String.format(Locale.getDefault(),
                    "盈亏 %s | 库存费 %s",
                    profitText,
                    storageFeeText);
            SpannableStringBuilder detailSpan = new SpannableStringBuilder(detailRaw);
            int profitStart = detailRaw.indexOf(profitText);
            if (profitStart >= 0) {
                detailSpan.setSpan(new ForegroundColorSpan(resolveAmountColor(binding.getRoot(), item.getProfit(), R.color.text_secondary)),
                        profitStart,
                        profitStart + profitText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int storageStart = detailRaw.lastIndexOf(storageFeeText);
            if (storageStart >= 0) {
                detailSpan.setSpan(new ForegroundColorSpan(resolveAmountColor(binding.getRoot(), item.getStorageFee(), R.color.text_secondary)),
                        storageStart,
                        storageStart + storageFeeText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvDetail.setText(detailSpan);
            binding.tvRemark.setVisibility(View.GONE);
        }

        private void updateExpandState(boolean expanded, boolean animate) {
            binding.tvExpandHint.setText(expanded ? "收起" : "展开");
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

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }

        private static String signedMoney(double value) {
            return (value >= 0d ? "+" : "-") + "$" + FormatUtils.formatPrice(Math.abs(value));
        }

        private static int resolveSideColor(View root, String side) {
            return ContextCompat.getColor(root.getContext(),
                    "buy".equalsIgnoreCase(side) ? R.color.accent_green : R.color.accent_red);
        }

        private static int resolveAmountColor(View root, double value, int neutralColorRes) {
            AccountValueStyleHelper.Direction direction = AccountValueStyleHelper.resolveNumericDirection(value);
            if (direction == AccountValueStyleHelper.Direction.POSITIVE) {
                return ContextCompat.getColor(root.getContext(), R.color.accent_green);
            }
            if (direction == AccountValueStyleHelper.Direction.NEGATIVE) {
                return ContextCompat.getColor(root.getContext(), R.color.accent_red);
            }
            return ContextCompat.getColor(root.getContext(), neutralColorRes);
        }
    }
}
