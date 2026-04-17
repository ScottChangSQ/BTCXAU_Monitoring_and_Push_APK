package com.binance.monitor.ui.theme;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.core.widget.TextViewCompat;

import com.binance.monitor.R;
import com.binance.monitor.data.local.ConfigManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

public final class UiPaletteManager {

    public static final class Palette {
        public final int id;
        public final String label;
        public final int surfaceStart;
        public final int surfaceEnd;
        public final int card;
        public final int control;
        public final int stroke;
        public final int primary;
        public final int primarySoft;
        public final int btc;
        public final int xau;
        public final int textPrimary;
        public final int textSecondary;
        public final int rise;
        public final int fall;

        private Palette(int id,
                        String label,
                        String surfaceStartHex,
                        String surfaceEndHex,
                        String cardHex,
                        String controlHex,
                        String strokeHex,
                        String primaryHex,
                        String primarySoftHex,
                        String btcHex,
                        String xauHex,
                        String textPrimaryHex,
                        String textSecondaryHex,
                        String riseHex,
                        String fallHex) {
            this.id = id;
            this.label = label;
            this.surfaceStart = Color.parseColor(surfaceStartHex);
            this.surfaceEnd = Color.parseColor(surfaceEndHex);
            this.card = Color.parseColor(cardHex);
            this.control = Color.parseColor(controlHex);
            this.stroke = Color.parseColor(strokeHex);
            this.primary = Color.parseColor(primaryHex);
            this.primarySoft = Color.parseColor(primarySoftHex);
            this.btc = Color.parseColor(btcHex);
            this.xau = Color.parseColor(xauHex);
            this.textPrimary = Color.parseColor(textPrimaryHex);
            this.textSecondary = Color.parseColor(textSecondaryHex);
            this.rise = Color.parseColor(riseHex);
            this.fall = Color.parseColor(fallHex);
        }
    }

    private static final Palette[] PALETTES = new Palette[]{
            new Palette(0, "石墨金", "#06080B", "#0A0E12", "#12181F", "#161D25", "#27313A", "#D1A055", "#2C2114", "#4F8CFF", "#D8B061", "#F2F5F7", "#93A0AA", "#2DB784", "#E85C5C"),
            new Palette(1, "复古铜", "#080605", "#110E0B", "#1B1612", "#231D18", "#3A3027", "#B9884A", "#302114", "#8BA9C8", "#D1A055", "#F4EEE7", "#A99A89", "#5E9B73", "#C56E52"),
            new Palette(2, "终端金", "#06080B", "#0A0E12", "#131920", "#171E26", "#2A343D", "#F0B44F", "#3A2A10", "#56A6FF", "#F3C86B", "#F3F5F7", "#94A1AA", "#31C78A", "#F06464"),
            new Palette(3, "蓝灰屏", "#070A0E", "#0E1319", "#141B23", "#19212A", "#2B3641", "#5C95FF", "#1A2942", "#46C6E9", "#CDB56E", "#EFF3F7", "#90A0AD", "#39C0A2", "#E76D7C"),
            new Palette(4, "墨绿屏", "#070907", "#0C100C", "#131914", "#182019", "#28342B", "#7DBA6E", "#1F2C1A", "#6E9ED8", "#C3A668", "#F1F5F1", "#96A69A", "#44BF88", "#D96868")
    };

    private UiPaletteManager() {
    }

    public static Palette resolve(Context context) {
        int selectedId = 0;
        try {
            selectedId = ConfigManager.getInstance(context.getApplicationContext()).getColorPalette();
        } catch (Exception ignored) {
        }
        return findById(selectedId);
    }

    public static String[] labels() {
        String[] labels = new String[PALETTES.length];
        for (int i = 0; i < PALETTES.length; i++) {
            labels[i] = PALETTES[i].label;
        }
        return labels;
    }

    public static Palette[] all() {
        return PALETTES.clone();
    }

    public static Palette findById(int paletteId) {
        for (Palette palette : PALETTES) {
            if (palette.id == paletteId) {
                return palette;
            }
        }
        return PALETTES[0];
    }

    public static GradientDrawable createPageBackground(Context context, Palette palette) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{palette.surfaceStart, palette.surfaceEnd});
        drawable.setCornerRadius(0f);
        return drawable;
    }

    public static GradientDrawable createFloatingBackground(Context context, Palette palette) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{palette.surfaceStart, palette.surfaceEnd});
        drawable.setCornerRadius(resolveRadiusPx(context, R.dimen.radius_lg));
        return drawable;
    }

    public static GradientDrawable createFilledDrawable(Context context, int fillColor) {
        return createRectDrawable(context, fillColor, android.graphics.Color.TRANSPARENT, R.dimen.radius_lg);
    }

    // 统一模块、弹窗和抽屉的 surface 外观，后续边框类调整只改这里。
    public static GradientDrawable createSurfaceDrawable(Context context, int fillColor, int strokeColor) {
        return createRectDrawable(context, fillColor, strokeColor, R.dimen.radius_lg);
    }

    public static GradientDrawable createOutlinedDrawable(Context context, int fillColor, int strokeColor) {
        return createRectDrawable(context, fillColor, strokeColor, R.dimen.radius_md);
    }

    private static GradientDrawable createRectDrawable(Context context,
                                                       int fillColor,
                                                       int strokeColor,
                                                       int cornerResId) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(resolveRadiusPx(context, cornerResId));
        drawable.setColor(fillColor);
        if (strokeColor != android.graphics.Color.TRANSPARENT) {
            drawable.setStroke(dp(context, 1), strokeColor);
        }
        return drawable;
    }

    public static GradientDrawable createListRowBackground(Context context, int fillColor, int strokeColor) {
        return createRectDrawable(context, fillColor, strokeColor, R.dimen.radius_md);
    }

    public static GradientDrawable createSectionBackground(Context context, int fillColor, int strokeColor) {
        return createSurfaceDrawable(context, fillColor, strokeColor);
    }

    public static GradientDrawable createThemeItemDrawable(Context context, int fillColor, int strokeColor) {
        return createRectDrawable(context, fillColor, strokeColor, R.dimen.radius_md);
    }

    public static void applyPageTheme(View root, Palette palette) {
        if (root == null) {
            return;
        }
        root.setBackground(createPageBackground(root.getContext(), palette));
        applyRecursively(root, palette);
    }

    // 统一普通弹窗窗口外壳，避免 Material 默认圆角背景重新露出来。
    public static void applyAlertDialogSurface(@Nullable Dialog dialog, @Nullable Palette palette) {
        if (dialog == null || palette == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(createSurfaceDrawable(dialog.getContext(), palette.card, palette.stroke));
    }

    // 统一底部抽屉外壳和承载层背景，避免外层容器继续保留圆角。
    public static void applyBottomSheetSurface(@Nullable BottomSheetDialog dialog, @Nullable Palette palette) {
        if (dialog == null || palette == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        bottomSheet.setBackground(createSurfaceDrawable(dialog.getContext(), palette.card, palette.stroke));
    }

    // 按当前主题同步系统状态栏和导航栏，避免深浅主题下图标不可见。
    public static void applySystemBars(ComponentActivity activity, Palette palette) {
        if (activity == null || palette == null) {
            return;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        window.setStatusBarColor(palette.surfaceStart);
        window.setNavigationBarColor(palette.surfaceEnd);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller == null) {
            return;
        }
        controller.setAppearanceLightStatusBars(isLightColor(palette.surfaceStart));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.setAppearanceLightNavigationBars(isLightColor(palette.surfaceEnd));
        }
    }

    // 统一底部导航按钮样式，确保不同主题下背景和文字同步切换。
    public static void styleBottomNavTab(TextView tab, boolean selected, Palette palette) {
        if (tab == null || palette == null) {
            return;
        }
        Context context = tab.getContext();
        int tintColor = selected ? controlSelectedText(context) : controlUnselectedText(context);
        tab.setBackgroundColor(palette.surfaceStart);
        tab.setTextColor(tintColor);
        tab.setTypeface(null, android.graphics.Typeface.NORMAL);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tab.setIncludeFontPadding(false);
        tab.setSingleLine(true);
        tab.setMaxLines(1);
        tab.setPadding(0, dp(context, 8), 0, dp(context, 6));
        tab.setCompoundDrawablePadding(dp(context, 2));
        tab.setGravity(android.view.Gravity.CENTER);
        Drawable topIcon = resolveBottomNavIconDrawable(context, tab.getId(), tintColor);
        tab.setCompoundDrawablesRelativeWithIntrinsicBounds(null, topIcon, null, null);
    }

    // 为底部导航统一分配图标并跟随当前主题着色。
    @Nullable
    private static Drawable resolveBottomNavIconDrawable(Context context, int viewId, int tintColor) {
        int iconRes;
        if (viewId == R.id.tabTrading) {
            iconRes = R.drawable.ic_nav_chart;
        } else if (viewId == R.id.tabAccount) {
            iconRes = R.drawable.ic_nav_account;
        } else if (viewId == R.id.tabAnalysis) {
            iconRes = R.drawable.ic_nav_account;
        } else {
            return null;
        }
        Drawable drawable = ContextCompat.getDrawable(context, iconRes);
        if (drawable == null) {
            return null;
        }
        Drawable wrapped = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(wrapped, tintColor);
        return wrapped;
    }

    // 统一图表页按钮样式，避免周期和指标按钮各自维护颜色逻辑。
    public static void styleInlineTextButton(Button button,
                                             boolean selected,
                                             Palette palette,
                                             float textSizeSp) {
        if (button == null || palette == null) {
            return;
        }
        int fillColor = selected ? palette.card : palette.surfaceStart;
        button.setBackground(createFilledDrawable(button.getContext(), fillColor));
        button.setTextColor(selected
                ? controlSelectedText(button.getContext())
                : controlUnselectedText(button.getContext()));
        button.setTypeface(null, android.graphics.Typeface.NORMAL);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        button.setPaintFlags(button.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
    }

    // 统一方角按钮的文字排版和最小触达尺寸，避免不同页面重复维护一套规则。
    public static void styleSquareTextAction(@Nullable TextView button,
                                             @NonNull Palette palette,
                                             int fillColor,
                                             int textColor,
                                             float textSizeSp,
                                             int horizontalPaddingDp,
                                             int minHeightResId,
                                             boolean enableAutoSize) {
        if (button == null) {
            return;
        }
        Context context = button.getContext();
        int minHeightPx = context.getResources().getDimensionPixelSize(minHeightResId);
        int horizontalPaddingPx = dp(context, horizontalPaddingDp);
        button.setBackground(createFilledDrawable(context, fillColor));
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setIncludeFontPadding(false);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(minHeightPx);
        button.setMinimumHeight(minHeightPx);
        button.setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0);
        if (enableAutoSize) {
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    button,
                    8,
                    Math.round(textSizeSp),
                    1,
                    TypedValue.COMPLEX_UNIT_SP
            );
        } else {
            TextViewCompat.setAutoSizeTextTypeWithDefaults(button, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        }
    }

    // 统一交易页顶部按钮视觉，保证产品、模式和状态入口属于同一组控件。
    public static void styleTopControlButton(@Nullable TextView button,
                                             @NonNull Palette palette,
                                             boolean selected,
                                             boolean enableAutoSize) {
        if (button == null) {
            return;
        }
        int fillColor = selected
                ? ColorUtils.setAlphaComponent(palette.primary, 44)
                : palette.control;
        int textColor = selected
                ? controlSelectedText(button.getContext())
                : palette.textPrimary;
        styleSquareTextAction(
                button,
                palette,
                fillColor,
                textColor,
                13f,
                8,
                R.dimen.control_height_lg,
                enableAutoSize
        );
    }

    // 统一交易页顶部产品标签视觉，避免和相邻按钮出现不同层级。
    public static void styleTopControlLabel(@Nullable TextView label,
                                            @NonNull Palette palette) {
        if (label == null) {
            return;
        }
        styleSquareTextAction(
                label,
                palette,
                palette.control,
                palette.textPrimary,
                13f,
                8,
                R.dimen.control_height_lg,
                false
        );
    }

    // 统一分段按钮的基础视觉和文字合同，避免桥接页与正式页分别维护同一套实现。
    public static void styleSegmentedButton(@Nullable MaterialButton button,
                                            @NonNull Palette palette,
                                            @NonNull String text,
                                            float textSizeSp) {
        if (button == null) {
            return;
        }
        button.setText(text);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setCornerRadius(0);
        button.setShapeAppearanceModel(button.getShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(0f)
                .build());

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int checkedBg = palette.primary;
        int uncheckedBg = ContextCompat.getColor(button.getContext(), R.color.bg_input);
        int checkedText = controlSelectedText(button.getContext());
        int uncheckedText = controlUnselectedText(button.getContext());
        button.setBackgroundTintList(new ColorStateList(states, new int[]{checkedBg, uncheckedBg}));
        button.setTextColor(new ColorStateList(states, new int[]{checkedText, uncheckedText}));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(button.getContext(), R.color.stroke_card)));
        button.setStrokeWidth(0);
        button.setRippleColor(ColorStateList.valueOf(ContextCompat.getColor(button.getContext(), R.color.accent_gold)));
    }

    // 依据文案实际宽度给分段按钮分配比例，保证文字可见而不是被平均宽度挤压。
    public static void applyContentAwareButtonGroupLayout(@Nullable MaterialButtonToggleGroup group,
                                                          @Nullable MaterialButton[] buttons,
                                                          float maxTextSizeSp,
                                                          float minTextSizeSp,
                                                          int horizontalPaddingDp) {
        if (group == null || buttons == null || buttons.length == 0) {
            return;
        }
        int availableWidth = group.getWidth() - group.getPaddingLeft() - group.getPaddingRight();
        if (availableWidth <= 0) {
            return;
        }

        Context context = group.getContext();
        int controlGapPx = context.getResources().getDimensionPixelSize(R.dimen.control_group_gap);
        availableWidth -= controlGapPx * Math.max(0, buttons.length - 1);
        if (availableWidth <= 0) {
            return;
        }
        int horizontalPaddingPx = dp(context, horizontalPaddingDp);
        float resolvedSizeSp = maxTextSizeSp;
        float[] measuredWidths = new float[buttons.length];
        float totalRequiredWidth = 0f;
        while (resolvedSizeSp >= minTextSizeSp) {
            totalRequiredWidth = 0f;
            for (int i = 0; i < buttons.length; i++) {
                measuredWidths[i] = measureButtonWidth(buttons[i], resolvedSizeSp, horizontalPaddingPx);
                totalRequiredWidth += measuredWidths[i];
            }
            if (totalRequiredWidth <= availableWidth || resolvedSizeSp == minTextSizeSp) {
                break;
            }
            resolvedSizeSp = Math.max(minTextSizeSp, resolvedSizeSp - 0.5f);
        }

        if (totalRequiredWidth <= 0f) {
            return;
        }
        for (int i = 0; i < buttons.length; i++) {
            MaterialButton button = buttons[i];
            if (button == null) {
                continue;
            }
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, resolvedSizeSp);
            button.setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0);
            if (button.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
                params.width = 0;
                params.weight = Math.max(1f, measuredWidths[i]) / totalRequiredWidth;
                params.setMarginStart(i == 0 ? 0 : controlGapPx);
                params.setMarginEnd(0);
                button.setLayoutParams(params);
            }
        }
    }

    // 统一产品下拉项文字样式，避免主题切换后出现不可读颜色。
    public static void styleSpinnerItemText(TextView textView, Palette palette, float textSizeSp) {
        if (textView == null || palette == null) {
            return;
        }
        textView.setTextColor(palette.textPrimary);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        textView.setTypeface(null, android.graphics.Typeface.NORMAL);
    }

    private static void applyRecursively(View view, Palette palette) {
        Context context = view.getContext();
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            card.setRadius(0f);
            card.setCardElevation(0f);
            card.setStrokeColor(palette.stroke);
            card.setStrokeWidth(dp(context, 1));
            card.setCardBackgroundColor(palette.card);
        } else if (view instanceof TextInputLayout) {
            TextInputLayout inputLayout = (TextInputLayout) view;
            inputLayout.setBoxBackgroundColor(palette.control);
            inputLayout.setBoxStrokeColor(Color.TRANSPARENT);
        } else if (view instanceof SeekBar) {
            SeekBar seekBar = (SeekBar) view;
            seekBar.setProgressTintList(ColorStateList.valueOf(palette.primary));
            seekBar.setThumbTintList(ColorStateList.valueOf(palette.primary));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(palette.stroke));
        } else if (view instanceof CompoundButton) {
            CompoundButton button = (CompoundButton) view;
            button.setBackground(null);
            button.setTextColor(palette.textPrimary);
            CompoundButtonCompat.setButtonTintList(button, createCompoundButtonTintList(palette));
        } else if (view instanceof Button && !(view instanceof MaterialButton)) {
            Button button = (Button) view;
            if (button.getCurrentTextColor() != ContextCompat.getColor(context, R.color.white)) {
                button.setBackground(createOutlinedDrawable(context, palette.card, palette.stroke));
                button.setTextColor(palette.textPrimary);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursively(group.getChildAt(i), palette);
            }
        }
    }

    public static int neutralFill(Context context) {
        return ContextCompat.getColor(context, R.color.bg_input);
    }

    public static int neutralStroke(Context context) {
        return ContextCompat.getColor(context, R.color.stroke_card);
    }

    public static int neutralText(Context context) {
        return ContextCompat.getColor(context, R.color.text_primary);
    }

    public static int subtleText(Context context) {
        return ContextCompat.getColor(context, R.color.text_secondary);
    }

    public static int controlSelectedText(Context context) {
        return ContextCompat.getColor(context, R.color.text_control_selected);
    }

    public static int controlUnselectedText(Context context) {
        return ContextCompat.getColor(context, R.color.text_control_unselected);
    }

    private static float measureButtonWidth(@Nullable MaterialButton button,
                                            float textSizeSp,
                                            int horizontalPaddingPx) {
        if (button == null) {
            return 0f;
        }
        CharSequence text = button.getText();
        String label = text == null ? "" : text.toString();
        if (label.isEmpty()) {
            return horizontalPaddingPx * 2f;
        }
        TextPaint probe = new TextPaint(button.getPaint());
        probe.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                button.getResources().getDisplayMetrics()
        ));
        return probe.measureText(label) + horizontalPaddingPx * 2f;
    }

    private static boolean isLightColor(int color) {
        return ColorUtils.calculateLuminance(color) >= 0.55d;
    }

    // 统一复选控件的勾选色，避免被当成普通按钮套上背景框。
    private static ColorStateList createCompoundButtonTintList(Palette palette) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int[] colors = new int[]{palette.primary, palette.textSecondary};
        return new ColorStateList(states, colors);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static float resolveRadiusPx(Context context, int dimenResId) {
        return context.getResources().getDimension(dimenResId);
    }
}
