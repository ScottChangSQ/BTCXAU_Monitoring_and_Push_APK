package com.binance.monitor.ui.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.databinding.LayoutFloatingWindowBinding;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.AppLaunchHelper;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.PermissionHelper;

import java.util.Collections;
import java.util.Map;

public class FloatingWindowManager {
    private static final int MINI_SIZE_DP = 25;
    private static final int EXPANDED_PADDING_DP = 10;
    private static final int MINIMIZE_BTN_SIZE_DP = 18;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LayoutFloatingWindowBinding binding;
    private WindowManager.LayoutParams layoutParams;
    private boolean enabled;
    private int alphaPercent = 88;
    private boolean showBtc = true;
    private boolean showXau = true;
    private boolean showing;
    private boolean minimized;

    private Map<String, Double> prices = Collections.emptyMap();
    private Map<String, KlineData> klines = Collections.emptyMap();
    private boolean monitoringEnabled;
    private String connectionStatus = "";

    private long miniBlinkEndAt;
    private boolean miniBlinkActive;
    private boolean miniBlinkDimmed;
    private long btcBlinkEndAt;
    private long xauBlinkEndAt;
    private boolean priceBlinkActive;
    private boolean priceBlinkDimmed;
    private long lastDragUpdateAt;
    private float lastAppliedAlpha = -1f;
    private int lastAppliedWidth = Integer.MIN_VALUE;
    private int lastAppliedHeight = Integer.MIN_VALUE;
    private boolean draggingWindow;
    private boolean pendingRender;
    private int cachedExpandedWidth;
    private float cachedExpandedCenterX;
    private float cachedExpandedCenterY;

    public FloatingWindowManager(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

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

    public void update(Map<String, Double> prices,
                       Map<String, KlineData> klines,
                       boolean monitoringEnabled,
                       String connectionStatus) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Map<String, Double> priceCopy = prices == null ? Collections.emptyMap() : prices;
            Map<String, KlineData> klineCopy = klines == null ? Collections.emptyMap() : klines;
            String statusCopy = connectionStatus == null ? "" : connectionStatus;
            handler.post(() -> update(priceCopy, klineCopy, monitoringEnabled, statusCopy));
            return;
        }
        this.prices = prices;
        this.klines = klines;
        this.monitoringEnabled = monitoringEnabled;
        this.connectionStatus = connectionStatus == null ? "" : connectionStatus;
        if (draggingWindow) {
            pendingRender = true;
            return;
        }
        render();
    }

    public void notifyAbnormalEvent(String symbol) {
        handler.post(() -> {
            long endAt = System.currentTimeMillis() + 10_000L;
            miniBlinkEndAt = Math.max(miniBlinkEndAt, endAt);
            if (AppConstants.SYMBOL_BTC.equals(symbol)) {
                btcBlinkEndAt = endAt;
            } else if (AppConstants.SYMBOL_XAU.equals(symbol)) {
                xauBlinkEndAt = endAt;
            }
            startPriceBlinkIfNeeded();
            if (minimized) {
                startMiniBlink();
            }
        });
    }

    public void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::hide);
            return;
        }
        stopAllBlinking();
        if (showing && binding != null) {
            try {
                windowManager.removeView(binding.getRoot());
            } catch (Exception ignored) {
            }
        }
        showing = false;
        binding = null;
        layoutParams = null;
        minimized = false;
        lastAppliedAlpha = -1f;
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
        layoutParams.x = 24;
        layoutParams.y = 180;
        applyWindowAlpha();
        View.OnTouchListener dragListener = new DragAndClickListener();
        binding.layoutExpanded.setOnTouchListener(dragListener);
        binding.viewMiniSquare.setOnTouchListener(dragListener);
        binding.btnMinimize.setOnClickListener(v -> setMinimized(true));
        try {
            windowManager.addView(binding.getRoot(), layoutParams);
            showing = true;
            refreshMinimizedState(true);
        } catch (Exception ignored) {
            showing = false;
            binding = null;
            layoutParams = null;
        }
    }

    private void render() {
        if (!enabled) {
            hide();
            return;
        }
        if (draggingWindow) {
            pendingRender = true;
            return;
        }
        if (!showing) {
            showIfPossible();
        }
        if (!showing || binding == null) {
            return;
        }
        applyWindowAlpha();
        binding.tvOverlayStatus.setText(monitoringEnabled
                ? context.getString(R.string.overlay_running)
                : context.getString(R.string.overlay_stopped));
        binding.tvOverlayConnection.setText(connectionStatus);
        renderSymbol(binding.layoutBtc, binding.tvBtcPrice, binding.tvBtcVolume, binding.tvBtcAmount,
                AppConstants.SYMBOL_BTC, showBtc);
        renderSymbol(binding.layoutXau, binding.tvXauPrice, binding.tvXauVolume, binding.tvXauAmount,
                AppConstants.SYMBOL_XAU, showXau);
        if (!priceBlinkActive) {
            applyIdlePriceColors();
        }
        if (!minimized) {
            cacheExpandedAnchor();
        }
        refreshMinimizedState(false);
    }

    private void renderSymbol(View container,
                              TextView priceView,
                              TextView volumeView,
                              TextView amountView,
                              String symbol,
                              boolean visible) {
        container.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            return;
        }
        Double price = prices.get(symbol);
        KlineData kline = klines.get(symbol);
        String unit = AppConstants.symbolToAsset(symbol);
        priceView.setText(price == null ? "--" : FormatUtils.formatPriceNoDecimalWithUnit(price));
        if (kline == null) {
            volumeView.setText(context.getString(R.string.overlay_waiting));
            amountView.setText("--");
            return;
        }
        volumeView.setText(FormatUtils.formatVolumeWithUnit(kline.getVolume(), unit));
        amountView.setText(FormatUtils.formatAmount(kline.getQuoteAssetVolume()).replace("M$", " M$"));
    }

    private void refreshMinimizedState(boolean forceLayout) {
        if (binding == null || layoutParams == null) {
            return;
        }
        binding.layoutExpanded.setVisibility(minimized ? View.GONE : View.VISIBLE);
        binding.viewMiniSquare.setVisibility(minimized ? View.VISIBLE : View.GONE);
        int targetWidth = minimized ? dp(25) : WindowManager.LayoutParams.WRAP_CONTENT;
        int targetHeight = minimized ? dp(25) : WindowManager.LayoutParams.WRAP_CONTENT;
        boolean sizeChanged = forceLayout
                || layoutParams.width != targetWidth
                || layoutParams.height != targetHeight
                || lastAppliedWidth != targetWidth
                || lastAppliedHeight != targetHeight;
        if (sizeChanged) {
            layoutParams.width = targetWidth;
            layoutParams.height = targetHeight;
            if (showing && !draggingWindow) {
                try {
                    windowManager.updateViewLayout(binding.getRoot(), layoutParams);
                } catch (Exception ignored) {
                }
            }
            lastAppliedWidth = targetWidth;
            lastAppliedHeight = targetHeight;
        }
        if (!minimized) {
            miniBlinkActive = false;
            miniBlinkDimmed = false;
            binding.viewMiniSquare.setAlpha(1f);
            setMiniSquareBackground(false);
            startPriceBlinkIfNeeded();
            return;
        }
        if (System.currentTimeMillis() < miniBlinkEndAt) {
            startMiniBlink();
        } else {
            binding.viewMiniSquare.setAlpha(1f);
            setMiniSquareBackground(false);
        }
    }

    private void setMinimized(boolean minimized) {
        if (this.minimized == minimized) {
            if (this.minimized && System.currentTimeMillis() < miniBlinkEndAt) {
                startMiniBlink();
            }
            return;
        }
        remapPositionForToggle(minimized);
        this.minimized = minimized;
        refreshMinimizedState(true);
    }

    private void remapPositionForToggle(boolean targetMinimized) {
        if (layoutParams == null || binding == null) {
            return;
        }
        int oldWidth = binding.getRoot().getWidth();
        if (oldWidth <= 0) {
            oldWidth = targetMinimized
                    ? Math.max(cachedExpandedWidth, dp(MINI_SIZE_DP))
                    : dp(MINI_SIZE_DP);
        }
        float sourceCenterX;
        float sourceCenterY;
        int newWidth;
        float targetCenterX;
        float targetCenterY;

        if (targetMinimized) {
            cacheExpandedAnchor();
            sourceCenterX = resolveExpandedAnchorCenterX();
            sourceCenterY = resolveExpandedAnchorCenterY();
            newWidth = dp(MINI_SIZE_DP);
            targetCenterX = newWidth / 2f;
            targetCenterY = dp(MINI_SIZE_DP) / 2f;
        } else {
            sourceCenterX = dp(MINI_SIZE_DP) / 2f;
            sourceCenterY = dp(MINI_SIZE_DP) / 2f;
            newWidth = cachedExpandedWidth > 0 ? cachedExpandedWidth : oldWidth;
            targetCenterX = cachedExpandedCenterX > 0f
                    ? cachedExpandedCenterX
                    : defaultExpandedAnchorCenter();
            targetCenterY = cachedExpandedCenterY > 0f
                    ? cachedExpandedCenterY
                    : defaultExpandedAnchorCenter();
        }
        remapWindowByCenter(oldWidth, newWidth, sourceCenterX, sourceCenterY, targetCenterX, targetCenterY);
    }

    private void remapWindowByCenter(int oldWidth,
                                     int newWidth,
                                     float sourceCenterX,
                                     float sourceCenterY,
                                     float targetCenterX,
                                     float targetCenterY) {
        if (layoutParams == null) {
            return;
        }
        float nextX = layoutParams.x + (oldWidth - newWidth) + (targetCenterX - sourceCenterX);
        float nextY = layoutParams.y + (sourceCenterY - targetCenterY);
        layoutParams.x = Math.max(0, Math.round(nextX));
        layoutParams.y = Math.max(0, Math.round(nextY));
    }

    private void cacheExpandedAnchor() {
        if (binding == null) {
            return;
        }
        int width = binding.getRoot().getWidth();
        if (width > 0) {
            cachedExpandedWidth = width;
        }
        cachedExpandedCenterX = resolveExpandedAnchorCenterX();
        cachedExpandedCenterY = resolveExpandedAnchorCenterY();
    }

    private float resolveExpandedAnchorCenterX() {
        if (binding != null
                && binding.layoutExpanded.getVisibility() == View.VISIBLE
                && binding.btnMinimize.getWidth() > 0) {
            return binding.layoutExpanded.getLeft()
                    + binding.btnMinimize.getLeft()
                    + binding.btnMinimize.getWidth() / 2f;
        }
        return defaultExpandedAnchorCenter();
    }

    private float resolveExpandedAnchorCenterY() {
        if (binding != null
                && binding.layoutExpanded.getVisibility() == View.VISIBLE
                && binding.btnMinimize.getHeight() > 0) {
            return binding.layoutExpanded.getTop()
                    + binding.btnMinimize.getTop()
                    + binding.btnMinimize.getHeight() / 2f;
        }
        return defaultExpandedAnchorCenter();
    }

    private float defaultExpandedAnchorCenter() {
        return dp(EXPANDED_PADDING_DP) + dp(MINIMIZE_BTN_SIZE_DP) / 2f;
    }

    private void applyWindowAlpha() {
        if (layoutParams == null || binding == null) {
            return;
        }
        float opacity = resolveBackgroundOpacity();
        if (Math.abs(lastAppliedAlpha - opacity) >= 0.001f) {
            int alpha = resolveBackgroundAlpha();
            applyBackgroundAlpha(binding.layoutExpanded, alpha);
            applyBackgroundAlpha(binding.btnMinimize, alpha);
            applyBackgroundAlpha(binding.viewMiniSquare, alpha);
            lastAppliedAlpha = opacity;
        }
        if (layoutParams.alpha == 1f) {
            return;
        }
        layoutParams.alpha = 1f;
        if (showing && !draggingWindow) {
            try {
                windowManager.updateViewLayout(binding.getRoot(), layoutParams);
            } catch (Exception ignored) {
            }
        }
    }

    private void startMiniBlink() {
        if (!minimized || binding == null) {
            return;
        }
        if (System.currentTimeMillis() >= miniBlinkEndAt) {
            miniBlinkActive = false;
            binding.viewMiniSquare.setAlpha(1f);
            setMiniSquareBackground(false);
            return;
        }
        if (miniBlinkActive) {
            return;
        }
        miniBlinkActive = true;
        miniBlinkDimmed = false;
        handler.removeCallbacks(miniBlinkRunnable);
        handler.post(miniBlinkRunnable);
    }

    private void startPriceBlinkIfNeeded() {
        if (binding == null || minimized) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= btcBlinkEndAt && now >= xauBlinkEndAt) {
            return;
        }
        if (priceBlinkActive) {
            return;
        }
        priceBlinkActive = true;
        handler.removeCallbacks(priceBlinkRunnable);
        handler.post(priceBlinkRunnable);
    }

    private void stopAllBlinking() {
        miniBlinkActive = false;
        priceBlinkActive = false;
        handler.removeCallbacks(miniBlinkRunnable);
        handler.removeCallbacks(priceBlinkRunnable);
        if (binding != null) {
            binding.viewMiniSquare.setAlpha(1f);
            setMiniSquareBackground(false);
            applyIdlePriceColors();
        }
    }

    private final Runnable miniBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!miniBlinkActive || binding == null) {
                miniBlinkActive = false;
                if (binding != null) {
                    binding.viewMiniSquare.setAlpha(1f);
                    setMiniSquareBackground(false);
                }
                return;
            }
            if (!minimized) {
                miniBlinkActive = false;
                binding.viewMiniSquare.setAlpha(1f);
                setMiniSquareBackground(false);
                return;
            }
            if (System.currentTimeMillis() >= miniBlinkEndAt) {
                miniBlinkActive = false;
                binding.viewMiniSquare.setAlpha(1f);
                setMiniSquareBackground(false);
                return;
            }
            miniBlinkDimmed = !miniBlinkDimmed;
            setMiniSquareBackground(miniBlinkDimmed);
            binding.viewMiniSquare.setAlpha(miniBlinkDimmed ? 0.65f : 1f);
            handler.postDelayed(this, 300L);
        }
    };

    private void setMiniSquareBackground(boolean blinking) {
        if (binding == null) {
            return;
        }
        binding.viewMiniSquare.setBackgroundResource(blinking
                ? R.drawable.bg_overlay_mini_blink
                : R.drawable.bg_overlay_mini);
        applyBackgroundAlpha(binding.viewMiniSquare, resolveBackgroundAlpha());
    }

    private void applyBackgroundAlpha(View view, int alpha) {
        if (view == null) {
            return;
        }
        Drawable drawable = view.getBackground();
        if (drawable != null) {
            drawable.mutate().setAlpha(alpha);
        }
    }

    private float resolveBackgroundOpacity() {
        int safePercent = Math.max(20, Math.min(100, alphaPercent));
        return safePercent / 100f;
    }

    private int resolveBackgroundAlpha() {
        return Math.round(resolveBackgroundOpacity() * 255f);
    }

    private final Runnable priceBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding == null || minimized) {
                priceBlinkActive = false;
                if (binding != null) {
                    applyIdlePriceColors();
                }
                return;
            }
            long now = System.currentTimeMillis();
            boolean btcActive = now < btcBlinkEndAt;
            boolean xauActive = now < xauBlinkEndAt;
            if (!btcActive && !xauActive) {
                priceBlinkActive = false;
                applyIdlePriceColors();
                return;
            }
            priceBlinkDimmed = !priceBlinkDimmed;
            UiPaletteManager.Palette palette = UiPaletteManager.resolve(context);
            if (btcActive) {
                binding.tvBtcPrice.setAlpha(1f);
                binding.tvBtcPrice.setTextColor(priceBlinkDimmed ? context.getColor(R.color.accent_red) : palette.btc);
            } else {
                binding.tvBtcPrice.setAlpha(1f);
                binding.tvBtcPrice.setTextColor(palette.btc);
            }
            if (xauActive) {
                binding.tvXauPrice.setAlpha(1f);
                binding.tvXauPrice.setTextColor(priceBlinkDimmed ? context.getColor(R.color.accent_red) : palette.xau);
            } else {
                binding.tvXauPrice.setAlpha(1f);
                binding.tvXauPrice.setTextColor(palette.xau);
            }
            handler.postDelayed(this, 300L);
        }
    };

    private void applyIdlePriceColors() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(context);
        binding.tvBtcPrice.setAlpha(1f);
        binding.tvXauPrice.setAlpha(1f);
        binding.tvBtcPrice.setTextColor(palette.btc);
        binding.tvXauPrice.setTextColor(palette.xau);
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private class DragAndClickListener implements View.OnTouchListener {
        private int downX;
        private int downY;
        private float rawX;
        private float rawY;
        private boolean dragging;
        private View touchTarget;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (layoutParams == null || binding == null) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = layoutParams.x;
                    downY = layoutParams.y;
                    rawX = event.getRawX();
                    rawY = event.getRawY();
                    dragging = false;
                    draggingWindow = false;
                    touchTarget = v;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    long now = SystemClock.uptimeMillis();
                    if (now - lastDragUpdateAt < 8L) {
                        return true;
                    }
                    lastDragUpdateAt = now;
                    int deltaX = (int) (event.getRawX() - rawX);
                    int deltaY = (int) (event.getRawY() - rawY);
                    if (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4) {
                        dragging = true;
                        draggingWindow = true;
                    }
                    layoutParams.x = downX - deltaX;
                    layoutParams.y = downY + deltaY;
                    try {
                        windowManager.updateViewLayout(binding.getRoot(), layoutParams);
                    } catch (Exception ignored) {
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    draggingWindow = false;
                    applyWindowAlpha();
                    refreshMinimizedState(false);
                    if (pendingRender) {
                        pendingRender = false;
                        render();
                    }
                    if (!dragging) {
                        handleClick(touchTarget, event);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    draggingWindow = false;
                    applyWindowAlpha();
                    refreshMinimizedState(false);
                    if (pendingRender) {
                        pendingRender = false;
                        render();
                    }
                    return true;
                default:
                    return false;
            }
        }

        private void handleClick(View target, MotionEvent event) {
            if (target == binding.viewMiniSquare) {
                setMinimized(false);
                return;
            }
            if (target == binding.layoutExpanded) {
                if (isInMinimizeArea(event.getX(), event.getY())) {
                    setMinimized(true);
                } else {
                    AppLaunchHelper.openBinance(context);
                }
            }
        }

        private boolean isInMinimizeArea(float x, float y) {
            return x >= 0 && y >= 0 && x <= dp(28) && y <= dp(28);
        }
    }
}
