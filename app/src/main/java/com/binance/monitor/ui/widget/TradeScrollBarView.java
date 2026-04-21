package com.binance.monitor.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;
import com.binance.monitor.ui.theme.SpacingTokenResolver;

public class TradeScrollBarView extends View {

    public interface OnThumbDragListener {
        void onDragFractionChanged(float fraction);

        void onDragStateChanged(boolean dragging);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF thumbRect = new RectF();

    private final float trackWidthPx;
    private final float thumbWidthPx;
    private final float minThumbHeightPx;
    private final float touchSlop;

    @Nullable
    private OnThumbDragListener onThumbDragListener;

    private boolean dragging;
    private float dragFraction;
    private float downY;
    private int scrollOffset;
    private int scrollExtent;
    private int scrollRange;

    public TradeScrollBarView(@NonNull Context context) {
        this(context, null);
    }

    public TradeScrollBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TradeScrollBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackWidthPx = SpacingTokenResolver.dpFloat(context, R.dimen.trade_scrollbar_track_width);
        thumbWidthPx = SpacingTokenResolver.dpFloat(context, R.dimen.trade_scrollbar_thumb_width);
        minThumbHeightPx = SpacingTokenResolver.dpFloat(context, R.dimen.trade_scrollbar_min_thumb_height);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        trackPaint.setColor(ContextCompat.getColor(context, R.color.border_subtle));
        thumbPaint.setColor(ContextCompat.getColor(context, R.color.accent_primary));
        setClickable(true);
        setFocusable(false);
    }

    public void setOnThumbDragListener(@Nullable OnThumbDragListener listener) {
        this.onThumbDragListener = listener;
    }

    public void setScrollMetrics(int offset, int extent, int range) {
        scrollOffset = Math.max(0, offset);
        scrollExtent = Math.max(0, extent);
        scrollRange = Math.max(0, range);
        if (!dragging) {
            dragFraction = resolveScrollFraction();
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float trackLeft = centerX - trackWidthPx / 2f;
        float trackTop = getPaddingTop();
        float trackBottom = getHeight() - getPaddingBottom();
        if (trackBottom <= trackTop) {
            return;
        }

        trackRect.set(trackLeft, trackTop, trackLeft + trackWidthPx, trackBottom);
        float trackRadius = trackWidthPx / 2f;
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint);

        float trackHeight = trackRect.height();
        float thumbHeight = resolveThumbHeight(trackHeight);
        float availableTravel = Math.max(0f, trackHeight - thumbHeight);
        float thumbTop = trackTop + (availableTravel * dragFraction);
        float thumbLeft = centerX - thumbWidthPx / 2f;
        thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbWidthPx, thumbTop + thumbHeight);
        float thumbRadius = thumbWidthPx / 2f;
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (getVisibility() != VISIBLE || !isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isTouchOnScrollbar(event.getX(), event.getY())) {
                    return false;
                }
                downY = event.getY();
                setDragging(true);
                updateFractionFromTouch(event.getY(), true);
                requestParentDisallowIntercept(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(event.getY() - downY) > touchSlop) {
                    setDragging(true);
                }
                if (dragging) {
                    updateFractionFromTouch(event.getY(), true);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    updateFractionFromTouch(event.getY(), true);
                    setDragging(false);
                    requestParentDisallowIntercept(false);
                    performClick();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    setDragging(false);
                    requestParentDisallowIntercept(false);
                    return true;
                }
                return false;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateFractionFromTouch(float touchY, boolean notifyListener) {
        float trackTop = getPaddingTop();
        float trackBottom = getHeight() - getPaddingBottom();
        float trackHeight = trackBottom - trackTop;
        float thumbHeight = resolveThumbHeight(trackHeight);
        float availableTravel = Math.max(1f, trackHeight - thumbHeight);
        float clampedY = Math.max(trackTop, Math.min(touchY, trackBottom));
        float top = Math.max(trackTop, Math.min(clampedY - (thumbHeight / 2f), trackBottom - thumbHeight));
        dragFraction = (top - trackTop) / availableTravel;
        invalidate();
        if (notifyListener && onThumbDragListener != null) {
            onThumbDragListener.onDragFractionChanged(dragFraction);
        }
    }

    private float resolveScrollFraction() {
        int scrollable = Math.max(0, scrollRange - scrollExtent);
        if (scrollable <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, scrollOffset / (float) scrollable));
    }

    private float resolveThumbHeight(float trackHeight) {
        if (scrollRange <= 0 || scrollExtent <= 0) {
            return Math.min(trackHeight, minThumbHeightPx);
        }
        float proportionalHeight = trackHeight * (scrollExtent / (float) scrollRange);
        return Math.max(minThumbHeightPx, Math.min(trackHeight, proportionalHeight));
    }

    private boolean isTouchOnScrollbar(float x, float y) {
        if (y < 0f || y > getHeight()) {
            return false;
        }
        float halfTouchWidth = SpacingTokenResolver.dpFloat(getContext(), R.dimen.trade_scrollbar_touch_half_width);
        float centerX = getWidth() / 2f;
        return x >= centerX - halfTouchWidth && x <= centerX + halfTouchWidth;
    }

    private void setDragging(boolean dragging) {
        if (this.dragging == dragging) {
            return;
        }
        this.dragging = dragging;
        setPressed(dragging);
        if (onThumbDragListener != null) {
            onThumbDragListener.onDragStateChanged(dragging);
        }
    }

    private void requestParentDisallowIntercept(boolean disallowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }
}
