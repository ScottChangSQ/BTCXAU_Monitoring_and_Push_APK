/*
 * 账户历史成交底部抽屉控制器，负责展示完整历史列表以及产品、方向、时间顺序筛选。
 * 供账户页历史成交入口直接复用，不再绕到分析页。
 */
package com.binance.monitor.ui.account;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.databinding.DialogAccountTradeHistorySheetBinding;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.ui.account.adapter.TradeRecordAdapterV2;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public final class AccountTradeHistoryBottomSheetController {
    private static final String FILTER_PRODUCT = "全部产品";
    private static final String FILTER_SIDE = "全部方向";
    private static final String FILTER_SIDE_BUY = "买入";
    private static final String FILTER_SIDE_SELL = "卖出";
    private static final String SORT_TIME_DESC = "时间倒序";
    private static final String SORT_TIME_ASC = "时间正序";

    private final AppCompatActivity activity;
    private String selectedProductFilter = FILTER_PRODUCT;
    private String selectedSideFilter = FILTER_SIDE;
    private String selectedSortFilter = SORT_TIME_DESC;

    // 创建账户历史成交底部抽屉控制器。
    public AccountTradeHistoryBottomSheetController(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    // 展示完整历史成交底部抽屉，并按当前筛选条件直接绑定列表。
    public void show(@Nullable List<TradeRecordItem> sourceTrades) {
        List<TradeRecordItem> baseTrades = copyTrades(sourceTrades);
        DialogAccountTradeHistorySheetBinding binding = DialogAccountTradeHistorySheetBinding.inflate(activity.getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(binding.getRoot());

        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        binding.tvTradeHistorySheetTitle.setTextColor(palette.textPrimary);
        binding.tvTradeHistorySheetSummary.setTextColor(palette.textSecondary);
        binding.tvTradeHistoryEmpty.setTextColor(palette.textSecondary);
        binding.tvTradeHistoryProductLabel.setTextColor(palette.textPrimary);
        binding.tvTradeHistorySideLabel.setTextColor(palette.textPrimary);
        binding.tvTradeHistorySortLabel.setTextColor(palette.textPrimary);
        binding.recyclerTradeHistory.setBackground(
                UiPaletteManager.createSectionBackground(activity, palette.surfaceEnd, palette.stroke)
        );
        styleFilterField(binding.spinnerTradeHistoryProduct, binding.tvTradeHistoryProductLabel, palette);
        styleFilterField(binding.spinnerTradeHistorySide, binding.tvTradeHistorySideLabel, palette);
        styleFilterField(binding.spinnerTradeHistorySort, binding.tvTradeHistorySortLabel, palette);

        TradeRecordAdapterV2 tradeAdapter = new TradeRecordAdapterV2();
        binding.recyclerTradeHistory.setLayoutManager(new LinearLayoutManager(activity));
        binding.recyclerTradeHistory.setItemAnimator(null);
        binding.recyclerTradeHistory.setAdapter(tradeAdapter);

        setupFilters(binding, tradeAdapter, baseTrades);
        dialog.show();
        UiPaletteManager.applyBottomSheetSurface(dialog, palette);
    }

    // 抽屉筛选条统一走主题浅色控件面板，避免 Notion 主题下仍混入旧深色输入框。
    private void styleFilterField(@NonNull Spinner spinner,
                                  @NonNull TextView labelView,
                                  @NonNull UiPaletteManager.Palette palette) {
        spinner.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.card, palette.stroke));
        labelView.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.card, palette.stroke));
        labelView.setTextColor(palette.textPrimary);
    }

    // 初始化筛选控件，并把筛选变化统一收口到同一套列表刷新逻辑。
    private void setupFilters(@NonNull DialogAccountTradeHistorySheetBinding binding,
                              @NonNull TradeRecordAdapterV2 tradeAdapter,
                              @NonNull List<TradeRecordItem> baseTrades) {
        List<String> productOptions = new ArrayList<>();
        productOptions.add(FILTER_PRODUCT);
        productOptions.addAll(AccountDeferredSnapshotRenderHelper.buildTradeProducts(baseTrades));
        if (!productOptions.contains(selectedProductFilter)) {
            selectedProductFilter = FILTER_PRODUCT;
        }
        if (!SORT_TIME_ASC.equals(selectedSortFilter) && !SORT_TIME_DESC.equals(selectedSortFilter)) {
            selectedSortFilter = SORT_TIME_DESC;
        }

        binding.spinnerTradeHistoryProduct.setAdapter(createTradeFilterAdapter(productOptions));
        binding.spinnerTradeHistorySide.setAdapter(createTradeFilterAdapter(buildSideOptions()));
        binding.spinnerTradeHistorySort.setAdapter(createTradeFilterAdapter(buildSortOptions()));

        binding.spinnerTradeHistoryProduct.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            selectedProductFilter = safeSpinnerValue(
                    binding.spinnerTradeHistoryProduct,
                    selectedProductFilter,
                    FILTER_PRODUCT
            );
            updateDisplayedTrades(binding, tradeAdapter, baseTrades);
        }));
        binding.spinnerTradeHistorySide.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            selectedSideFilter = safeSpinnerValue(
                    binding.spinnerTradeHistorySide,
                    selectedSideFilter,
                    FILTER_SIDE
            );
            updateDisplayedTrades(binding, tradeAdapter, baseTrades);
        }));
        binding.spinnerTradeHistorySort.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            selectedSortFilter = safeSpinnerValue(
                    binding.spinnerTradeHistorySort,
                    selectedSortFilter,
                    SORT_TIME_DESC
            );
            updateDisplayedTrades(binding, tradeAdapter, baseTrades);
        }));

        setSpinnerSelectionByValue(binding.spinnerTradeHistoryProduct, selectedProductFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeHistorySide, selectedSideFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeHistorySort, selectedSortFilter);

        binding.tvTradeHistoryProductLabel.setOnClickListener(v -> binding.spinnerTradeHistoryProduct.performClick());
        binding.tvTradeHistorySideLabel.setOnClickListener(v -> binding.spinnerTradeHistorySide.performClick());
        binding.tvTradeHistorySortLabel.setOnClickListener(v -> binding.spinnerTradeHistorySort.performClick());

        updateDisplayedTrades(binding, tradeAdapter, baseTrades);
    }

    // 应用当前筛选条件并刷新底部抽屉里的完整历史成交列表。
    private void updateDisplayedTrades(@NonNull DialogAccountTradeHistorySheetBinding binding,
                                       @NonNull TradeRecordAdapterV2 tradeAdapter,
                                       @NonNull List<TradeRecordItem> baseTrades) {
        selectedProductFilter = safeSpinnerValue(
                binding.spinnerTradeHistoryProduct,
                selectedProductFilter,
                FILTER_PRODUCT
        );
        selectedSideFilter = safeSpinnerValue(
                binding.spinnerTradeHistorySide,
                selectedSideFilter,
                FILTER_SIDE
        );
        selectedSortFilter = safeSpinnerValue(
                binding.spinnerTradeHistorySort,
                selectedSortFilter,
                SORT_TIME_DESC
        );

        updateFilterLabel(binding.tvTradeHistoryProductLabel, selectedProductFilter, FILTER_PRODUCT);
        updateFilterLabel(binding.tvTradeHistorySideLabel, selectedSideFilter, FILTER_SIDE);
        updateFilterLabel(binding.tvTradeHistorySortLabel, selectedSortFilter, SORT_TIME_DESC);

        AccountDeferredSnapshotRenderHelper.TradeFilterRequest request =
                new AccountDeferredSnapshotRenderHelper.TradeFilterRequest(
                        selectedProductFilter,
                        FILTER_PRODUCT.equals(selectedProductFilter),
                        selectedSideFilter,
                        FILTER_SIDE.equals(selectedSideFilter),
                        AccountDeferredSnapshotRenderHelper.SortMode.CLOSE_TIME,
                        SORT_TIME_DESC.equals(selectedSortFilter)
                );
        List<TradeRecordItem> filteredTrades =
                AccountDeferredSnapshotRenderHelper.buildFilteredTrades(baseTrades, request);
        tradeAdapter.collapseAllExpandedRows();
        tradeAdapter.submitList(filteredTrades);

        boolean empty = filteredTrades.isEmpty();
        binding.recyclerTradeHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.tvTradeHistoryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.tvTradeHistorySheetSummary.setText(empty
                ? activity.getString(R.string.account_trade_history_sheet_empty)
                : activity.getString(
                        R.string.account_trade_history_sheet_summary,
                        filteredTrades.size(),
                        baseTrades.size()
                ));
    }

    // 统一构造筛选下拉选项适配器，保持底部抽屉与分析页筛选样式一致。
    @NonNull
    private ArrayAdapter<String> createTradeFilterAdapter(@NonNull List<String> options) {
        List<String> items = new ArrayList<>();
        for (String option : options) {
            String safeValue = option == null ? "" : option.trim();
            if (!safeValue.isEmpty()) {
                items.add(safeValue);
            }
        }
        if (items.isEmpty()) {
            items.add("--");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity,
                R.layout.item_spinner_filter_anchor,
                android.R.id.text1,
                items
        );
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    // 生成方向筛选选项。
    @NonNull
    private List<String> buildSideOptions() {
        List<String> options = new ArrayList<>();
        options.add(FILTER_SIDE);
        options.add(FILTER_SIDE_BUY);
        options.add(FILTER_SIDE_SELL);
        return options;
    }

    // 生成时间顺序筛选选项。
    @NonNull
    private List<String> buildSortOptions() {
        List<String> options = new ArrayList<>();
        options.add(SORT_TIME_DESC);
        options.add(SORT_TIME_ASC);
        return options;
    }

    // 让覆盖在 Spinner 上层的文案与当前选择保持一致。
    private void updateFilterLabel(@Nullable TextView labelView,
                                   @Nullable String value,
                                   @NonNull String fallback) {
        if (labelView == null) {
            return;
        }
        String safeText = value == null ? "" : value.trim();
        labelView.setText(safeText.isEmpty() ? fallback : safeText);
    }

    // 从 Spinner 当前状态安全读取值，缺失时回退到上次选择。
    @NonNull
    private String safeSpinnerValue(@Nullable Spinner spinner,
                                    @Nullable String fallback,
                                    @NonNull String defaultValue) {
        if (spinner == null || spinner.getSelectedItem() == null) {
            String safeFallback = fallback == null ? "" : fallback.trim();
            return safeFallback.isEmpty() ? defaultValue : safeFallback;
        }
        String selected = spinner.getSelectedItem().toString().trim();
        return selected.isEmpty() ? defaultValue : selected;
    }

    // 根据指定值回填 Spinner 选中项，避免重复弹出时筛选状态丢失。
    private void setSpinnerSelectionByValue(@Nullable Spinner spinner, @Nullable String value) {
        if (spinner == null || value == null) {
            return;
        }
        SpinnerAdapter adapter = spinner.getAdapter();
        if (adapter == null) {
            return;
        }
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item != null && value.equals(item.toString())) {
                spinner.setSelection(i, false);
                return;
            }
        }
        if (adapter.getCount() > 0) {
            spinner.setSelection(0, false);
        }
    }

    // 复制交易列表，避免外部在弹层打开期间改写底层数据。
    @NonNull
    private List<TradeRecordItem> copyTrades(@Nullable List<TradeRecordItem> sourceTrades) {
        return sourceTrades == null ? new ArrayList<>() : new ArrayList<>(sourceTrades);
    }
}
