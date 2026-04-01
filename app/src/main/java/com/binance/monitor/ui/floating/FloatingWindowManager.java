/*
 * 悬浮窗管理器，负责渲染统一快照下的连接状态、合并盈亏和产品简表。
 * 数据由 MonitorService 一次性喂入，布局支持整块拖动、最小化、点击还原和异常闪烁提示。
 */
package com.binance.monitor.ui.floating;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.ArrayList;
import java.util.List;
public class FloatingWindowManager {
    private static final long DRAG_FRAME_INTERVAL_MS = 12L;

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
    private DragAndClickListener dragAndClickListener;

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
        ViewGroup.LayoutParams expandedParams = binding.layoutExpanded.getLayoutParams();
        expandedParams.width = dp(FloatingWindowLayoutHelper.resolveExpandedWidthDp());
        binding.layoutExpanded.setLayoutParams(expandedParams);
        int horizontalPadding = dp(FloatingWindowLayoutHelper.resolveHorizontalPaddingDp());
        binding.layoutExpanded.setPadding(
                horizontalPadding,
                binding.layoutExpanded.getPaddingTop(),
                horizontalPadding,
                binding.layoutExpanded.getPaddingBottom()
        );
        applyWindowAlpha();
        binding.btnMinimize.setOnClickListener(v -> setMinimized(true));
        dragAndClickListener = new DragAndClickListener();
        bindDragSurface(binding.rootFloating);
        bindDragSurface(binding.layoutExpanded);
        bindDragSurface(binding.tvOverlayStatus);
        bindDragSurface(binding.tvOverlayConnection);
        bindDragSurface(binding.tvOverlayEmpty);
        bindDragSurface(binding.layoutSymbolCards);
        bindDragSurface(binding.btnMinimize);
        bindDragSurface(binding.viewMiniSquare);
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
        binding.layoutExpanded.setBackground(UiPaletteManager.createFloatingBackground(context, palette));
        binding.tvOverlayConnection.setText(snapshot.getConnectionStatus() == null || snapshot.getConnectionStatus().trim().isEmpty()
                ? context.getString(R.string.status_unknown)
                : snapshot.getConnectionStatus());
        binding.tvOverlayConnection.setTextColor(palette.textSecondary);
        List<FloatingSymbolCardData> visibleCards = collectVisibleCards();
        renderSummaryHeader(palette, visibleCards);
        applyWindowAlpha();
        int renderedCount = renderSymbolCards(visibleCards, palette);
        boolean hasItems = renderedCount > 0;
        binding.tvOverlayEmpty.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        binding.tvOverlayEmpty.setTextColor(palette.textSecondary);
        refreshMinimizedState(false);
    }

    private List<FloatingSymbolCardData> collectVisibleCards() {
        List<FloatingSymbolCardData> visible = new ArrayList<>();
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
            visible.add(card);
        }
        return visible;
    }

    private void renderSummaryHeader(UiPaletteManager.Palette palette, List<FloatingSymbolCardData> visibleCards) {
        boolean masked = SensitiveDisplayMasker.isEnabled(context);
        double totalPnl = 0d;
        for (FloatingSymbolCardData card : visibleCards) {
            if (card != null) {
                totalPnl += card.getTotalPnl();
            }
        }
        boolean hasCard = !visibleCards.isEmpty();
        String text = masked
                ? SensitiveDisplayMasker.MASK_TEXT
                : FormatUtils.formatSignedMoneyNoDecimal(hasCard ? totalPnl : 0d);
        binding.tvOverlayStatus.setText(text);
        int pnlColor = masked ? palette.textPrimary : resolvePnlColor(totalPnl, hasCard);
        binding.tvOverlayStatus.setTextColor(pnlColor);
        binding.tvOverlayStatus.setTextSize(hasCard ? 13f : 12f);
        binding.tvOverlayStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        binding.btnMinimize.setTextColor(palette.textPrimary);
        binding.btnMinimize.setBackground(UiPaletteManager.createOutlinedDrawable(context, palette.control, palette.stroke));
        binding.viewMiniSquare.setText(text);
        binding.viewMiniSquare.setTextColor(pnlColor);
        binding.viewMiniSquare.setTypeface(null, android.graphics.Typeface.BOLD);
        binding.viewMiniSquare.setBackground(UiPaletteManager.createOutlinedDrawable(
                context,
                applyAlpha(palette.card, 238),
                applyAlpha(pnlColor, 168)
        ));
    }

    private int renderSymbolCards(List<FloatingSymbolCardData> visibleCards, UiPaletteManager.Palette palette) {
        binding.layoutSymbolCards.removeAllViews();
        for (int i = 0; i < visibleCards.size(); i++) {
            FloatingSymbolCardData card = visibleCards.get(i);
            if (card == null) {
                continue;
            }
            binding.layoutSymbolCards.addView(buildSymbolCard(card, palette, i > 0));
        }
        return binding.layoutSymbolCards.getChildCount();
    }

    private View buildSymbolCard(FloatingSymbolCardData card,
                                 UiPaletteManager.Palette palette,
                                 boolean addTopSpacing) {
        boolean masked = SensitiveDisplayMasker.isEnabled(context);
        LinearLayout cardView = new LinearLayout(context);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setPadding(0, addTopSpacing ? dp(4) : 0, 0, 0);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardView.setLayoutParams(cardParams);

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                dp(FloatingWindowLayoutHelper.resolveValueRowWidthDp()),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView labelView = new TextView(context);
        labelView.setText(card.getLabel());
        labelView.setTextColor(palette.textPrimary);
        labelView.setTextSize(10f);
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        labelView.setSingleLine(true);
        labelView.setMaxLines(1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                dp(FloatingWindowLayoutHelper.resolveSymbolLabelColumnWidthDp()),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        labelView.setLayoutParams(labelParams);

        TextView pnlView = new TextView(context);
        LinearLayout.LayoutParams pnlParams = new LinearLayout.LayoutParams(
                dp(FloatingWindowLayoutHelper.resolvePnlColumnWidthDp()),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pnlView.setLayoutParams(pnlParams);
        double totalPnl = card.getTotalPnl();
        pnlView.setText(masked
                ? SensitiveDisplayMasker.MASK_TEXT
                : FormatUtils.formatSignedMoneyNoDecimal(totalPnl));
        pnlView.setTextColor(masked ? palette.textPrimary : (totalPnl >= 0d ? palette.rise : palette.fall));
        pnlView.setTextSize(10f);
        pnlView.setTypeface(null, android.graphics.Typeface.BOLD);
        pnlView.setGravity(Gravity.END);
        headerRow.addView(labelView);
        headerRow.addView(pnlView);
        cardView.addView(headerRow);

        TextView priceView = new TextView(context);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                dp(FloatingWindowLayoutHelper.resolveValueRowWidthDp()),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        priceParams.topMargin = dp(1);
        priceView.setLayoutParams(priceParams);
        priceView.setText(card.hasLatestPrice()
                ? SensitiveDisplayMasker.maskPrice(FormatUtils.formatPriceWithUnit(card.getLatestPrice()), masked)
                : "--");
        priceView.setTextColor(palette.textPrimary);
        priceView.setTextSize(13f);
        priceView.setTypeface(null, android.graphics.Typeface.BOLD);
        priceView.setGravity(Gravity.START);
        priceView.setSingleLine(true);
        priceView.setMaxLines(1);
        priceView.setEllipsize(TextUtils.TruncateAt.END);
        cardView.addView(priceView);

        TextView volumeView = new TextView(context);
        LinearLayout.LayoutParams volumeParams = new LinearLayout.LayoutParams(
                dp(FloatingWindowLayoutHelper.resolveValueRowWidthDp()),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        volumeParams.topMargin = dp(1);
        volumeView.setLayoutParams(volumeParams);
        volumeView.setText("成交量 " + SensitiveDisplayMasker.maskQuantity(
                FormatUtils.formatVolume(card.getVolume()),
                masked));
        volumeView.setTextColor(palette.textSecondary);
        volumeView.setTextSize(8.5f);
        volumeView.setGravity(Gravity.START);
        cardView.addView(volumeView);

        TextView amountView = new TextView(context);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                dp(FloatingWindowLayoutHelper.resolveValueRowWidthDp()),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        amountParams.topMargin = dp(1);
        amountView.setLayoutParams(amountParams);
        amountView.setText("成交额 " + SensitiveDisplayMasker.maskAmount(
                FormatUtils.formatAmount(card.getAmount()),
                masked));
        amountView.setTextColor(palette.textSecondary);
        amountView.setTextSize(8.5f);
        amountView.setGravity(Gravity.START);
        cardView.addView(amountView);

        bindDragSurface(cardView);
        cardView.setOnClickListener(v -> openChartForSymbol(card.getCode()));
        return cardView;
    }

    private void refreshMinimizedState(boolean forceBlink) {
        if (binding == null) {
            return;
        }
        if (forceBlink) {
            View root = binding.getRoot();
            root.setVisibility(View.INVISIBLE);
            applyWindowModeVisibility();
            root.post(() -> {
                if (binding == null) {
                    return;
                }
                requestImmediateWindowRelayout();
                root.setVisibility(View.VISIBLE);
                applyMiniBlinkState(true);
            });
            return;
        }
        applyWindowModeVisibility();
        applyMiniBlinkState(false);
    }

    // 统一切换展开态和最小化态的可见性，避免两个状态同时参与测量。
    private void applyWindowModeVisibility() {
        if (binding == null) {
            return;
        }
        binding.layoutExpanded.setVisibility(minimized ? View.GONE : View.VISIBLE);
        binding.viewMiniSquare.setVisibility(minimized ? View.VISIBLE : View.GONE);
        if (minimized) {
            binding.viewMiniSquare.bringToFront();
        }
        requestImmediateWindowRelayout();
    }

    // 控制最小化方块闪烁状态，避免状态切换时遗留半透明效果。
    private void applyMiniBlinkState(boolean forceBlink) {
        if (binding == null) {
            return;
        }
        if (minimized && (forceBlink || System.currentTimeMillis() < miniBlinkEndAt)) {
            startMiniBlink();
        } else if (!minimized) {
            handler.removeCallbacks(miniBlinkRunnable);
            miniBlinkActive = false;
            miniBlinkDimmed = false;
            applyMiniSquareBackgroundAlpha(false);
        }
    }

    private void setMinimized(boolean minimized) {
        this.minimized = minimized;
        refreshMinimizedState(true);
    }

    // 将拖动与点击判定统一挂到所有可操作区域，避免产品区单独吃掉手势。
    private void bindDragSurface(View target) {
        if (target == null) {
            return;
        }
        target.setOnTouchListener(dragAndClickListener);
    }

    // 立即触发浮层重排，减少最小化时先出现在左侧再跳到右侧的闪动。
    private void requestImmediateWindowRelayout() {
        if (!showing || binding == null || layoutParams == null) {
            return;
        }
        View root = binding.getRoot();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        root.measure(widthSpec, heightSpec);
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        root.requestLayout();
        layoutParams.width = root.getMeasuredWidth();
        layoutParams.height = root.getMeasuredHeight();
        try {
            windowManager.updateViewLayout(root, layoutParams);
        } catch (Exception ignored) {
        }
    }

    private void applyWindowAlpha() {
        if (layoutParams == null) {
            return;
        }
        layoutParams.alpha = 1f;
        applyFloatingBackgroundAlpha();
        if (showing && binding != null) {
            try {
                windowManager.updateViewLayout(binding.getRoot(), layoutParams);
            } catch (Exception ignored) {
            }
        }
    }

    // 仅调整背景透明度，避免系统窗口级透明把文字一起变淡。
    private void applyFloatingBackgroundAlpha() {
        if (binding == null) {
            return;
        }
        int baseAlpha = resolveBackgroundAlpha();
        applyBackgroundAlpha(binding.layoutExpanded, baseAlpha);
        applyBackgroundAlpha(binding.btnMinimize, baseAlpha);
        applyMiniSquareBackgroundAlpha(miniBlinkDimmed);
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
                applyMiniSquareBackgroundAlpha(false);
                return;
            }
            miniBlinkDimmed = !miniBlinkDimmed;
            applyMiniSquareBackgroundAlpha(miniBlinkDimmed);
            handler.postDelayed(this, 300L);
        }
    };

    // 最小化闪烁时只闪背景，不改变字体透明度。
    private void applyMiniSquareBackgroundAlpha(boolean dimmed) {
        if (binding == null) {
            return;
        }
        int baseAlpha = resolveBackgroundAlpha();
        int targetAlpha = dimmed ? Math.max(54, Math.round(baseAlpha * 0.38f)) : baseAlpha;
        applyBackgroundAlpha(binding.viewMiniSquare, targetAlpha);
    }

    // 统一给背景 drawable 应用透明度，避免影响文本本身。
    private void applyBackgroundAlpha(View view, int alpha) {
        if (view == null) {
            return;
        }
        Drawable background = view.getBackground();
        if (background == null) {
            return;
        }
        background = background.mutate();
        background.setAlpha(Math.max(0, Math.min(255, alpha)));
        view.setBackground(background);
    }

    // 把用户设置的百分比换算成背景 alpha，保持最低 20% 可见度。
    private int resolveBackgroundAlpha() {
        float ratio = Math.max(0.2f, Math.min(1f, alphaPercent / 100f));
        return Math.round(255f * ratio);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int applyAlpha(int color, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (safeAlpha << 24);
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
