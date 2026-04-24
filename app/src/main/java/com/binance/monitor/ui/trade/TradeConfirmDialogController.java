/*
 * 交易确认控制器，负责给第一阶段交易流提供统一确认边界。
 * 当前阶段只固定“默认必须确认、默认不开一键交易”的最小规则，供交易执行协调器调用。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;

public class TradeConfirmDialogController {
    @NonNull
    private final TradeRiskGuard.ConfigProvider configProvider;

    public TradeConfirmDialogController() {
        this(TradeRiskGuard.Config::defaultConfig);
    }

    public TradeConfirmDialogController(@NonNull TradeRiskGuard.ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    // 生成当前交易命令的确认决策。
    public Decision buildDecision(TradeCommand command, @Nullable TradeCheckResult checkResult) {
        if (command == null) {
            return new Decision(false, true, false, "交易命令无效", null);
        }
        if (checkResult == null) {
            return new Decision(false, true, false, "检查结果缺失，请重新确认", null);
        }
        TradeRiskPreview riskPreview = TradeRiskPreviewResolver.resolve(command, checkResult);
        TradeRiskGuard.Config config = configProvider.getConfig();
        TradeRiskGuard.Decision riskDecision = TradeRiskGuard.evaluateTrade(command, config);
        String message = buildConfirmationMessage(riskPreview, riskDecision);
        boolean quickModeEnabled = config != null && config.isOneClickTradingEnabled();
        boolean allowOneClick = riskDecision.isAllowed()
                && TradeQuickModePolicy.shouldAllowQuickMode(command, quickModeEnabled, config);
        return new Decision(
                riskDecision.isAllowed(),
                !allowOneClick || riskDecision.isConfirmationRequired(),
                allowOneClick,
                message,
                riskPreview
        );
    }

    // 拼接统一确认文案，先给动作摘要，再给风险摘要。
    @NonNull
    private String buildConfirmationMessage(@Nullable TradeRiskPreview riskPreview,
                                            @NonNull TradeRiskGuard.Decision riskDecision) {
        if (!riskDecision.isAllowed()) {
            return riskDecision.getMessage();
        }
        if (riskPreview == null) {
            return "请确认本次交易后再提交";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(riskPreview.getActionSummary());
        builder.append("\n\n风险摘要");
        if (!riskPreview.getMarginText().isEmpty()) {
            builder.append("\n预计保证金：").append(riskPreview.getMarginText());
        }
        if (!riskPreview.getFreeMarginAfterText().isEmpty()) {
            builder.append("\n剩余可用保证金：").append(riskPreview.getFreeMarginAfterText());
        }
        if (!riskPreview.getStopLossAmountText().isEmpty()) {
            builder.append("\n止损金额：").append(riskPreview.getStopLossAmountText());
        }
        if (!riskPreview.getTakeProfitAmountText().isEmpty()) {
            builder.append("\n止盈金额：").append(riskPreview.getTakeProfitAmountText());
        }
        if (!riskDecision.getMessage().isEmpty()) {
            builder.append("\n\n风控提示\n").append(riskDecision.getMessage());
        }
        return builder.toString();
    }

    public static class Decision {
        private final boolean allowed;
        private final boolean confirmationRequired;
        private final boolean oneClickTradingEnabled;
        private final String message;
        private final TradeRiskPreview riskPreview;

        // 保存本次确认决策。
        public Decision(boolean allowed,
                        boolean confirmationRequired,
                        boolean oneClickTradingEnabled,
                        String message,
                        @Nullable TradeRiskPreview riskPreview) {
            this.allowed = allowed;
            this.confirmationRequired = confirmationRequired;
            this.oneClickTradingEnabled = oneClickTradingEnabled;
            this.message = message == null ? "" : message;
            this.riskPreview = riskPreview;
        }

        public boolean isAllowed() {
            return allowed;
        }

        // 返回是否必须确认。
        public boolean isConfirmationRequired() {
            return confirmationRequired;
        }

        // 返回是否开启一键交易。
        public boolean isOneClickTradingEnabled() {
            return oneClickTradingEnabled;
        }

        // 返回确认提示文案。
        public String getMessage() {
            return message;
        }

        // 返回风险预演结果。
        @Nullable
        public TradeRiskPreview getRiskPreview() {
            return riskPreview;
        }
    }
}
