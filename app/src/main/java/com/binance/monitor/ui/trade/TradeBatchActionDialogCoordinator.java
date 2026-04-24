/*
 * 批量交易交互协调器，负责把图表页和账户页的批量操作入口统一收口到同一条对话链和提交链。
 * 与 BatchTradeCoordinator、TradeCommandFactory 和账户快照数据协同工作。
 */
package com.binance.monitor.ui.trade;

import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.R;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.ui.chart.MarketChartTradeSupport;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class TradeBatchActionDialogCoordinator {

    public interface CompletionListener {
        void onTradeBatchCompleted();
    }

    public static final class BatchActionContext {
        private final String accountId;
        private final String accountMode;
        private final String preferredSymbol;
        private final List<PositionItem> positions;
        private final List<PositionItem> pendingOrders;

        public BatchActionContext(@Nullable String accountId,
                                  @Nullable String accountMode,
                                  @Nullable String preferredSymbol,
                                  @Nullable List<PositionItem> positions,
                                  @Nullable List<PositionItem> pendingOrders) {
            this.accountId = safe(accountId);
            this.accountMode = safe(accountMode);
            this.preferredSymbol = safe(preferredSymbol);
            this.positions = copyItems(positions);
            this.pendingOrders = copyItems(pendingOrders);
        }

        @NonNull
        public String getAccountId() {
            return accountId;
        }

        @NonNull
        public String getAccountMode() {
            return accountMode;
        }

        @NonNull
        public String getPreferredSymbol() {
            return preferredSymbol;
        }

        @NonNull
        public List<PositionItem> getPositions() {
            return positions;
        }

        @NonNull
        public List<PositionItem> getPendingOrders() {
            return pendingOrders;
        }
    }

    private interface PositionScopeHandler {
        void onSelected(@NonNull String title, @NonNull List<PositionItem> items);
    }

    private final AppCompatActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor;
    private final BatchTradeCoordinator batchTradeCoordinator;
    private final CompletionListener completionListener;
    private final ConfigManager configManager;
    @Nullable
    private Future<?> runningTask;

    public TradeBatchActionDialogCoordinator(@NonNull AppCompatActivity activity,
                                             @NonNull ExecutorService ioExecutor,
                                             @NonNull BatchTradeCoordinator batchTradeCoordinator,
                                             @NonNull CompletionListener completionListener) {
        this.activity = activity;
        this.ioExecutor = ioExecutor;
        this.batchTradeCoordinator = batchTradeCoordinator;
        this.completionListener = completionListener;
        this.configManager = ConfigManager.getInstance(activity.getApplicationContext());
    }

    // 打开统一批量操作入口。
    public void showEntry(@NonNull BatchActionContext context) {
        if (context.getAccountId().isEmpty()) {
            Toast.makeText(activity, "当前账户未连接，暂时不能批量操作", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[]{
                "批量平仓",
                "批量撤销挂单",
                "批量修改挂单",
                "批量修改持仓 TP/SL"
        };
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("批量操作")
                .setItems(items, (dialogInterface, which) -> {
                    if (which == 0) {
                        showBatchCloseModePicker(context);
                        return;
                    }
                    if (which == 1) {
                        showPendingScopePicker(context, "选择要撤销的挂单范围", new PositionScopeHandler() {
                            @Override
                            public void onSelected(@NonNull String title, @NonNull List<PositionItem> items) {
                                BatchTradePlan plan = buildPendingCancelPlan(context, title, items);
                                submitPlanWithGate(plan, title, true);
                            }
                        });
                        return;
                    }
                    if (which == 2) {
                        showPendingModifyModePicker(context);
                        return;
                    }
                    showPositionModifyModePicker(context);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
    }

    // 页面离开时取消仍在排队的批量任务。
    public void cancelRunningTask() {
        if (runningTask != null) {
            runningTask.cancel(true);
            runningTask = null;
        }
    }

    // 批量平仓支持按全部、产品、方向和盈亏四种口径筛选。
    private void showBatchCloseModePicker(@NonNull BatchActionContext context) {
        if (context.getPositions().isEmpty()) {
            Toast.makeText(activity, "当前没有可批量平仓的持仓", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[]{"全部平仓", "按产品平仓", "按方向平仓", "按盈亏平仓"};
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("选择批量平仓方式")
                .setItems(items, (dialogInterface, which) -> {
                    if (which == 0) {
                        submitPlanWithGate(buildBatchClosePlan(context, "全部平仓", context.getPositions()), "全部平仓", true);
                        return;
                    }
                    if (which == 1) {
                        showPositionScopePicker(context, "选择要平仓的产品", true, false, false, new PositionScopeHandler() {
                            @Override
                            public void onSelected(@NonNull String title, @NonNull List<PositionItem> items) {
                                submitPlanWithGate(buildBatchClosePlan(context, title, items), title, true);
                            }
                        });
                        return;
                    }
                    if (which == 2) {
                        showDirectionPicker(context);
                        return;
                    }
                    showProfitPicker(context);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
    }

    // 按买入/卖出筛选批量平仓。
    private void showDirectionPicker(@NonNull BatchActionContext context) {
        String[] items = new String[]{"全部买入持仓", "全部卖出持仓"};
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("选择方向")
                .setItems(items, (dialogInterface, which) -> {
                    boolean buySide = which == 0;
                    List<PositionItem> matches = new ArrayList<>();
                    for (PositionItem item : context.getPositions()) {
                        if (item != null && isBuySide(item.getSide()) == buySide) {
                            matches.add(item);
                        }
                    }
                    String title = buySide ? "全部买入持仓" : "全部卖出持仓";
                    submitPlanWithGate(buildBatchClosePlan(context, title, matches), title, true);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
    }

    // 按盈利/亏损筛选批量平仓。
    private void showProfitPicker(@NonNull BatchActionContext context) {
        String[] items = new String[]{"全部盈利持仓", "全部亏损持仓"};
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("选择盈亏方向")
                .setItems(items, (dialogInterface, which) -> {
                    boolean profit = which == 0;
                    List<PositionItem> matches = new ArrayList<>();
                    for (PositionItem item : context.getPositions()) {
                        if (item != null && isProfitPosition(item) == profit) {
                            matches.add(item);
                        }
                    }
                    String title = profit ? "全部盈利持仓" : "全部亏损持仓";
                    submitPlanWithGate(buildBatchClosePlan(context, title, matches), title, true);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
    }

    // 批量修改挂单固定走“先选产品，再选方向”，避免不同产品或不同方向共用一套参数。
    private void showPendingModifyModePicker(@NonNull BatchActionContext context) {
        if (context.getPendingOrders().isEmpty()) {
            Toast.makeText(activity, "当前没有可修改的挂单", Toast.LENGTH_SHORT).show();
            return;
        }
        showProductThenDirectionScopePicker(
                context.getPendingOrders(),
                context.getPreferredSymbol(),
                "选择要批量修改的挂单产品",
                "选择要批量修改的挂单方向",
                "买入挂单",
                "卖出挂单",
                new PositionScopeHandler() {
                    @Override
                    public void onSelected(@NonNull String title, @NonNull List<PositionItem> items) {
                        showPendingModifyDialog(context, title, items);
                    }
                }
        );
    }

    // 批量修改持仓 TP/SL 同样固定走“先选产品，再选方向”，避免买卖仓位共用一套止盈止损。
    private void showPositionModifyModePicker(@NonNull BatchActionContext context) {
        if (context.getPositions().isEmpty()) {
            Toast.makeText(activity, "当前没有可修改的持仓", Toast.LENGTH_SHORT).show();
            return;
        }
        showProductThenDirectionScopePicker(
                context.getPositions(),
                context.getPreferredSymbol(),
                "选择要批量修改的持仓产品",
                "选择要批量修改的持仓方向",
                "买入持仓",
                "卖出持仓",
                new PositionScopeHandler() {
                    @Override
                    public void onSelected(@NonNull String title, @NonNull List<PositionItem> items) {
                        showPositionModifyDialog(context, title, items);
                    }
                }
        );
    }

    // 挂单范围只允许按产品收口，避免不同产品被同一价格输入误改。
    private void showPendingScopePicker(@NonNull BatchActionContext context,
                                        @NonNull String title,
                                        @NonNull PositionScopeHandler handler) {
        List<PositionItem> pendingOrders = context.getPendingOrders();
        if (pendingOrders.isEmpty()) {
            Toast.makeText(activity, "当前没有可操作的挂单", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, List<PositionItem>> grouped = groupBySymbol(pendingOrders, context.getPreferredSymbol());
        List<String> labels = new ArrayList<>();
        List<List<PositionItem>> buckets = new ArrayList<>();
        labels.add("全部挂单（" + pendingOrders.size() + "）");
        buckets.add(new ArrayList<>(pendingOrders));
        for (Map.Entry<String, List<PositionItem>> entry : grouped.entrySet()) {
            labels.add(entry.getKey() + "（" + entry.getValue().size() + "）");
            buckets.add(entry.getValue());
        }
        showScopeDialog(title, labels, buckets, handler);
    }

    // 批量修改类入口统一先按产品，再在该产品下按方向分桶。
    private void showProductThenDirectionScopePicker(@NonNull List<PositionItem> source,
                                                     @Nullable String preferredSymbol,
                                                     @NonNull String productTitle,
                                                     @NonNull String directionTitle,
                                                     @NonNull String buyLabel,
                                                     @NonNull String sellLabel,
                                                     @NonNull PositionScopeHandler handler) {
        Map<String, List<PositionItem>> grouped = groupBySymbol(source, preferredSymbol);
        List<String> labels = new ArrayList<>();
        List<List<PositionItem>> buckets = new ArrayList<>();
        for (Map.Entry<String, List<PositionItem>> entry : grouped.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            labels.add(entry.getKey() + "（" + entry.getValue().size() + "）");
            buckets.add(entry.getValue());
        }
        showScopeDialog(productTitle, labels, buckets, new PositionScopeHandler() {
            @Override
            public void onSelected(@NonNull String title, @NonNull List<PositionItem> items) {
                String productName = stripScopeCountSuffix(title);
                showDirectionScopePicker(
                        items,
                        directionTitle + " · " + productName,
                        productName + " · " + buyLabel,
                        productName + " · " + sellLabel,
                        handler
                );
            }
        });
    }

    // 方向范围统一只分买入和卖出两桶，供挂单修改和持仓 TP/SL 修改复用。
    private void showDirectionScopePicker(@NonNull List<PositionItem> source,
                                          @NonNull String title,
                                          @NonNull String buyLabel,
                                          @NonNull String sellLabel,
                                          @NonNull PositionScopeHandler handler) {
        List<String> labels = new ArrayList<>();
        List<List<PositionItem>> buckets = new ArrayList<>();
        List<PositionItem> buyItems = filterItemsByDirection(source, true);
        if (!buyItems.isEmpty()) {
            labels.add(buyLabel + "（" + buyItems.size() + "）");
            buckets.add(buyItems);
        }
        List<PositionItem> sellItems = filterItemsByDirection(source, false);
        if (!sellItems.isEmpty()) {
            labels.add(sellLabel + "（" + sellItems.size() + "）");
            buckets.add(sellItems);
        }
        showScopeDialog(title, labels, buckets, handler);
    }

    @NonNull
    private String stripScopeCountSuffix(@NonNull String raw) {
        int index = raw.indexOf('（');
        if (index <= 0) {
            return raw;
        }
        return raw.substring(0, index).trim();
    }

    // 持仓范围既可按产品，也可按其它口径调用。
    private void showPositionScopePicker(@NonNull BatchActionContext context,
                                         @NonNull String title,
                                         boolean groupByProduct,
                                         boolean buySide,
                                         boolean profitOnly,
                                         @NonNull PositionScopeHandler handler) {
        List<PositionItem> source = new ArrayList<>();
        for (PositionItem item : context.getPositions()) {
            if (item == null) {
                continue;
            }
            if (buySide && !isBuySide(item.getSide())) {
                continue;
            }
            if (profitOnly && !isProfitPosition(item)) {
                continue;
            }
            source.add(item);
        }
        if (source.isEmpty()) {
            Toast.makeText(activity, "当前没有符合条件的持仓", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!groupByProduct) {
            handler.onSelected(title, source);
            return;
        }
        Map<String, List<PositionItem>> grouped = groupBySymbol(source, context.getPreferredSymbol());
        List<String> labels = new ArrayList<>();
        List<List<PositionItem>> buckets = new ArrayList<>();
        for (Map.Entry<String, List<PositionItem>> entry : grouped.entrySet()) {
            labels.add(entry.getKey() + "（" + entry.getValue().size() + "）");
            buckets.add(entry.getValue());
        }
        showScopeDialog(title, labels, buckets, handler);
    }

    @NonNull
    private List<PositionItem> filterItemsByDirection(@Nullable List<PositionItem> source, boolean buySide) {
        List<PositionItem> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        for (PositionItem item : source) {
            if (item != null && isBuySide(item.getSide()) == buySide) {
                result.add(item);
            }
        }
        return result;
    }

    // 通用范围选择列表。
    private void showScopeDialog(@NonNull String title,
                                 @NonNull List<String> labels,
                                 @NonNull List<List<PositionItem>> buckets,
                                 @NonNull PositionScopeHandler handler) {
        if (labels.isEmpty() || buckets.isEmpty()) {
            Toast.makeText(activity, "当前没有可操作的对象", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialogInterface, which) -> {
                    if (which < 0 || which >= buckets.size()) {
                        return;
                    }
                    List<PositionItem> selected = buckets.get(which);
                    if (selected == null || selected.isEmpty()) {
                        Toast.makeText(activity, "当前范围没有可操作的对象", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    handler.onSelected(labels.get(which), selected);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
    }

    // 批量修改挂单时，价格留空表示沿用原值，只要改任意一项即可。
    private void showPendingModifyDialog(@NonNull BatchActionContext context,
                                         @NonNull String title,
                                         @NonNull List<PositionItem> pendingOrders) {
        LinearLayout container = createFormContainer();
        TextInputLayout priceLayout = createInputLayout("新挂单价格（留空则沿用原值）");
        TextInputEditText priceInput = createInput(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        priceLayout.addView(priceInput);
        container.addView(priceLayout);

        TextInputLayout slLayout = createInputLayout("新止损（留空则沿用原值）");
        TextInputEditText slInput = createInput(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        slLayout.addView(slInput);
        container.addView(slLayout);

        TextInputLayout tpLayout = createInputLayout("新止盈（留空则沿用原值）");
        TextInputEditText tpInput = createInput(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tpLayout.addView(tpInput);
        container.addView(tpLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("批量修改挂单")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            BatchTradePlan plan = buildPendingModifyPlan(context, title, pendingOrders, priceInput, slInput, tpInput);
            submitPlanWithGate(plan, "批量修改挂单 " + title, false);
            dialog.dismiss();
        });
    }

    // 批量修改持仓 TP/SL 时同样允许空值沿用。
    private void showPositionModifyDialog(@NonNull BatchActionContext context,
                                          @NonNull String title,
                                          @NonNull List<PositionItem> positions) {
        LinearLayout container = createFormContainer();
        TextInputLayout slLayout = createInputLayout("新止损（留空则沿用原值）");
        TextInputEditText slInput = createInput(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        slLayout.addView(slInput);
        container.addView(slLayout);

        TextInputLayout tpLayout = createInputLayout("新止盈（留空则沿用原值）");
        TextInputEditText tpInput = createInput(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tpLayout.addView(tpInput);
        container.addView(tpLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("批量修改持仓 TP/SL")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            BatchTradePlan plan = buildPositionModifyPlan(context, title, positions, slInput, tpInput);
            submitPlanWithGate(plan, "批量修改持仓 " + title, false);
            dialog.dismiss();
        });
    }

    // 关闭类批量动作支持一键模式免确认；价格修改类保留一次确认。
    private void submitPlanWithGate(@NonNull BatchTradePlan plan,
                                    @NonNull String confirmTitle,
                                    boolean closableAction) {
        boolean skipConfirm = closableAction && configManager.isTradeOneClickModeEnabled();
        if (skipConfirm) {
            submitPlan(plan);
            return;
        }
        String message = confirmTitle + "\n共 " + plan.getItems().size() + " 项";
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("确认批量操作")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", (dialogInterface, which) -> submitPlan(plan))
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
    }

    // 正式提交批量计划，并在结果返回后统一提示和刷新。
    private void submitPlan(@NonNull BatchTradePlan plan) {
        if (runningTask != null) {
            Toast.makeText(activity, "上一笔批量操作仍在处理中", Toast.LENGTH_SHORT).show();
            return;
        }
        runningTask = ioExecutor.submit(() -> {
            try {
                BatchTradeCoordinator.ExecutionResult result = batchTradeCoordinator.submit(plan);
                mainHandler.post(() -> handleBatchResult(result));
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "批量操作失败" : exception.getMessage().trim();
                mainHandler.post(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
            } finally {
                runningTask = null;
            }
        });
    }

    // 批量结果统一显示摘要和单项结果。
    private void handleBatchResult(@Nullable BatchTradeCoordinator.ExecutionResult executionResult) {
        if (executionResult == null || executionResult.getReceipt() == null) {
            Toast.makeText(activity, "批量结果未确认，请稍后刷新", Toast.LENGTH_SHORT).show();
            return;
        }
        if (executionResult.getLatestCache() != null) {
            completionListener.onTradeBatchCompleted();
        }
        List<String> lines = BatchTradeResultFormatter.buildItemLines(executionResult.getReceipt());
        StringBuilder builder = new StringBuilder();
        builder.append(BatchTradeResultFormatter.buildSummary(executionResult.getReceipt()));
        if (!lines.isEmpty()) {
            builder.append("\n\n");
            for (int index = 0; index < lines.size(); index++) {
                if (index > 0) {
                    builder.append('\n');
                }
                builder.append(lines.get(index));
            }
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(resolveResultTitle(executionResult))
                .setMessage(builder.toString())
                .setPositiveButton("知道了", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(activity));
    }

    @NonNull
    private String resolveResultTitle(@NonNull BatchTradeCoordinator.ExecutionResult executionResult) {
        BatchTradeCoordinator.UiState state = executionResult.getUiState();
        if (state == BatchTradeCoordinator.UiState.ACCEPTED) {
            return "批量操作已受理";
        }
        if (state == BatchTradeCoordinator.UiState.PARTIAL) {
            return "批量操作部分成功";
        }
        if (state == BatchTradeCoordinator.UiState.RESULT_UNCONFIRMED) {
            return "结果未确认";
        }
        return "批量操作失败";
    }

    @NonNull
    private BatchTradePlan buildBatchClosePlan(@NonNull BatchActionContext context,
                                               @NonNull String summary,
                                               @NonNull List<PositionItem> positions) {
        requireNonEmptyItems(positions, "当前没有可平仓的持仓");
        BatchTradePlan plan = TradeComplexActionPlanner.planBatchClose(
                context.getAccountId(),
                context.getAccountMode(),
                positions
        );
        return replacePlanSummary(plan, summary);
    }

    @NonNull
    private BatchTradePlan buildPendingCancelPlan(@NonNull BatchActionContext context,
                                                  @NonNull String summary,
                                                  @NonNull List<PositionItem> pendingOrders) {
        requireNonEmptyItems(pendingOrders, "当前没有可撤销的挂单");
        List<BatchTradeItem> items = new ArrayList<>();
        int index = 1;
        for (PositionItem item : pendingOrders) {
            long orderTicket = item == null ? 0L : item.getOrderId();
            if (orderTicket <= 0L) {
                continue;
            }
            items.add(new BatchTradeItem(
                    "cancel-" + index,
                    "撤销挂单 " + resolveDisplaySymbol(item) + " #" + orderTicket,
                    TradeCommandFactory.pendingCancel(context.getAccountId(), resolveTradeSymbol(item), orderTicket),
                    null
            ));
            index++;
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("当前挂单缺少有效票号，暂时不能批量撤销");
        }
        return new BatchTradePlan(
                nextBatchId("batch-pending-cancel"),
                "BEST_EFFORT",
                context.getAccountMode(),
                summary,
                items
        );
    }

    @NonNull
    private BatchTradePlan buildPendingModifyPlan(@NonNull BatchActionContext context,
                                                  @NonNull String summary,
                                                  @NonNull List<PositionItem> pendingOrders,
                                                  @Nullable EditText priceInput,
                                                  @Nullable EditText slInput,
                                                  @Nullable EditText tpInput) {
        requireNonEmptyItems(pendingOrders, "当前没有可修改的挂单");
        double newPrice = parseOptionalValue(priceInput);
        double newSl = parseOptionalValue(slInput);
        double newTp = parseOptionalValue(tpInput);
        boolean changed = newPrice > 0d || newSl > 0d || newTp > 0d;
        if (!changed) {
            throw new IllegalArgumentException("请至少填写一个新的价格");
        }
        List<BatchTradeItem> items = new ArrayList<>();
        int index = 1;
        for (PositionItem item : pendingOrders) {
            long orderTicket = item == null ? 0L : item.getOrderId();
            if (orderTicket <= 0L) {
                continue;
            }
            double price = newPrice > 0d ? newPrice : item.getPendingPrice();
            double sl = newSl > 0d ? newSl : item.getStopLoss();
            double tp = newTp > 0d ? newTp : item.getTakeProfit();
            items.add(new BatchTradeItem(
                    "modify-pending-" + index,
                    "修改挂单 " + resolveDisplaySymbol(item) + " #" + orderTicket,
                    TradeCommandFactory.pendingModify(context.getAccountId(), resolveTradeSymbol(item), orderTicket, price, sl, tp),
                    null
            ));
            index++;
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("当前挂单缺少有效票号，暂时不能批量修改");
        }
        return new BatchTradePlan(nextBatchId("batch-pending-modify"), "BEST_EFFORT", context.getAccountMode(), summary, items);
    }

    @NonNull
    private BatchTradePlan buildPositionModifyPlan(@NonNull BatchActionContext context,
                                                   @NonNull String summary,
                                                   @NonNull List<PositionItem> positions,
                                                   @Nullable EditText slInput,
                                                   @Nullable EditText tpInput) {
        requireNonEmptyItems(positions, "当前没有可修改的持仓");
        double newSl = parseOptionalValue(slInput);
        double newTp = parseOptionalValue(tpInput);
        boolean changed = newSl > 0d || newTp > 0d;
        if (!changed) {
            throw new IllegalArgumentException("请至少填写一个新的止盈或止损");
        }
        List<BatchTradeItem> items = new ArrayList<>();
        int index = 1;
        for (PositionItem item : positions) {
            long positionTicket = item == null ? 0L : item.getPositionTicket();
            if (positionTicket <= 0L) {
                continue;
            }
            double sl = newSl > 0d ? newSl : item.getStopLoss();
            double tp = newTp > 0d ? newTp : item.getTakeProfit();
            items.add(new BatchTradeItem(
                    "modify-tpsl-" + index,
                    "修改持仓 " + resolveDisplaySymbol(item) + " #" + positionTicket,
                    TradeCommandFactory.modifyTpSl(
                            context.getAccountId(),
                            resolveTradeSymbol(item),
                            positionTicket,
                            resolveReferencePrice(item),
                            sl,
                            tp
                    ),
                    null
            ));
            index++;
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("当前持仓缺少有效票号，暂时不能批量修改");
        }
        return new BatchTradePlan(nextBatchId("batch-position-modify"), "BEST_EFFORT", context.getAccountMode(), summary, items);
    }

    @NonNull
    private BatchTradePlan replacePlanSummary(@NonNull BatchTradePlan plan, @NonNull String summary) {
        return new BatchTradePlan(plan.getBatchId(), plan.getStrategy(), plan.getAccountMode(), summary, plan.getItems());
    }

    private void requireNonEmptyItems(@Nullable List<PositionItem> items, @NonNull String message) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    @NonNull
    private LinearLayout createFormContainer() {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(8);
        container.setPadding(padding, padding, padding, padding);
        return container;
    }

    @NonNull
    private TextInputLayout createInputLayout(@NonNull String hint) {
        TextInputLayout layout = new TextInputLayout(activity);
        layout.setHint(hint);
        UiPaletteManager.styleInputField(layout, UiPaletteManager.resolve(activity));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        layout.setLayoutParams(params);
        return layout;
    }

    @NonNull
    private TextInputEditText createInput(int inputType) {
        TextInputEditText input = new TextInputEditText(activity);
        input.setInputType(inputType);
        input.setSingleLine(true);
        return input;
    }

    private int dp(int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    private double parseOptionalValue(@Nullable EditText input) {
        String text = input == null || input.getText() == null ? "" : input.getText().toString();
        return MarketChartTradeSupport.parseOptionalDouble(text, 0d);
    }

    @NonNull
    private Map<String, List<PositionItem>> groupBySymbol(@NonNull List<PositionItem> items,
                                                          @Nullable String preferredSymbol) {
        LinkedHashMap<String, List<PositionItem>> grouped = new LinkedHashMap<>();
        String preferred = safe(preferredSymbol).toUpperCase(Locale.ROOT);
        if (!preferred.isEmpty()) {
            grouped.put(preferred, new ArrayList<>());
        }
        for (PositionItem item : items) {
            String symbol = resolveDisplaySymbol(item);
            List<PositionItem> bucket = grouped.get(symbol);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(symbol, bucket);
            }
            bucket.add(item);
        }
        if (!preferred.isEmpty() && grouped.containsKey(preferred) && grouped.get(preferred).isEmpty()) {
            grouped.remove(preferred);
        }
        return grouped;
    }

    private boolean isBuySide(@Nullable String side) {
        String normalized = safe(side).toLowerCase(Locale.ROOT);
        return "buy".equals(normalized) || "long".equals(normalized);
    }

    private boolean isProfitPosition(@NonNull PositionItem item) {
        return item.getTotalPnL() >= 0d;
    }

    @NonNull
    private String resolveTradeSymbol(@Nullable PositionItem item) {
        if (item == null) {
            return "";
        }
        String code = safe(item.getCode());
        if (!code.isEmpty()) {
            return code.toUpperCase(Locale.ROOT);
        }
        return safe(item.getProductName()).toUpperCase(Locale.ROOT);
    }

    @NonNull
    private String resolveDisplaySymbol(@Nullable PositionItem item) {
        String symbol = resolveTradeSymbol(item);
        return symbol.isEmpty() ? "--" : symbol;
    }

    private double resolveReferencePrice(@Nullable PositionItem item) {
        if (item == null) {
            return 0d;
        }
        if (item.getCostPrice() > 0d) {
            return item.getCostPrice();
        }
        if (item.getPendingPrice() > 0d) {
            return item.getPendingPrice();
        }
        return item.getLatestPrice();
    }

    @NonNull
    private String nextBatchId(@NonNull String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }

    @NonNull
    private static <T> List<T> copyItems(@Nullable List<T> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
