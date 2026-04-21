/*
 * 图表页交易协调器，统一处理交易菜单、表单、异步执行和结果反馈。
 * 该协调器只负责交易链，页面本体只保留控件装配、生命周期和图表刷新。
 */
package com.binance.monitor.ui.chart;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.R;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.BatchTradeReceipt;
import com.binance.monitor.data.model.v2.trade.TradeTemplate;
import com.binance.monitor.databinding.ActivityMarketChartBinding;
import com.binance.monitor.databinding.DialogTradeCommandBinding;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.ui.trade.BatchTradeCoordinator;
import com.binance.monitor.ui.trade.BatchTradeResultFormatter;
import com.binance.monitor.ui.trade.TradeAuditActivity;
import com.binance.monitor.ui.trade.TradeComplexActionPlanner;
import com.binance.monitor.ui.trade.TradeCommandFactory;
import com.binance.monitor.ui.trade.TradeCommandStateMachine;
import com.binance.monitor.ui.trade.TradeExecutionCoordinator;
import com.binance.monitor.ui.trade.TradeTemplateRepository;
import com.binance.monitor.util.FormatUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

final class MarketChartTradeDialogCoordinator {
    private static final String COMPLEX_ACTION_FULL_CLOSE = "全部平仓";
    private static final String COMPLEX_ACTION_PARTIAL_CLOSE = "部分平仓";
    private static final String COMPLEX_ACTION_ADD_POSITION = "加仓";
    private static final String COMPLEX_ACTION_REVERSE = "反手";
    private static final String COMPLEX_ACTION_CLOSE_BY = "对锁平仓";

    interface Host {
        void refreshChartOverlays();
    }

    interface SymbolProvider {
        @NonNull
        String getSelectedSymbol();
    }

    interface CandleProvider {
        @NonNull
        List<CandleEntry> getLoadedCandles();
    }

    enum ChartTradeAction {
        OPEN_BUY,
        OPEN_SELL,
        PENDING_ADD,
        PENDING_MODIFY,
        CLOSE_POSITION,
        MODIFY_TPSL,
        PENDING_CANCEL
    }

    private static final class TradeDialogInput {
        private final ChartTradeAction action;
        private final String symbol;
        private final String orderType;
        private final double volume;
        private final double price;
        private final double sl;
        private final double tp;
        private final long positionTicket;
        private final long orderTicket;
        private final String complexAction;
        private final double targetVolume;
        private final PositionItem targetItem;

        // 记录一次交易弹窗采样后的完整输入。
        private TradeDialogInput(ChartTradeAction action,
                                 String symbol,
                                 String orderType,
                                 double volume,
                                 double price,
                                 double sl,
                                 double tp,
                                 long positionTicket,
                                 long orderTicket,
                                 String complexAction,
                                 double targetVolume,
                                 @Nullable PositionItem targetItem) {
            this.action = action;
            this.symbol = symbol == null ? "" : symbol;
            this.orderType = orderType == null ? "" : orderType;
            this.volume = volume;
            this.price = price;
            this.sl = sl;
            this.tp = tp;
            this.positionTicket = positionTicket;
            this.orderTicket = orderTicket;
            this.complexAction = complexAction == null ? "" : complexAction;
            this.targetVolume = targetVolume;
            this.targetItem = targetItem;
        }
    }

    private final AppCompatActivity activity;
    private final ActivityMarketChartBinding binding;
    private final Handler mainHandler;
    private final ExecutorService ioExecutor;
    private final AccountStatsPreloadManager accountStatsPreloadManager;
    private final TradeExecutionCoordinator tradeExecutionCoordinator;
    private final BatchTradeCoordinator batchTradeCoordinator;
    private final TradeTemplateRepository tradeTemplateRepository;
    private final SecureSessionPrefs secureSessionPrefs;
    private final SymbolProvider symbolProvider;
    private final CandleProvider candleProvider;
    private final Host host;
    private Future<?> tradePrepareTask;
    private Future<?> tradeSubmitTask;
    private volatile boolean tradeFlowRunning;

    // 创建图表页交易协调器。
    MarketChartTradeDialogCoordinator(@NonNull AppCompatActivity activity,
                                      @NonNull ActivityMarketChartBinding binding,
                                      @NonNull Handler mainHandler,
                                      @Nullable ExecutorService ioExecutor,
                                      @Nullable AccountStatsPreloadManager accountStatsPreloadManager,
                                      @Nullable TradeExecutionCoordinator tradeExecutionCoordinator,
                                      @Nullable BatchTradeCoordinator batchTradeCoordinator,
                                      @Nullable TradeTemplateRepository tradeTemplateRepository,
                                      @NonNull SymbolProvider symbolProvider,
                                      @NonNull CandleProvider candleProvider,
                                      @NonNull Host host) {
        this.activity = activity;
        this.binding = binding;
        this.mainHandler = mainHandler;
        this.ioExecutor = ioExecutor;
        this.accountStatsPreloadManager = accountStatsPreloadManager;
        this.tradeExecutionCoordinator = tradeExecutionCoordinator;
        this.batchTradeCoordinator = batchTradeCoordinator;
        this.tradeTemplateRepository = tradeTemplateRepository;
        this.secureSessionPrefs = new SecureSessionPrefs(activity.getApplicationContext());
        this.symbolProvider = symbolProvider;
        this.candleProvider = candleProvider;
        this.host = host;
    }

    // 根据动作类型打开统一表单。
    void showTradeCommandDialog(@NonNull ChartTradeAction action, @Nullable PositionItem targetItem) {
        if (!canPresentTradeUi()) {
            return;
        }
        DialogTradeCommandBinding dialogBinding = DialogTradeCommandBinding.inflate(
                LayoutInflater.from(activity)
        );
        configureTradeDialog(action, targetItem, dialogBinding);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(resolveTradeDialogTitle(action))
                .setView(dialogBinding.getRoot())
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                requestTradeExecution(buildDialogInput(action, targetItem, dialogBinding));
                dialog.dismiss();
            } catch (IllegalArgumentException exception) {
                showTradeMessage(exception.getMessage());
            }
        }));
        dialog.show();
        applyDialogSurface(dialog);
    }

    // 供图表快捷交易直接提交命令，继续复用统一检查、确认、提交和刷新链路。
    void submitDirectTradeCommand(@NonNull TradeCommand command) {
        requestTradeExecution(command);
    }

    // 页面离开时取消仍在排队的交易任务。
    void cancelTradeTasks() {
        if (tradePrepareTask != null) {
            tradePrepareTask.cancel(true);
            tradePrepareTask = null;
        }
        if (tradeSubmitTask != null) {
            tradeSubmitTask.cancel(true);
            tradeSubmitTask = null;
        }
        tradeFlowRunning = false;
    }

    // 给统一表单填默认值并控制可见字段。
    private void configureTradeDialog(@NonNull ChartTradeAction action,
                                      @Nullable PositionItem targetItem,
                                      @NonNull DialogTradeCommandBinding dialogBinding) {
        boolean showOrderType = action == ChartTradeAction.PENDING_ADD;
        boolean showComplexAction = action == ChartTradeAction.CLOSE_POSITION && targetItem != null;
        boolean showVolume = action == ChartTradeAction.OPEN_BUY
                || action == ChartTradeAction.OPEN_SELL
                || action == ChartTradeAction.PENDING_ADD;
        boolean showPrice = action == ChartTradeAction.PENDING_ADD
                || action == ChartTradeAction.PENDING_MODIFY;
        boolean showRiskFields = action != ChartTradeAction.CLOSE_POSITION
                && action != ChartTradeAction.PENDING_CANCEL;
        double referencePrice = resolveTradeReferencePrice(targetItem, targetItem);
        TradeTemplate defaultTemplate = resolveDefaultTradeTemplate(action);

        dialogBinding.layoutTradeOrderType.setVisibility(showOrderType ? View.VISIBLE : View.GONE);
        dialogBinding.layoutTradeComplexAction.setVisibility(showComplexAction ? View.VISIBLE : View.GONE);
        dialogBinding.tvTradeVolumeLabel.setVisibility(showVolume ? View.VISIBLE : View.GONE);
        dialogBinding.etTradeVolume.setVisibility(showVolume ? View.VISIBLE : View.GONE);
        dialogBinding.tvTradePriceLabel.setVisibility(showPrice ? View.VISIBLE : View.GONE);
        dialogBinding.etTradePrice.setVisibility(showPrice ? View.VISIBLE : View.GONE);
        dialogBinding.tvTradeSlLabel.setVisibility(showRiskFields ? View.VISIBLE : View.GONE);
        dialogBinding.etTradeSl.setVisibility(showRiskFields ? View.VISIBLE : View.GONE);
        dialogBinding.tvTradeTpLabel.setVisibility(showRiskFields ? View.VISIBLE : View.GONE);
        dialogBinding.etTradeTp.setVisibility(showRiskFields ? View.VISIBLE : View.GONE);
        dialogBinding.tvTradeCommandHint.setText(resolveTradeDialogHint(action, targetItem, referencePrice));

        if (showOrderType) {
            dialogBinding.spinnerTradeOrderType.setAdapter(createTradeOrderTypeAdapter(
                    new String[]{"buy_limit", "sell_limit", "buy_stop", "sell_stop"}
            ));
        }
        if (showComplexAction) {
            dialogBinding.spinnerTradeComplexAction.setAdapter(createTradeOrderTypeAdapter(
                    new String[]{
                            COMPLEX_ACTION_FULL_CLOSE,
                            COMPLEX_ACTION_PARTIAL_CLOSE,
                            COMPLEX_ACTION_ADD_POSITION,
                            COMPLEX_ACTION_REVERSE,
                            COMPLEX_ACTION_CLOSE_BY
                    }
            ));
            dialogBinding.spinnerTradeComplexAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateTradeComplexActionFields(dialogBinding, targetItem);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    updateTradeComplexActionFields(dialogBinding, targetItem);
                }
            });
            dialogBinding.etTradeTargetVolume.setText(String.format(Locale.getDefault(), "%.2f", Math.abs(targetItem.getQuantity())));
            updateTradeComplexActionFields(dialogBinding, targetItem);
        } else {
            dialogBinding.tvTradeTargetVolumeLabel.setVisibility(View.GONE);
            dialogBinding.etTradeTargetVolume.setVisibility(View.GONE);
        }
        if (showVolume) {
            double defaultVolume = defaultTemplate == null ? 0d : defaultTemplate.getDefaultVolume();
            if (defaultVolume <= 0d && tradeTemplateRepository != null) {
                defaultVolume = tradeTemplateRepository.getDefaultVolume();
            }
            if (defaultVolume > 0d) {
                dialogBinding.etTradeVolume.setText(FormatUtils.formatVolume(defaultVolume));
            }
        }
        if (showPrice && referencePrice > 0d) {
            dialogBinding.etTradePrice.setText(FormatUtils.formatPrice(referencePrice));
        }
        if (showRiskFields && defaultTemplate != null) {
            if (defaultTemplate.getDefaultSl() > 0d) {
                dialogBinding.etTradeSl.setText(FormatUtils.formatPrice(defaultTemplate.getDefaultSl()));
            }
            if (defaultTemplate.getDefaultTp() > 0d) {
                dialogBinding.etTradeTp.setText(FormatUtils.formatPrice(defaultTemplate.getDefaultTp()));
            }
        }
        if (action == ChartTradeAction.PENDING_MODIFY && targetItem != null) {
            if (targetItem.getPendingPrice() > 0d) {
                dialogBinding.etTradePrice.setText(FormatUtils.formatPrice(targetItem.getPendingPrice()));
            }
            if (targetItem.getStopLoss() > 0d) {
                dialogBinding.etTradeSl.setText(FormatUtils.formatPrice(targetItem.getStopLoss()));
            }
            if (targetItem.getTakeProfit() > 0d) {
                dialogBinding.etTradeTp.setText(FormatUtils.formatPrice(targetItem.getTakeProfit()));
            }
        }
        if (action == ChartTradeAction.MODIFY_TPSL && targetItem != null) {
            if (targetItem.getStopLoss() > 0d) {
                dialogBinding.etTradeSl.setText(FormatUtils.formatPrice(targetItem.getStopLoss()));
            }
            if (targetItem.getTakeProfit() > 0d) {
                dialogBinding.etTradeTp.setText(FormatUtils.formatPrice(targetItem.getTakeProfit()));
            }
        }
    }

    // 构建表单输入，保证进入后台线程前已经完成页面值采样。
    private TradeDialogInput buildDialogInput(@NonNull ChartTradeAction action,
                                              @Nullable PositionItem targetItem,
                                              @NonNull DialogTradeCommandBinding dialogBinding) {
        String symbol = resolveTradeSymbol(targetItem);
        double referencePrice = resolveTradeReferencePrice(targetItem, targetItem);
        if (action == ChartTradeAction.OPEN_BUY || action == ChartTradeAction.OPEN_SELL) {
            double volume = requirePositiveTradeValue(dialogBinding.etTradeVolume, "手数");
            return new TradeDialogInput(action, symbol, "", volume, 0d,
                    parseOptionalTradeValue(dialogBinding.etTradeSl),
                    parseOptionalTradeValue(dialogBinding.etTradeTp),
                    0L, 0L, "", 0d, targetItem);
        }
        if (action == ChartTradeAction.PENDING_ADD) {
            double volume = requirePositiveTradeValue(dialogBinding.etTradeVolume, "手数");
            double price = requirePositiveTradeValue(dialogBinding.etTradePrice, "价格");
            Object selected = dialogBinding.spinnerTradeOrderType.getSelectedItem();
            String orderType = selected == null ? "" : selected.toString();
            if (orderType.trim().isEmpty()) {
                throw new IllegalArgumentException("请选择挂单类型");
            }
            return new TradeDialogInput(action, symbol, orderType, volume, price,
                    parseOptionalTradeValue(dialogBinding.etTradeSl),
                    parseOptionalTradeValue(dialogBinding.etTradeTp),
                    0L, 0L, "", 0d, targetItem);
        }
        if (action == ChartTradeAction.PENDING_MODIFY) {
            if (targetItem == null) {
                throw new IllegalArgumentException("未找到目标挂单，暂时不能修改");
            }
            if (targetItem.getOrderId() <= 0L) {
                throw new IllegalArgumentException("当前挂单缺少 orderTicket，暂时不能修改");
            }
            double price = parseOptionalTradeValue(dialogBinding.etTradePrice);
            double sl = parseOptionalTradeValue(dialogBinding.etTradeSl);
            double tp = parseOptionalTradeValue(dialogBinding.etTradeTp);
            if (price <= 0d && sl <= 0d && tp <= 0d) {
                throw new IllegalArgumentException("请至少填写价格、止损或止盈中的一项");
            }
            return new TradeDialogInput(action, symbol, "", 0d, price, sl, tp, 0L, targetItem.getOrderId(), "", 0d, targetItem);
        }
        if (action == ChartTradeAction.MODIFY_TPSL) {
            if (targetItem == null) {
                throw new IllegalArgumentException("未找到目标持仓，暂时不能改单");
            }
            if (targetItem.getPositionTicket() <= 0L) {
                throw new IllegalArgumentException("当前持仓缺少 positionTicket，暂时不能改单");
            }
            double sl = parseOptionalTradeValue(dialogBinding.etTradeSl);
            double tp = parseOptionalTradeValue(dialogBinding.etTradeTp);
            if (sl <= 0d && tp <= 0d) {
                throw new IllegalArgumentException("请至少填写止损或止盈");
            }
            return new TradeDialogInput(action, symbol, "", 0d,
                    referencePrice > 0d ? referencePrice : 0d,
                    sl, tp, targetItem.getPositionTicket(), 0L, "", 0d, targetItem);
        }
        if (action == ChartTradeAction.CLOSE_POSITION) {
            if (targetItem == null) {
                throw new IllegalArgumentException("未找到目标持仓，暂时不能平仓");
            }
            if (targetItem.getPositionTicket() <= 0L) {
                throw new IllegalArgumentException("当前持仓缺少 positionTicket，暂时不能平仓");
            }
            String complexAction = resolveSelectedTradeComplexAction(dialogBinding);
            return new TradeDialogInput(action,
                    symbol,
                    "",
                    Math.abs(targetItem.getQuantity()),
                    0d,
                    0d,
                    0d,
                    targetItem.getPositionTicket(),
                    0L,
                    complexAction,
                    parseOptionalTradeValue(dialogBinding.etTradeTargetVolume),
                    targetItem);
        }
        if (action == ChartTradeAction.PENDING_CANCEL) {
            if (targetItem == null) {
                throw new IllegalArgumentException("未找到目标挂单，暂时不能删除");
            }
            if (targetItem.getOrderId() <= 0L) {
                throw new IllegalArgumentException("当前挂单缺少 orderTicket，暂时不能删除");
            }
            return new TradeDialogInput(action,
                    symbol,
                    "",
                    0d,
                    0d,
                    0d,
                    0d,
                    0L,
                    targetItem.getOrderId(),
                    "",
                    0d,
                    targetItem);
        }
        throw new IllegalArgumentException("当前动作不支持表单提交");
    }

    // 触发一笔交易执行，统一走检查、确认、提交、强刷闭环。
    private void requestTradeExecution(@Nullable TradeDialogInput input) {
        if (input == null) {
            return;
        }
        AccountStatsPreloadManager.Cache baselineCache = resolveTradeBaselineCache();
        String accountId = resolveTradeAccountId(baselineCache);
        if (accountId.isEmpty()) {
            throw new IllegalArgumentException("当前账户未连接，暂时不能交易");
        }
        BatchTradePlan batchPlan = buildBatchTradePlan(accountId, baselineCache, input);
        if (batchPlan != null) {
            requestBatchTradeExecution(batchPlan);
            return;
        }
        requestTradeExecution(buildTradeCommand(accountId, input), baselineCache);
    }

    // 复杂动作统一先展开成批量计划，再交给第三阶段 batch 主链处理。
    @Nullable
    private BatchTradePlan buildBatchTradePlan(@NonNull String accountId,
                                               @Nullable AccountStatsPreloadManager.Cache baselineCache,
                                               @NonNull TradeDialogInput input) {
        if (input.action != ChartTradeAction.CLOSE_POSITION || input.targetItem == null) {
            return null;
        }
        if (COMPLEX_ACTION_FULL_CLOSE.equals(input.complexAction) || input.complexAction.trim().isEmpty()) {
            return null;
        }
        if (COMPLEX_ACTION_PARTIAL_CLOSE.equals(input.complexAction)) {
            if (input.targetVolume <= 0d) {
                throw new IllegalArgumentException("目标手数必须大于 0");
            }
            return TradeComplexActionPlanner.planPartialClose(
                    accountId,
                    resolveTradeAccountMode(baselineCache),
                    input.targetItem,
                    input.targetVolume
            );
        }
        if (COMPLEX_ACTION_ADD_POSITION.equals(input.complexAction)) {
            if (input.targetVolume <= 0d) {
                throw new IllegalArgumentException("目标手数必须大于 0");
            }
            return TradeComplexActionPlanner.planAddPosition(
                    accountId,
                    resolveTradeAccountMode(baselineCache),
                    resolveTradeSymbol(input.targetItem),
                    input.targetItem.getSide(),
                    input.targetVolume,
                    resolveTradeReferencePrice(input.targetItem, input.targetItem),
                    0d,
                    0d
            );
        }
        if (COMPLEX_ACTION_REVERSE.equals(input.complexAction)) {
            if (input.targetVolume <= 0d) {
                throw new IllegalArgumentException("目标手数必须大于 0");
            }
            return TradeComplexActionPlanner.planReverse(
                    accountId,
                    resolveTradeAccountMode(baselineCache),
                    input.targetItem,
                    input.targetVolume,
                    resolveTradeReferencePrice(input.targetItem, input.targetItem)
            );
        }
        if (COMPLEX_ACTION_CLOSE_BY.equals(input.complexAction)) {
            PositionItem oppositePosition = resolveCloseByOppositePosition(baselineCache, input.targetItem);
            return TradeComplexActionPlanner.planCloseBy(accountId, input.targetItem, oppositePosition);
        }
        throw new IllegalArgumentException("当前复杂动作暂不支持");
    }

    // 直接用已有命令进入执行链，避免页面层复制提交流程。
    private void requestTradeExecution(@Nullable TradeCommand command) {
        requestTradeExecution(command, resolveTradeBaselineCache());
    }

    private void requestTradeExecution(@Nullable TradeCommand command,
                                       @Nullable AccountStatsPreloadManager.Cache baselineCache) {
        if (command == null) {
            return;
        }
        if (tradeFlowRunning) {
            showTradeMessage("上一笔交易仍在处理中");
            return;
        }
        if (tradeExecutionCoordinator == null || ioExecutor == null) {
            showTradeMessage("交易链路未初始化");
            return;
        }
        tradeFlowRunning = true;
        tradePrepareTask = ioExecutor.submit(() -> {
            try {
                if (resolveTradeAccountId(baselineCache).isEmpty()) {
                    postTradeError("当前账户未连接，暂时不能交易");
                    return;
                }
                TradeExecutionCoordinator.PreparedTrade prepared =
                        tradeExecutionCoordinator.prepareExecution(command);
                mainHandler.post(() -> handlePreparedTrade(command, prepared, baselineCache));
            } catch (IllegalArgumentException exception) {
                postTradeError(exception.getMessage());
            } catch (Exception exception) {
                postTradeError("交易准备失败：" + safeTradeMessage(exception.getMessage()));
            } finally {
                tradePrepareTask = null;
            }
        });
    }

    // 执行批量交易计划，并在完成后展示总览和单项结果。
    private void requestBatchTradeExecution(@Nullable BatchTradePlan plan) {
        if (plan == null) {
            return;
        }
        if (tradeFlowRunning) {
            showTradeMessage("上一笔交易仍在处理中");
            return;
        }
        if (batchTradeCoordinator == null || ioExecutor == null) {
            showTradeMessage("批量交易链路未初始化");
            return;
        }
        tradeFlowRunning = true;
        tradePrepareTask = ioExecutor.submit(() -> {
            try {
                BatchTradeCoordinator.ExecutionResult result = batchTradeCoordinator.submit(plan);
                mainHandler.post(() -> handleBatchTradeExecutionResult(result));
            } catch (IllegalArgumentException exception) {
                postTradeError(exception.getMessage());
            } catch (Exception exception) {
                postTradeError("批量交易失败：" + safeTradeMessage(exception.getMessage()));
            } finally {
                tradePrepareTask = null;
            }
        });
    }

    // 处理检查后的待确认态。
    private void handlePreparedTrade(@NonNull TradeCommand command,
                                     @Nullable TradeExecutionCoordinator.PreparedTrade preparedTrade,
                                     @Nullable AccountStatsPreloadManager.Cache baselineCache) {
        if (!canPresentTradeUi()) {
            tradeFlowRunning = false;
            return;
        }
        if (preparedTrade == null) {
            tradeFlowRunning = false;
            showTradeOutcomeDialog("结果未确认", "交易准备失败，请稍后重试");
            return;
        }
        if (preparedTrade.getUiState() != TradeExecutionCoordinator.UiState.AWAITING_CONFIRMATION) {
            tradeFlowRunning = false;
            showTradeOutcomeDialog(
                    resolveTradeOutcomeTitle(preparedTrade.getUiState()),
                    preparedTrade.getMessage(),
                    preparedTrade.getStateMachine() == null ? "" : preparedTrade.getStateMachine().getCommand().getRequestId()
            );
            return;
        }
        String message = TradeCommandFactory.describe(command) + "\n\n" + preparedTrade.getMessage();
        AlertDialog confirmDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("确认交易")
                .setMessage(message)
                .setNegativeButton("取消", (dialogInterface, which) -> tradeFlowRunning = false)
                .setOnCancelListener(dialogInterface -> tradeFlowRunning = false)
                .setPositiveButton("确认", (dialogInterface, which) ->
                        submitTradeAfterConfirmation(preparedTrade.markConfirmed(), baselineCache))
                .create();
        confirmDialog.show();
        applyDialogSurface(confirmDialog);
    }

    // 提交确认后的交易，并把结果回写到页面提示。
    private void submitTradeAfterConfirmation(@NonNull TradeExecutionCoordinator.PreparedTrade preparedTrade,
                                              @Nullable AccountStatsPreloadManager.Cache baselineCache) {
        if (ioExecutor == null || tradeExecutionCoordinator == null) {
            tradeFlowRunning = false;
            showTradeMessage("交易链路未初始化");
            return;
        }
        tradeSubmitTask = ioExecutor.submit(() -> {
            try {
                TradeExecutionCoordinator.ExecutionResult executionResult =
                        tradeExecutionCoordinator.submitAfterConfirmation(preparedTrade, baselineCache);
                mainHandler.post(() -> handleTradeExecutionResult(executionResult));
            } finally {
                tradeSubmitTask = null;
            }
        });
    }

    // 根据最终结果给用户明确反馈。
    private void handleTradeExecutionResult(@Nullable TradeExecutionCoordinator.ExecutionResult executionResult) {
        if (!canPresentTradeUi()) {
            tradeFlowRunning = false;
            return;
        }
        tradeFlowRunning = false;
        if (executionResult == null) {
            showTradeOutcomeDialog("结果未确认", "交易结果缺失，请等待后续刷新");
            return;
        }
        if (executionResult.getLatestCache() != null) {
            host.refreshChartOverlays();
        }
        if (executionResult.getUiState() == TradeExecutionCoordinator.UiState.SETTLED) {
            showTradeMessage(executionResult.getMessage());
            return;
        }
        showTradeOutcomeDialog(
                resolveTradeOutcomeTitle(executionResult),
                safeTradeMessage(executionResult.getMessage()),
                resolveTradeTraceId(executionResult)
        );
    }

    // 根据批量结果给用户展示总览和单项清单。
    private void handleBatchTradeExecutionResult(@Nullable BatchTradeCoordinator.ExecutionResult executionResult) {
        if (!canPresentTradeUi()) {
            tradeFlowRunning = false;
            return;
        }
        tradeFlowRunning = false;
        if (executionResult == null || executionResult.getReceipt() == null) {
            showTradeOutcomeDialog("结果未确认", "批量结果缺失，请等待后续刷新");
            return;
        }
        if (executionResult.getLatestCache() != null) {
            host.refreshChartOverlays();
        }
        showTradeOutcomeDialog(
                resolveBatchTradeOutcomeTitle(executionResult),
                buildBatchTradeOutcomeMessage(executionResult.getReceipt()),
                executionResult.getReceipt().getBatchId()
        );
    }

    // 把表单输入转换成第一阶段交易命令。
    private TradeCommand buildTradeCommand(@NonNull String accountId, @NonNull TradeDialogInput input) {
        TradeTemplate template = resolveDefaultTradeTemplate(input.action);
        switch (input.action) {
            case OPEN_BUY:
                return applyTemplate(TradeCommandFactory.openMarket(accountId, input.symbol, "buy",
                        input.volume, input.price, input.sl, input.tp), template);
            case OPEN_SELL:
                return applyTemplate(TradeCommandFactory.openMarket(accountId, input.symbol, "sell",
                        input.volume, input.price, input.sl, input.tp), template);
            case PENDING_ADD:
                return applyTemplate(TradeCommandFactory.pendingAdd(accountId, input.symbol, input.orderType,
                        input.volume, input.price, input.sl, input.tp), template);
            case PENDING_MODIFY:
                return TradeCommandFactory.pendingModify(accountId, input.symbol,
                        input.orderTicket, input.price, input.sl, input.tp);
            case CLOSE_POSITION:
                return TradeCommandFactory.closePosition(accountId, input.symbol,
                        input.positionTicket, input.volume, input.price);
            case MODIFY_TPSL:
                return TradeCommandFactory.modifyTpSl(accountId, input.symbol,
                        input.positionTicket, input.price, input.sl, input.tp);
            case PENDING_CANCEL:
                return TradeCommandFactory.pendingCancel(accountId, input.symbol, input.orderTicket);
            default:
                throw new IllegalArgumentException("当前动作不在第一阶段范围");
        }
    }

    @Nullable
    private TradeTemplate resolveDefaultTradeTemplate() {
        return tradeTemplateRepository == null ? null : tradeTemplateRepository.getDefaultTemplate();
    }

    @Nullable
    private TradeTemplate resolveDefaultTradeTemplate(@NonNull ChartTradeAction action) {
        TradeTemplate defaultTemplate = resolveDefaultTradeTemplate();
        if (defaultTemplate == null) {
            return null;
        }
        if (action != ChartTradeAction.OPEN_BUY
                && action != ChartTradeAction.OPEN_SELL
                && action != ChartTradeAction.PENDING_ADD) {
            return null;
        }
        String requiredScope = action == ChartTradeAction.PENDING_ADD ? "pending" : "market";
        if (supportsEntryScope(defaultTemplate, requiredScope)) {
            return defaultTemplate;
        }
        for (TradeTemplate template : tradeTemplateRepository.getTemplates()) {
            if (supportsEntryScope(template, requiredScope)) {
                return template;
            }
        }
        return defaultTemplate;
    }

    private boolean supportsEntryScope(@Nullable TradeTemplate template, @NonNull String requiredScope) {
        if (template == null) {
            return false;
        }
        String scope = template.getEntryScope().trim().toLowerCase(Locale.ROOT);
        return scope.isEmpty() || "both".equals(scope) || requiredScope.equals(scope);
    }

    @NonNull
    private TradeCommand applyTemplate(@NonNull TradeCommand command, @Nullable TradeTemplate template) {
        if (tradeTemplateRepository == null) {
            return TradeCommandFactory.withTemplate(command, template);
        }
        return tradeTemplateRepository.applyTemplate(command, template);
    }

    // 优先使用最新缓存，缺失时再触发一次前台抓取。
    @Nullable
    private AccountStatsPreloadManager.Cache resolveTradeBaselineCache() {
        if (accountStatsPreloadManager == null) {
            return null;
        }
        AccountStatsPreloadManager.Cache latestCache = accountStatsPreloadManager.getLatestCache();
        if (isTradeBaselineCacheReady(latestCache)) {
            return latestCache;
        }
        AccountStatsPreloadManager.Cache fetchedCache = accountStatsPreloadManager.fetchFullForUi(AccountTimeRange.ALL);
        return isTradeBaselineCacheReady(fetchedCache) ? fetchedCache : null;
    }

    // 从账户缓存里读取当前账号，作为交易命令 accountId。
    @NonNull
    private String resolveTradeAccountId(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (!isTradeBaselineCacheReady(cache) || cache == null || cache.getAccount() == null) {
            return "";
        }
        return cache.getAccount().trim();
    }

    private boolean isTradeBaselineCacheReady(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null || cache.getSnapshot() == null || !cache.isConnected()) {
            return false;
        }
        if (!ConfigManager.getInstance(activity.getApplicationContext()).isAccountSessionActive()) {
            return false;
        }
        SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();
        if (sessionSummary == null || sessionSummary.getActiveAccount() == null) {
            return false;
        }
        String activeAccount = sessionSummary.getActiveAccount().getLogin();
        String activeServer = sessionSummary.getActiveAccount().getServer();
        return activeAccount != null
                && activeServer != null
                && activeAccount.trim().equalsIgnoreCase(cache.getAccount().trim())
                && activeServer.trim().equalsIgnoreCase(cache.getServer().trim());
    }

    // 解析当前目标品种，优先使用条目里的 MT5 品种。
    @NonNull
    private String resolveTradeSymbol(@Nullable PositionItem item) {
        if (item != null && item.getCode() != null && !item.getCode().trim().isEmpty()) {
            return MarketChartTradeSupport.toTradeSymbol(item.getCode());
        }
        return MarketChartTradeSupport.toTradeSymbol(symbolProvider.getSelectedSymbol());
    }

    // 统一解析参考价，优先最新 K 线，回退持仓和挂单价。
    private double resolveTradeReferencePrice(@Nullable PositionItem positionItem,
                                             @Nullable PositionItem pendingOrderItem) {
        return MarketChartTradeSupport.resolveReferencePrice(
                candleProvider.getLoadedCandles(),
                positionItem,
                pendingOrderItem
        );
    }

    // 解析必须为正数的输入。
    private double requirePositiveTradeValue(@Nullable EditText input, @NonNull String fieldLabel) {
        double value = parseOptionalTradeValue(input);
        if (value <= 0d) {
            throw new IllegalArgumentException(fieldLabel + "必须大于 0");
        }
        return value;
    }

    // 解析可选价格输入。
    private double parseOptionalTradeValue(@Nullable EditText input) {
        String text = input == null || input.getText() == null ? "" : input.getText().toString();
        return MarketChartTradeSupport.parseOptionalDouble(text, 0d);
    }

    // 根据复杂动作切换目标手数字段显隐。
    private void updateTradeComplexActionFields(@NonNull DialogTradeCommandBinding dialogBinding) {
        updateTradeComplexActionFields(dialogBinding, null);
    }

    // 根据复杂动作切换目标手数字段与提示文案。
    private void updateTradeComplexActionFields(@NonNull DialogTradeCommandBinding dialogBinding,
                                                @Nullable PositionItem targetItem) {
        String complexAction = resolveSelectedTradeComplexAction(dialogBinding);
        boolean showTargetVolume = shouldShowTargetVolumeInput(complexAction);
        dialogBinding.tvTradeTargetVolumeLabel.setVisibility(showTargetVolume ? View.VISIBLE : View.GONE);
        dialogBinding.etTradeTargetVolume.setVisibility(showTargetVolume ? View.VISIBLE : View.GONE);
        if (COMPLEX_ACTION_REVERSE.equals(complexAction)) {
            dialogBinding.tvTradeTargetVolumeLabel.setText("反手开仓手数");
        } else if (COMPLEX_ACTION_ADD_POSITION.equals(complexAction)) {
            dialogBinding.tvTradeTargetVolumeLabel.setText("加仓手数");
        } else {
            dialogBinding.tvTradeTargetVolumeLabel.setText("目标手数");
        }
        if (targetItem != null) {
            dialogBinding.tvTradeCommandHint.setText(resolveClosePositionComplexActionHint(targetItem, complexAction));
        }
    }

    // 读取当前复杂动作选择。
    @NonNull
    private String resolveSelectedTradeComplexAction(@NonNull DialogTradeCommandBinding dialogBinding) {
        Object selected = dialogBinding.spinnerTradeComplexAction.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    // 只有部分平仓和反手需要显式输入目标手数。
    private boolean shouldShowTargetVolumeInput(@Nullable String complexAction) {
        return COMPLEX_ACTION_PARTIAL_CLOSE.equals(complexAction)
                || COMPLEX_ACTION_ADD_POSITION.equals(complexAction)
                || COMPLEX_ACTION_REVERSE.equals(complexAction);
    }

    // 解析当前账户模式，批量计划必须显式带出 netting/hedging 真值。
    @NonNull
    private String resolveTradeAccountMode(@Nullable AccountStatsPreloadManager.Cache baselineCache) {
        if (baselineCache == null || baselineCache.getAccountMode() == null) {
            return "";
        }
        return baselineCache.getAccountMode().trim();
    }

    // 解析 Close By 对应的唯一反向持仓；多笔或缺失都直接报明确边界。
    @NonNull
    private PositionItem resolveCloseByOppositePosition(@Nullable AccountStatsPreloadManager.Cache baselineCache,
                                                        @NonNull PositionItem targetItem) {
        AccountSnapshot snapshot = baselineCache == null ? null : baselineCache.getSnapshot();
        if (snapshot == null || snapshot.getPositions() == null || snapshot.getPositions().isEmpty()) {
            throw new IllegalArgumentException("当前没有可用于 Close By 的反向持仓");
        }
        List<PositionItem> matches = new ArrayList<>();
        String targetSymbol = normalizeTradeSymbol(resolveTradeSymbol(targetItem));
        String targetSide = normalizeTradeSide(targetItem.getSide());
        for (PositionItem position : snapshot.getPositions()) {
            if (position == null || position.getPositionTicket() == targetItem.getPositionTicket()) {
                continue;
            }
            if (!targetSymbol.equals(normalizeTradeSymbol(resolveTradeSymbol(position)))) {
                continue;
            }
            if (targetSide.equals(normalizeTradeSide(position.getSide()))) {
                continue;
            }
            matches.add(position);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("当前没有可用于 Close By 的反向持仓");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("当前存在多笔反向持仓，暂不支持直接 Close By");
        }
        return matches.get(0);
    }

    // 统一规整交易方向。
    @NonNull
    private String normalizeTradeSide(@Nullable String side) {
        String normalized = side == null ? "" : side.trim().toLowerCase(Locale.ROOT);
        if ("long".equals(normalized)) {
            return "buy";
        }
        if ("short".equals(normalized)) {
            return "sell";
        }
        return normalized;
    }

    // 统一规整交易品种。
    @NonNull
    private String normalizeTradeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    // 把后台线程错误安全回传到主线程。
    private void postTradeError(@Nullable String message) {
        mainHandler.post(() -> {
            if (!canPresentTradeUi()) {
                tradeFlowRunning = false;
                return;
            }
            tradeFlowRunning = false;
            showTradeOutcomeDialog("交易未发出", safeTradeMessage(message));
        });
    }

    // 弹出明确结果提示。
    private void showTradeOutcomeDialog(@NonNull String title, @Nullable String message) {
        showTradeOutcomeDialog(title, message, "");
    }

    // 弹出明确结果提示，并在可追踪时提供“查看追踪”入口。
    private void showTradeOutcomeDialog(@NonNull String title,
                                        @Nullable String message,
                                        @Nullable String traceId) {
        if (!canPresentTradeUi()) {
            tradeFlowRunning = false;
            return;
        }
        String safeTraceId = traceId == null ? "" : traceId.trim();
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(safeTradeMessage(message))
                .setPositiveButton("知道了", null)
                .setNeutralButton(safeTraceId.isEmpty() ? null : "查看追踪", safeTraceId.isEmpty() ? null : (dialogInterface, which) ->
                        TradeAuditActivity.open(activity, safeTraceId))
                .create();
        dialog.show();
        applyDialogSurface(dialog);
    }

    // 轻量提示已成功收敛的交易结果。
    private void showTradeMessage(@Nullable String message) {
        if (!canPresentTradeUi()) {
            tradeFlowRunning = false;
            return;
        }
        Toast.makeText(activity, safeTradeMessage(message), Toast.LENGTH_SHORT).show();
    }

    // 判断当前页面是否还能安全承接交易弹窗或提示。
    private boolean canPresentTradeUi() {
        return !activity.isFinishing() && !activity.isDestroyed() && binding != null;
    }

    // 统一把交易相关弹窗收口到当前主题面板。
    private void applyDialogSurface(@Nullable AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(palette.textPrimary);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(palette.textPrimary);
        }
        int titleId = activity.getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId != 0) {
            View titleView = dialog.findViewById(titleId);
            if (titleView instanceof TextView) {
                ((TextView) titleView).setTextColor(palette.textPrimary);
            }
        }
    }

    // 统一交易动作菜单样式，确保深色弹窗里的列表项文字保持高对比可读。
    @NonNull
    private ArrayAdapter<String> createTradeActionMenuAdapter(@NonNull String[] actions) {
        return new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, android.R.id.text1, actions) {
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleTradeActionMenuItem(view);
                return view;
            }
        };
    }

    // 统一交易动作菜单列表项文字样式，不再手写旧 spinner 文案皮肤。
    private void styleTradeActionMenuItem(@Nullable View view) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        UiPaletteManager.styleSelectFieldLabel(
                textView,
                UiPaletteManager.resolve(activity),
                R.style.TextAppearance_BinanceMonitor_ControlCompact
        );
    }

    // 交易弹窗挂单类型使用统一下拉样式。
    @NonNull
    private ArrayAdapter<String> createTradeOrderTypeAdapter(@NonNull String[] orderTypes) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                activity,
                R.layout.item_spinner_filter,
                android.R.id.text1,
                orderTypes
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleTradeOrderTypeSelectFieldItem(view);
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleTradeOrderTypeSelectFieldItem(view);
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    // 统一挂单类型选择字段文字样式，避免弹窗里再分出独立 spinner 主体。
    private void styleTradeOrderTypeSelectFieldItem(@Nullable View view) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        UiPaletteManager.styleSelectFieldLabel(
                textView,
                UiPaletteManager.resolve(activity),
                R.style.TextAppearance_BinanceMonitor_ControlCompact
        );
    }

    // 统一生成弹窗标题。
    @NonNull
    private String resolveTradeDialogTitle(@NonNull ChartTradeAction action) {
        switch (action) {
            case OPEN_BUY:
                return "市价买入";
            case OPEN_SELL:
                return "市价卖出";
            case PENDING_ADD:
                return "新增挂单";
            case PENDING_MODIFY:
                return "修改挂单";
            case CLOSE_POSITION:
                return "持仓操作";
            case MODIFY_TPSL:
                return "修改止盈止损";
            case PENDING_CANCEL:
                return "删除挂单";
            default:
                return "交易操作";
        }
    }

    // 统一生成表单提示文案。
    @NonNull
    private String resolveTradeDialogHint(@NonNull ChartTradeAction action,
                                          @Nullable PositionItem targetItem,
                                          double referencePrice) {
        if (action == ChartTradeAction.CLOSE_POSITION) {
            return resolveClosePositionComplexActionHint(targetItem, COMPLEX_ACTION_FULL_CLOSE);
        }
        if (action == ChartTradeAction.PENDING_CANCEL) {
            return "当前品种 " + resolveTradeSymbol(targetItem) + "，确认删除这笔挂单。";
        }
        if (action == ChartTradeAction.PENDING_MODIFY) {
            if (referencePrice > 0d) {
                return "参考价 $" + FormatUtils.formatPrice(referencePrice) + "，可修改挂单价格、止损或止盈。";
            }
            return "请填写新的挂单价格、止损或止盈，提交后将由服务器按 MT5 规则校验。";
        }
        if (action == ChartTradeAction.MODIFY_TPSL) {
            if (referencePrice > 0d) {
                return "参考价 $" + FormatUtils.formatPrice(referencePrice) + "，至少填写止损或止盈。";
            }
            return "至少填写止损或止盈，提交后将由服务器按 MT5 规则校验。";
        }
        if (action == ChartTradeAction.PENDING_ADD) {
            return "当前品种 " + resolveTradeSymbol(targetItem) + "，请填写挂单手数和触发价格。";
        }
        return "当前品种 " + resolveTradeSymbol(targetItem) + "，将按服务器实时价执行，可选填写止损止盈。";
    }

    // 按复杂动作生成持仓处理提示，避免复杂动作仍显示固定平仓文案。
    @NonNull
    private String resolveClosePositionComplexActionHint(@Nullable PositionItem targetItem,
                                                         @Nullable String complexAction) {
        String symbol = resolveTradeSymbol(targetItem);
        if (COMPLEX_ACTION_PARTIAL_CLOSE.equals(complexAction)) {
            return "当前品种 " + symbol + "，请输入本次要平掉的手数。";
        }
        if (COMPLEX_ACTION_ADD_POSITION.equals(complexAction)) {
            return "当前品种 " + symbol + "，将沿当前方向按目标手数继续加仓。";
        }
        if (COMPLEX_ACTION_REVERSE.equals(complexAction)) {
            return "当前品种 " + symbol + "，将先平当前持仓，再按目标手数开反向仓位。";
        }
        if (COMPLEX_ACTION_CLOSE_BY.equals(complexAction)) {
            return "当前品种 " + symbol + "，将按成对持仓执行对锁平仓。";
        }
        return "当前品种 " + symbol + "，将按服务器实时价执行平仓。";
    }

    // 把结果状态翻译成页面标题。
    @NonNull
    private String resolveTradeOutcomeTitle(@Nullable TradeExecutionCoordinator.ExecutionResult executionResult) {
        TradeExecutionCoordinator.UiState uiState = executionResult == null ? null : executionResult.getUiState();
        if (uiState == TradeExecutionCoordinator.UiState.REJECTED) {
            return "交易被拒绝";
        }
        if (uiState == TradeExecutionCoordinator.UiState.SETTLED) {
            return "交易已完成";
        }
        if (uiState == TradeExecutionCoordinator.UiState.ACCEPTED_AWAITING_SYNC) {
            return "交易已受理";
        }
        if (executionResult != null
                && executionResult.getStateMachine() != null
                && executionResult.getStateMachine().getStep() == TradeCommandStateMachine.Step.ACCEPTED) {
            return "交易已受理";
        }
        return "结果未确认";
    }

    @NonNull
    private String resolveTradeOutcomeTitle(@Nullable TradeExecutionCoordinator.UiState uiState) {
        if (uiState == TradeExecutionCoordinator.UiState.REJECTED) {
            return "交易被拒绝";
        }
        if (uiState == TradeExecutionCoordinator.UiState.SETTLED) {
            return "交易已完成";
        }
        if (uiState == TradeExecutionCoordinator.UiState.ACCEPTED_AWAITING_SYNC) {
            return "交易已受理";
        }
        return "结果未确认";
    }

    // 把批量执行状态翻译成页面标题。
    @NonNull
    private String resolveBatchTradeOutcomeTitle(@Nullable BatchTradeCoordinator.ExecutionResult executionResult) {
        BatchTradeCoordinator.UiState uiState = executionResult == null ? null : executionResult.getUiState();
        if (uiState == BatchTradeCoordinator.UiState.ACCEPTED) {
            return "批量交易已受理";
        }
        if (uiState == BatchTradeCoordinator.UiState.PARTIAL) {
            return "批量交易部分成功";
        }
        if (uiState == BatchTradeCoordinator.UiState.FAILED) {
            return "批量交易失败";
        }
        return "结果未确认";
    }

    // 生成批量结果展示文案：先总览，再列单项结果。
    @NonNull
    private String buildBatchTradeOutcomeMessage(@Nullable BatchTradeReceipt receipt) {
        String summary = BatchTradeResultFormatter.buildSummary(receipt);
        List<String> itemLines = BatchTradeResultFormatter.buildItemLines(receipt);
        if (itemLines.isEmpty()) {
            return summary;
        }
        StringBuilder builder = new StringBuilder(summary).append("\n\n");
        for (int index = 0; index < itemLines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(itemLines.get(index));
        }
        return builder.toString();
    }

    // 统一处理空消息，避免提示框出现空白。
    @NonNull
    private String safeTradeMessage(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            return "请稍后查看账户刷新结果";
        }
        return message.trim();
    }

    @NonNull
    private String resolveTradeTraceId(@Nullable TradeExecutionCoordinator.ExecutionResult executionResult) {
        if (executionResult == null || executionResult.getStateMachine() == null || executionResult.getStateMachine().getCommand() == null) {
            return "";
        }
        return executionResult.getStateMachine().getCommand().getRequestId();
    }
}
