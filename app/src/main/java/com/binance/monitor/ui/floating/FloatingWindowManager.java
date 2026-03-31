/*
 * 悬浮窗管理器，负责渲染统一快照下的连接状态、合并盈亏、产品行情卡片与分产品盈亏。
 * 数据由 MonitorService 一次性喂入，布局支持整块长按拖动、最小化、点击还原和异常闪烁提示。
 */
package com.binance.monitor.ui.floating;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.databinding.LayoutFloatingWindowBinding;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.PermissionHelper;

import java.util.ArrayList;
import java.util.List;

public class FloatingWindowManager {
    private static final int MINI_SIZE_DP = 25;
    private static final long DRAG_LONG_PRESS_MS = 220L;
    private static final long DRAG_FRAME_INTERVAL_MS = 16L;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int dragSlopPx;

    private LayoutFloatingWindowBinding binding;
    private WindowManager.LayoutParams layoutParams;
    private boolean enabled;
    private int alphaPercent = 88;
    private boolean showing;
    private boolean minimized;
    private boolean draggingWindow;
    private boolean pendingRender;
    private FloatingWindowSnapshot snapshot = new FloatingWindowSnapshot("", 0L, new ArrayList<>());
    private boolean showBtc = true;
    private boolean showXau = true;
    private long miniBlinkEndAt;
    private boolean miniBlinkActive;
    private boolean miniBlinkDimmed;
    private long lastDragLayoutAt;
    private int lastDragLayoutX = Integer.MIN_VALUE;
    private int lastDragLayoutY = Integer.MIN_VALUE;

    public FloatingWindowManager(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        this.dragSlopPx = Math.max(6, ViewConfiguration.get(this.context).getScaledTouchSlop());
    }

    // 应用悬浮窗显示偏好。
    public void applyPreferences(boolean enabled, int alphaPercent, boolean showBtc, boolean showXau) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> applyPreferences(enabled, alphaPercent, showBtc, showXau));
            return;
        }
        this.enabled = enabled;
        this.alphaPercent = alphaPercent;
        this.showBtc = showBtc;
        this.showXau = showXau;
        if (!enabled) {
            hide();
            return;
        }
        showIfPossible();
        render();
    }

    // 刷新悬浮窗展示内容。
    public void update(FloatingWindowSnapshot snapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatingWindowSnapshot copy = snapshot == null
                    ? new FloatingWindowSnapshot("", 0L, new ArrayList<>())
                    : new FloatingWindowSnapshot(
                    snapshot.getConnectionStatus(),
                    snapshot.getUpdatedAt(),
                    snapshot.getCards());
            handler.post(() -> update(copy));
            return;
        }
        this.snapshot = snapshot == null
                ? new FloatingWindowSnapshot("", 0L, new ArrayList<>())
                : snapshot;
        if (draggingWindow) {
            pendingRender = true;
            return;
        }
        render();
    }

    // 异常发生时让最小化方块闪烁，方便后台感知。
    public void notifyAbnormalEvent(String symbol) {
        handler.post(() -> {
            miniBlinkEndAt = Math.max(miniBlinkEndAt, System.currentTimeMillis() + 10_000L);
            if (minimized) {
                startMiniBlink();
            }
        });
    }

    // 主动隐藏悬浮窗。
    public void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::hide);
            return;
        }
        if (!showing || binding == null) {
            return;
        }
        handler.removeCallbacks(miniBlinkRunnable);
        miniBlinkActive = false;
        try {
            windowManager.removeView(binding.getRoot());
        } catch (Exception ignored) {
        }
        binding = null;
        layoutParams = null;
        showing = false;
        minimized = false;
        draggingWindow = false;
        pendingRender = false;
    }

    private void showIfPossible() {
        if (!enabled || showing || !PermissionHelper.canDrawOverlays(context)) {
            return;
        }
        binding = LayoutFloatingWindowBinding.inflate(LayoutInflater.from(context));
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.END;
        layoutParams.x = dp(12);
        layoutParams.y = dp(120);
        applyWindowAlpha();
        binding.btnMinimize.setOnClickListener(v -> setMinimized(true));
        DragAndClickListener dragListener = new DragAndClickListener();
        binding.rootFloating.setOnTouchListener(dragListener);
        binding.viewMiniSquare.setOnTouchListener(dragListener);
        windowManager.addView(binding.getRoot(), layoutParams);
        showing = true;
    }

    private void render() {
        if (!enabled) {
            hide();
            return;
        }
        showIfPossible();
        if (!showing || binding == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(context);
        applyWindowAlpha();
        binding.layoutExpanded.setBackground(UiPaletteManager.createFloatingBackground(context, palette));
        binding.viewMiniSquare.setBackground(UiPaletteManager.createFilledDrawable(context, palette.primary));
        binding.tvOverlayConnection.setText(snapshot.getConnectionStatus() == null || snapshot.getConnectionStatus().trim().isEmpty()
                ? context.getString(R.string.status_unknown)
                : snapshot.getConnectionStatus());
        binding.tvOverlayConnection.setTextColor(palette.textSecondary);
        renderSummaryHeader(palette);
        renderSymbolCards(palette);
        boolean hasItems = !snapshot.getCards().isEmpty();
        binding.tvOverlayEmpty.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        binding.tvOverlayEmpty.setTextColor(palette.textSecondary);
        refreshMinimizedState(false);
    }

    private void renderSummaryHeader(UiPaletteManager.Palette palette) {
        double totalPnl = 0d;
        for (FloatingSymbolCardData card : snapshot.getCards()) {
            if (card != null) {
                totalPnl += card.getTotalPnl();
            }
        }
        boolean hasCard = !snapshot.getCards().isEmpty();
        String text = hasCard ? FormatUtils.formatSigned(totalPnl) : "0.00";
        binding.tvOverlayStatus.setText(text);
        binding.tvOverlayStatus.setTextColor(resolvePnlColor(totalPnl, hasCard));
        binding.tvOverlayStatus.setTextSize(hasCard ? 20f : 18f);
        binding.btnMinimize.setTextColor(palette.textPrimary);
    }

    private void renderSymbolCards(UiPaletteManager.Palette palette) {
        binding.layoutSymbolCards.removeAllViews();
        for (FloatingSymbolCardData card : snapshot.getCards()) {
            if (card == null) {
                continue;
            }
            if (!showBtc && AppConstants.SYMBOL_BTC.equalsIgnoreCase(card.getCode())) {
                continue;
            }
            if (!showXau && AppConstants.SYMBOL_XAU.equalsIgnoreCase(card.getCode())) {
                continue;
            }
            binding.layoutSymbolCards.addView(buildSymbolCard(card, palette));
        }
    }

    private View buildSymbolCard(FloatingSymbolCardData card, UiPaletteManager.Palette palette) {
        LinearLayout cardView = new LinearLayout(context);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setBackground(UiPaletteManager.createOutlinedDrawable(context, palette.card, palette.stroke));
        cardView.setPadding(dp(10), dp(9), dp(10), dp(9));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(8);
        cardView.setLayoutParams(cardParams);

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setText(card.getLabel());
        labelView.setTextColor(palette.textPrimary);
        labelView.setTextSize(11f);
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        labelView.setLayoutParams(labelParams);

        TextView pnlView = new TextView(context);
        LinearLayout.LayoutParams pnlParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pnlView.setLayoutParams(pnlParams);
        double totalPnl = card.getTotalPnl();
        pnlView.setText(FormatUtils.formatSigned(totalPnl));
        pnlView.setTextColor(totalPnl >= 0d ? palette.rise : palette.fall);
        pnlView.setTextSize(12f);
        pnlView.setTypeface(null, android.graphics.Typeface.BOLD);
        headerRow.addView(labelView);
        headerRow.addView(pnlView);
        cardView.addView(headerRow);

        TextView priceView = new TextView(context);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        priceParams.topMargin = dp(4);
        priceView.setLayoutParams(priceParams);
        priceView.setText(card.hasLatestPrice() ? FormatUtils.formatPriceWithUnit(card.getLatestPrice()) : "--");
        priceView.setTextColor(palette.textPrimary);
        priceView.setTextSize(18f);
        priceView.setTypeface(null, android.graphics.Typeface.BOLD);
        cardView.addView(priceView);

        TextView volumeView = new TextView(context);
        volumeView.setText("成交量 " + FormatUtils.formatVolume(card.getVolume()));
        volumeView.setTextColor(palette.textSecondary);
        volumeView.setTextSize(10f);
        LinearLayout.LayoutParams volumeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        volumeParams.topMargin = dp(4);
        volumeView.setLayoutParams(volumeParams);
        cardView.addView(volumeView);

        TextView amountView = new TextView(context);
        amountView.setText("成交额 " + FormatUtils.formatAmount(card.getAmount()));
        amountView.setTextColor(palette.textSecondary);
        amountView.setTextSize(10f);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        amountParams.topMargin = dp(2);
        amountView.setLayoutParams(amountParams);
        cardView.addView(amountView);

        cardView.setOnClickListener(v -> openChartForSymbol(card.getCode()));
        return cardView;
    }

    private void refreshMinimizedState(boolean forceBlink) {
        if (binding == null) {
            return;
        }
        binding.layoutExpanded.setVisibility(minimized ? View.GONE : View.VISIBLE);
        binding.viewMiniSquare.setVisibility(minimized ? View.VISIBLE : View.GONE);
        if (minimized && (forceBlink || System.currentTimeMillis() < miniBlinkEndAt)) {
            startMiniBlink();
        } else if (!minimized) {
            handler.removeCallbacks(miniBlinkRunnable);
            miniBlinkActive = false;
            binding.viewMiniSquare.setAlpha(1f);
        }
    }

    private void setMinimized(boolean minimized) {
        this.minimized = minimized;
        refreshMinimizedState(true);
    }

    private void applyWindowAlpha() {
        if (layoutParams == null) {
            return;
        }
        layoutParams.alpha = Math.max(0.2f, Math.min(1f, alphaPercent / 100f));
        if (showing && binding != null) {
            try {
                windowManager.updateViewLayout(binding.getRoot(), layoutParams);
            } catch (Exception ignored) {
            }
        }
    }

    private void openChartForSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return;
        }
        Intent intent = new Intent(context, MarketChartActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MarketChartActivity.EXTRA_TARGET_SYMBOL, symbol.trim());
        context.startActivity(intent);
    }

    private int resolvePnlColor(double totalPnl, boolean hasPosition) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(context);
        if (!hasPosition) {
            return palette.textPrimary;
        }
        return totalPnl >= 0d ? palette.rise : palette.fall;
    }

    private void startMiniBlink() {
        if (miniBlinkActive || binding == null) {
            return;
        }
        miniBlinkActive = true;
        handler.removeCallbacks(miniBlinkRunnable);
        handler.post(miniBlinkRunnable);
    }

    private final Runnable miniBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding == null || !minimized) {
                miniBlinkActive = false;
                return;
            }
            long now = System.currentTimeMillis();
            if (now >= miniBlinkEndAt) {
                miniBlinkActive = false;
                miniBlinkDimmed = false;
                binding.viewMiniSquare.setAlpha(1f);
                return;
            }
            miniBlinkDimmed = !miniBlinkDimmed;
            binding.viewMiniSquare.setAlpha(miniBlinkDimmed ? 0.35f : 1f);
            handler.postDelayed(this, 300L);
        }
    };

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private class DragAndClickListener implements View.OnTouchListener {
        private int downX;
        private int downY;
        private float rawX;
        private float rawY;
        private long downAt;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (binding == null || layoutParams == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = layoutParams.x;
                    downY = layoutParams.y;
                    rawX = event.getRawX();
                    rawY = event.getRawY();
                    downAt = SystemClock.elapsedRealtime();
                    draggingWindow = false;
                    lastDragLayoutAt = 0L;
                    lastDragLayoutX = layoutParams.x;
                    lastDragLayoutY = layoutParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float moveX = event.getRawX() - rawX;
                    float moveY = event.getRawY() - rawY;
                    if (!draggingWindow) {
                        if (SystemClock.elapsedRealtime() - downAt < DRAG_LONG_PRESS_MS) {
                            return true;
                        }
                        if (Math.hypot(moveX, moveY) < dragSlopPx) {
                            return true;
                        }
                    }
                    int deltaX = Math.round(rawX - event.getRawX());
                    int deltaY = Math.round(event.getRawY() - rawY);
                    int targetX = downX + deltaX;
                    int targetY = Math.max(0, downY + deltaY);
                    long now = SystemClock.elapsedRealtime();
                    boolean movedEnough = Math.abs(targetX - lastDragLayoutX) >= 2 || Math.abs(targetY - lastDragLayoutY) >= 2;
                    boolean frameReady = now - lastDragLayoutAt >= DRAG_FRAME_INTERVAL_MS;
                    if (movedEnough && frameReady) {
                        layoutParams.x = targetX;
                        layoutParams.y = targetY;
                        try {
                            windowManager.updateViewLayout(binding.getRoot(), layoutParams);
                            lastDragLayoutAt = now;
                            lastDragLayoutX = targetX;
                            lastDragLayoutY = targetY;
                        } catch (Exception ignored) {
                        }
                    }
                    draggingWindow = true;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (draggingWindow
                            && (layoutParams.x != downX + Math.round(rawX - event.getRawX())
                            || layoutParams.y != Math.max(0, downY + Math.round(event.getRawY() - rawY)))) {
                        layoutParams.x = downX + Math.round(rawX - event.getRawX());
                        layoutParams.y = Math.max(0, downY + Math.round(event.getRawY() - rawY));
                        try {
                            windowManager.updateViewLayout(binding.getRoot(), layoutParams);
                        } catch (Exception ignored) {
                        }
                    }
                    boolean wasDragging = draggingWindow;
                    draggingWindow = false;
                    if (!wasDragging && SystemClock.elapsedRealtime() - downAt < 250L) {
                        handleClick(event);
                    } else if (pendingRender) {
                        pendingRender = false;
                        render();
                    }
                    return true;
                default:
                    return false;
            }
        }

        private void handleClick(MotionEvent event) {
            if (binding == null) {
                return;
            }
            if (minimized) {
                setMinimized(false);
                return;
            }
            if (performClickIfTouched(binding.btnMinimize, event)) {
                setMinimized(true);
                return;
            }
            performChildRowClick(binding.layoutSymbolCards, event);
        }

        private boolean performChildRowClick(LinearLayout container, MotionEvent event) {
            if (container == null || container.getChildCount() == 0) {
                return false;
            }
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (performClickIfTouched(child, event)) {
                    return true;
                }
            }
            return false;
        }

        private boolean performClickIfTouched(View target, MotionEvent event) {
            if (target == null || target.getVisibility() != View.VISIBLE) {
                return false;
            }
            int[] location = new int[2];
            target.getLocationOnScreen(location);
            float rawTouchX = event.getRawX();
            float rawTouchY = event.getRawY();
            boolean touched = rawTouchX >= location[0]
                    && rawTouchX <= location[0] + target.getWidth()
                    && rawTouchY >= location[1]
                    && rawTouchY <= location[1] + target.getHeight();
            if (!touched) {
                return false;
            }
            target.performClick();
            return true;
        }
    }
}
