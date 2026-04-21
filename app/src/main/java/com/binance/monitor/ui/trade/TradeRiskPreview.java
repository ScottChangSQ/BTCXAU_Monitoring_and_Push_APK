/*
 * 交易风险预演模型，负责承载确认阶段要展示的动作摘要和风险摘要文本。
 * 与 TradeRiskPreviewResolver、TradeConfirmDialogController 协同工作。
 */
package com.binance.monitor.ui.trade;

public class TradeRiskPreview {
    private final String actionSummary;
    private final String templateName;
    private final String marginText;
    private final String freeMarginAfterText;
    private final String stopLossAmountText;
    private final String takeProfitAmountText;

    // 保存一次交易在确认阶段要显示的风险摘要。
    public TradeRiskPreview(String actionSummary,
                            String templateName,
                            String marginText,
                            String freeMarginAfterText,
                            String stopLossAmountText,
                            String takeProfitAmountText) {
        this.actionSummary = actionSummary == null ? "" : actionSummary;
        this.templateName = templateName == null ? "" : templateName;
        this.marginText = marginText == null ? "" : marginText;
        this.freeMarginAfterText = freeMarginAfterText == null ? "" : freeMarginAfterText;
        this.stopLossAmountText = stopLossAmountText == null ? "" : stopLossAmountText;
        this.takeProfitAmountText = takeProfitAmountText == null ? "" : takeProfitAmountText;
    }

    // 返回动作摘要。
    public String getActionSummary() {
        return actionSummary;
    }

    // 返回当前模板名。
    public String getTemplateName() {
        return templateName;
    }

    // 返回保证金文本。
    public String getMarginText() {
        return marginText;
    }

    // 返回剩余可用保证金文本。
    public String getFreeMarginAfterText() {
        return freeMarginAfterText;
    }

    // 返回止损金额文本。
    public String getStopLossAmountText() {
        return stopLossAmountText;
    }

    // 返回止盈金额文本。
    public String getTakeProfitAmountText() {
        return takeProfitAmountText;
    }

    // 判断当前是否至少有一项风险指标可展示。
    public boolean hasAnyRiskMetric() {
        return !marginText.isEmpty()
                || !freeMarginAfterText.isEmpty()
                || !stopLossAmountText.isEmpty()
                || !takeProfitAmountText.isEmpty();
    }
}
