/*
 * 交易确认控制器，负责给第一阶段交易流提供统一确认边界。
 * 当前阶段只固定“默认必须确认、默认不开一键交易”的最小规则，供交易执行协调器调用。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;

public class TradeConfirmDialogController {

    // 生成当前交易命令的确认决策。
    public Decision buildDecision(TradeCommand command, @Nullable TradeCheckResult checkResult) {
        String message = "请确认本次交易后再提交";
        if (command == null) {
            message = "交易命令无效";
        } else if (checkResult == null) {
            message = "检查结果缺失，请重新确认";
        }
        return new Decision(true, false, message);
    }

    public static class Decision {
        private final boolean confirmationRequired;
        private final boolean oneClickTradingEnabled;
        private final String message;

        // 保存本次确认决策。
        public Decision(boolean confirmationRequired,
                        boolean oneClickTradingEnabled,
                        String message) {
            this.confirmationRequired = confirmationRequired;
            this.oneClickTradingEnabled = oneClickTradingEnabled;
            this.message = message == null ? "" : message;
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
    }
}
