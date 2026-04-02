/*
 * 账户统计曲线区的子图边距辅助，负责统一计算合并布局时的上下留白。
 * 供净值主图、仓位比例、回撤和日收益附图共用，避免子图之间出现断层空白。
 */
package com.binance.monitor.ui.account;

final class CurvePaneSpacingHelper {

    private CurvePaneSpacingHelper() {
    }

    // 与上一张图合并时取消顶部留白，让边界直接贴合。
    static float resolveTopInsetPx(boolean mergeWithPrevious, float defaultTopInsetPx) {
        return mergeWithPrevious ? 0f : Math.max(0f, defaultTopInsetPx);
    }

    // 与下一张图合并时取消底部留白；若底部要显示时间刻度，则保留时间刻度区域。
    static float resolveBottomInsetPx(boolean mergeWithNext,
                                      boolean showBottomTimeLabels,
                                      float defaultBottomInsetPx,
                                      float bottomTimeInsetPx) {
        if (showBottomTimeLabels) {
            return Math.max(0f, bottomTimeInsetPx);
        }
        return mergeWithNext ? 0f : Math.max(0f, defaultBottomInsetPx);
    }

    // 合并到下一张图时，把底部刻度挪到框内，避免文字被裁掉。
    static float resolveBottomLabelBaseline(float bottom, boolean mergeWithNext, float offsetPx) {
        return mergeWithNext ? bottom - Math.max(0f, offsetPx) : bottom + Math.max(0f, offsetPx);
    }
}
