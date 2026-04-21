package com.binance.monitor.ui.account.adapter;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ItemPositionBinding;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStore;
import com.binance.monitor.runtime.state.model.ProductRuntimeSnapshot;
import com.binance.monitor.ui.rules.IndicatorFormatterCenter;
import com.binance.monitor.ui.theme.UiPaletteManager;
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

    private final UnifiedRuntimeSnapshotStore runtimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();
    private final List<PositionItem> items = new ArrayList<>();
    private final List<String> rowKeys = new ArrayList<>();
    private final Set<String> expandedKeys = new HashSet<>();
    private final Map<String, Integer> productCounts = new HashMap<>();
    private boolean masked;
    private ActionListener actionListener;

    public interface ActionListener {
        void onModifyRequested(PositionItem item);
        void onDeleteRequested(PositionItem item);
    }

    // 注册挂单操作回调，供图表页接入改单和撤单入口。
    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submitList(List<PositionItem> data) {
        List<PositionItem> nextItems = data == null ? new ArrayList<>() : new ArrayList<>(data);
        List<String> nextRowKeys = buildRowKeys(nextItems);
        Map<String, Integer> previousProductCounts = new HashMap<>(productCounts);
        Map<String, Integer> nextProductCounts = buildProductCounts(nextItems);
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
                previousProductCounts,
                nextItems,
                nextRowKeys,
                nextProductCounts));

        expandedKeys.clear();
        expandedKeys.addAll(nextExpanded);
        items.clear();
        rowKeys.clear();
        productCounts.clear();
        productCounts.putAll(buildProductCounts(nextItems));
        items.addAll(nextItems);
        rowKeys.addAll(nextRowKeys);
        diffResult.dispatchUpdatesTo(this);
    }

    // 账户持仓相关页面关闭隐私显示时，统一把挂单数量和价格打码。
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

    private Map<String, Integer> buildProductCounts(List<PositionItem> data) {
        Map<String, Integer> counts = new HashMap<>();
        if (data == null || data.isEmpty()) {
            return counts;
        }
        for (PositionItem item : data) {
            String productSymbol = buildProductSymbolKey(item);
            if (productSymbol.isEmpty()) {
                continue;
            }
            counts.put(productSymbol, counts.getOrDefault(productSymbol, 0) + 1);
        }
        return counts;
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
        ProductRuntimeSnapshot runtimeSnapshot = runtimeSnapshotStore.selectProduct(buildProductSymbolKey(item));
        int productCount = resolveProductCount(productCounts, item);
        holder.bind(item, expanded, false, masked, actionListener != null, productCount, runtimeSnapshot);
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
        holder.binding.btnPositionModifyAction.setOnClickListener(v -> {
            ActionListener listener = actionListener;
            if (listener == null) {
                return;
            }
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= items.size()) {
                return;
            }
            listener.onModifyRequested(items.get(adapterPosition));
        });
        holder.binding.btnPositionDeleteAction.setOnClickListener(v -> {
            ActionListener listener = actionListener;
            if (listener == null) {
                return;
            }
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= items.size()) {
                return;
            }
            listener.onDeleteRequested(items.get(adapterPosition));
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
        ProductRuntimeSnapshot runtimeSnapshot = runtimeSnapshotStore.selectProduct(buildProductSymbolKey(item));
        int productCount = resolveProductCount(productCounts, item);
        holder.bind(item,
                expanded,
                hasPayload(payloads, PAYLOAD_EXPAND_STATE),
                masked,
                actionListener != null,
                productCount,
                runtimeSnapshot);
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

    private String contentKeyOf(PositionItem item, @NonNull Map<String, Integer> counts) {
        if (item == null) {
            return "";
        }
        String productName = safe(item.getProductName());
        String side = safe(item.getSide());
        double displayLots = Math.abs(item.getPendingLots()) > 1e-9
                ? Math.abs(item.getPendingLots())
                : Math.abs(item.getQuantity());
        long lots = Math.round(displayLots * 10_000d);
        long quantity = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long latest = Math.round(item.getLatestPrice() * 100d);
        long pendingPrice = Math.round(item.getPendingPrice() * 100d);
        long takeProfit = Math.round(item.getTakeProfit() * 100d);
        long stopLoss = Math.round(item.getStopLoss() * 100d);
        ProductRuntimeSnapshot runtimeSnapshot = runtimeSnapshotStore.selectProduct(buildProductSymbolKey(item));
        int productCount = resolveProductCount(counts, item);
        String runtimePendingSignature = productCount == 1 && runtimeSnapshot.getPendingCount() == 1
                ? runtimeSnapshot.getSymbol() + "|" + runtimeSnapshot.getProductRevision()
                + "|" + runtimeSnapshot.getPendingCount()
                : "";
        return identityKeyOf(item) + "|" + lots + "|" + quantity + "|" + latest + "|" + pendingPrice + "|"
                + takeProfit + "|" + stopLoss + "|" + item.getPendingCount()
                + "|" + productName + "|" + side + "|" + runtimePendingSignature;
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

    @NonNull
    private String buildProductSymbolKey(@Nullable PositionItem item) {
        if (item == null) {
            return "";
        }
        String symbol = safe(item.getCode()).trim();
        if (symbol.isEmpty()) {
            symbol = safe(item.getProductName()).trim();
        }
        return symbol.toUpperCase(Locale.US);
    }

    private int resolveProductCount(@NonNull Map<String, Integer> counts, @Nullable PositionItem item) {
        String productSymbol = buildProductSymbolKey(item);
        if (productSymbol.isEmpty()) {
            return 0;
        }
        Integer count = counts.get(productSymbol);
        return count == null ? 0 : count;
    }

    private final class RowDiffCallback extends DiffUtil.Callback {
        private final List<PositionItem> oldItems;
        private final List<String> oldKeys;
        private final Map<String, Integer> oldProductCounts;
        private final List<PositionItem> newItems;
        private final List<String> newKeys;
        private final Map<String, Integer> newProductCounts;

        private RowDiffCallback(List<PositionItem> oldItems,
                                List<String> oldKeys,
                                Map<String, Integer> oldProductCounts,
                                List<PositionItem> newItems,
                                List<String> newKeys,
                                Map<String, Integer> newProductCounts) {
            this.oldItems = oldItems;
            this.oldKeys = oldKeys;
            this.oldProductCounts = oldProductCounts;
            this.newItems = newItems;
            this.newKeys = newKeys;
            this.newProductCounts = newProductCounts;
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
            return contentKeyOf(oldItems.get(oldItemPosition), oldProductCounts)
                    .equals(contentKeyOf(newItems.get(newItemPosition), newProductCounts));
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
                  boolean actionEnabled,
                  int productCount,
                  @NonNull ProductRuntimeSnapshot runtimeSnapshot) {
            UiPaletteManager.Palette palette = UiPaletteManager.resolve(binding.getRoot().getContext());
            enforceSummarySingleLine();
            applyRowPalette(palette, expanded);
            binding.btnPositionCloseAction.setVisibility(View.GONE);
            binding.btnPositionModifyAction.setVisibility(actionEnabled ? View.VISIBLE : View.GONE);
            binding.btnPositionDeleteAction.setVisibility(actionEnabled ? View.VISIBLE : View.GONE);
            if (masked) {
                binding.tvSummary.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvSummary.setTextColor(palette.textPrimary);
                updateExpandState(expanded, animateExpand);
                binding.tvProduct.setVisibility(View.GONE);
                binding.tvBase.setVisibility(View.GONE);
                binding.tvMetrics.setText(SensitiveDisplayMasker.MASK_TEXT);
                binding.tvMetrics.setTextColor(palette.textSecondary);
                binding.tvPnL.setVisibility(View.GONE);
                return;
            }
            String sideText = sideCn(item.getSide());
            int sideColor = resolveSideColor(palette, item.getSide());
            boolean useRuntimePendingSummary = shouldUseRuntimePendingSummary(productCount, runtimeSnapshot);
            double pendingPrice = item.getPendingPrice() > 0d ? item.getPendingPrice() : item.getLatestPrice();
            double displayLots = Math.abs(item.getPendingLots()) > 1e-9
                    ? Math.abs(item.getPendingLots())
                    : Math.abs(item.getQuantity());
            int pendingCount = item.getPendingCount() > 0
                    ? item.getPendingCount()
                    : (useRuntimePendingSummary ? runtimeSnapshot.getPendingCount() : 0);
            String qtyText = displayLots > 1e-9
                    ? IndicatorFormatterCenter.formatQuantity(displayLots, 2, " 手")
                    : (pendingCount > 0
                    ? (pendingCount + " 单")
                    : "0.00 手");
            String pendingPriceText = formatCollapsedPendingPrice(pendingPrice);
            String summaryRaw = String.format(Locale.getDefault(),
                    "%s | %s | %s | %s",
                    resolveDisplayProductName(item, runtimeSnapshot),
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
            binding.tvSummary.setTextColor(palette.textPrimary);
            binding.tvSummary.setText(summarySpan);

            updateExpandState(expanded, animateExpand);

            binding.tvProduct.setVisibility(View.GONE);
            binding.tvBase.setVisibility(View.GONE);
            binding.tvMetrics.setTextColor(palette.textSecondary);
            binding.tvMetrics.setText(String.format(Locale.getDefault(),
                    "价位 %s | 现价 %s\n止盈 %s | 止损 %s",
                    "$" + pendingPriceText,
                    IndicatorFormatterCenter.formatPrice(item.getLatestPrice(), 2, false),
                    optionalPrice(item.getTakeProfit()),
                    optionalPrice(item.getStopLoss())));
            binding.tvPnL.setVisibility(View.GONE);
        }

        // 挂单行统一跟随主题面板和动作按钮样式。
        private void applyRowPalette(@NonNull UiPaletteManager.Palette palette, boolean expanded) {
            android.content.Context context = binding.getRoot().getContext();
            int rowFill = expanded ? palette.surfaceEnd : palette.card;
            binding.layoutHeader.setBackground(UiPaletteManager.createListRowBackground(
                    context,
                    rowFill,
                    palette.stroke
            ));
            styleActionButton(binding.btnPositionModifyAction, palette, false);
            styleActionButton(binding.btnPositionDeleteAction, palette, true);
        }

        // 挂单行操作按钮统一走 ActionButton 主体，避免账户页继续保留旧方角动作入口。
        private void styleActionButton(@NonNull android.widget.TextView button,
                                       @NonNull UiPaletteManager.Palette palette,
                                       boolean danger) {
            int fillColor = danger
                    ? ColorUtils.setAlphaComponent(palette.fall, 24)
                    : palette.primarySoft;
            int textColor = danger ? palette.fall : palette.textPrimary;
            UiPaletteManager.styleActionButton(
                    button,
                    palette,
                    fillColor,
                    textColor,
                    R.style.TextAppearance_BinanceMonitor_Control,
                    8,
                    R.dimen.position_row_action_height
            );
        }

        private static boolean shouldUseRuntimePendingSummary(int productCount,
                                                              @NonNull ProductRuntimeSnapshot runtimeSnapshot) {
            return productCount == 1 && runtimeSnapshot.getPendingCount() == 1;
        }

        @NonNull
        private static String resolveDisplayProductName(@NonNull PositionItem item,
                                                        @NonNull ProductRuntimeSnapshot runtimeSnapshot) {
            String productName = safe(item.getProductName()).trim();
            if (!productName.isEmpty()) {
                return productName;
            }
            if (!runtimeSnapshot.getDisplayLabel().isEmpty()) {
                return runtimeSnapshot.getDisplayLabel();
            }
            if (!runtimeSnapshot.getSymbol().isEmpty()) {
                return runtimeSnapshot.getSymbol();
            }
            return safe(item.getCode()).trim();
        }

        // 每次绑定时重新锁定摘要文本单行约束，避免 RecyclerView 复用后出现错误换行或多行高度。
        private void enforceSummarySingleLine() {
            binding.tvSummary.setIncludeFontPadding(false);
            binding.tvSummary.setSingleLine(true);
            binding.tvSummary.setLines(1);
            binding.tvSummary.setMinLines(1);
            binding.tvSummary.setMaxLines(1);
            binding.tvSummary.setHorizontallyScrolling(true);
            binding.tvSummary.setEllipsize(TextUtils.TruncateAt.END);
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
            return IndicatorFormatterCenter.formatPrice(value, 2, false);
        }

        private static String formatCollapsedPendingPrice(double price) {
            double roundedPrice = Math.rint(price);
            return String.format(Locale.getDefault(), "%,.0f", roundedPrice);
        }

        private static String sideCn(String side) {
            return "buy".equalsIgnoreCase(side) ? "买入" : ("sell".equalsIgnoreCase(side) ? "卖出" : side);
        }

        private static int resolveSideColor(@NonNull UiPaletteManager.Palette palette, String side) {
            return "buy".equalsIgnoreCase(side) ? palette.rise : palette.fall;
        }
    }
}
