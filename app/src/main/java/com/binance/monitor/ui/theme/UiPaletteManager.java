/*
 * 统一运行时主题入口，负责把共享控件收口到标准主体语言，并维护悬浮窗、输入框与滚轮的公共视觉真值。
 */
package com.binance.monitor.ui.theme;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.binance.monitor.R;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.ui.rules.ContainerSurfaceRegistry;
import com.binance.monitor.ui.rules.ContainerSurfaceRole;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

public final class UiPaletteManager {

    private static final class PaletteDefinition {
        final int id;
        final String label;
        final int surfaceStartResId;
        final int surfaceEndResId;
        final int cardResId;
        final int controlResId;
        final int strokeResId;
        final int primaryResId;
        final int primarySoftResId;
        final int btcResId;
        final int xauResId;
        final int textPrimaryResId;
        final int textSecondaryResId;
        final int riseResId;
        final int fallResId;
        final int controlSelectedTextResId;
        final int controlUnselectedTextResId;
        final int radiusSmDp;
        final int radiusMdDp;
        final int radiusLgDp;

        PaletteDefinition(int id,
                          @NonNull String label,
                          @ColorRes int surfaceStartResId,
                          @ColorRes int surfaceEndResId,
                          @ColorRes int cardResId,
                          @ColorRes int controlResId,
                          @ColorRes int strokeResId,
                          @ColorRes int primaryResId,
                          @ColorRes int primarySoftResId,
                          @ColorRes int btcResId,
                          @ColorRes int xauResId,
                          @ColorRes int textPrimaryResId,
                          @ColorRes int textSecondaryResId,
                          @ColorRes int riseResId,
                          @ColorRes int fallResId,
                          @ColorRes int controlSelectedTextResId,
                          @ColorRes int controlUnselectedTextResId,
                          int radiusSmDp,
                          int radiusMdDp,
                          int radiusLgDp) {
            this.id = id;
            this.label = label;
            this.surfaceStartResId = surfaceStartResId;
            this.surfaceEndResId = surfaceEndResId;
            this.cardResId = cardResId;
            this.controlResId = controlResId;
            this.strokeResId = strokeResId;
            this.primaryResId = primaryResId;
            this.primarySoftResId = primarySoftResId;
            this.btcResId = btcResId;
            this.xauResId = xauResId;
            this.textPrimaryResId = textPrimaryResId;
            this.textSecondaryResId = textSecondaryResId;
            this.riseResId = riseResId;
            this.fallResId = fallResId;
            this.controlSelectedTextResId = controlSelectedTextResId;
            this.controlUnselectedTextResId = controlUnselectedTextResId;
            this.radiusSmDp = radiusSmDp;
            this.radiusMdDp = radiusMdDp;
            this.radiusLgDp = radiusLgDp;
        }

        @NonNull
        Palette resolve(@NonNull Context context) {
            return new Palette(
                    id,
                    label,
                    color(context, surfaceStartResId),
                    color(context, surfaceEndResId),
                    color(context, cardResId),
                    color(context, controlResId),
                    color(context, strokeResId),
                    color(context, primaryResId),
                    color(context, primarySoftResId),
                    color(context, btcResId),
                    color(context, xauResId),
                    color(context, textPrimaryResId),
                    color(context, textSecondaryResId),
                    color(context, riseResId),
                    color(context, fallResId),
                    color(context, controlSelectedTextResId),
                    color(context, controlUnselectedTextResId),
                    radiusSmDp,
                    radiusMdDp,
                    radiusLgDp
            );
        }
    }

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
        public final int controlSelectedText;
        public final int controlUnselectedText;
        public final int radiusSmDp;
        public final int radiusMdDp;
        public final int radiusLgDp;

        private Palette(int id,
                        String label,
                        int surfaceStart,
                        int surfaceEnd,
                        int card,
                        int control,
                        int stroke,
                        int primary,
                        int primarySoft,
                        int btc,
                        int xau,
                        int textPrimary,
                        int textSecondary,
                        int rise,
                        int fall,
                        int controlSelectedText,
                        int controlUnselectedText,
                        int radiusSmDp,
                        int radiusMdDp,
                        int radiusLgDp) {
            this.id = id;
            this.label = label;
            this.surfaceStart = surfaceStart;
            this.surfaceEnd = surfaceEnd;
            this.card = card;
            this.control = control;
            this.stroke = stroke;
            this.primary = primary;
            this.primarySoft = primarySoft;
            this.btc = btc;
            this.xau = xau;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.rise = rise;
            this.fall = fall;
            this.controlSelectedText = controlSelectedText;
            this.controlUnselectedText = controlUnselectedText;
            this.radiusSmDp = radiusSmDp;
            this.radiusMdDp = radiusMdDp;
            this.radiusLgDp = radiusLgDp;
        }
    }

    private static final PaletteDefinition[] PALETTES = new PaletteDefinition[]{
            new PaletteDefinition(
                    0,
                    "金融专业风",
                    R.color.bg_app_base,
                    R.color.bg_panel_base,
                    R.color.bg_card_base,
                    R.color.bg_field_base,
                    R.color.border_subtle,
                    R.color.accent_primary,
                    R.color.bg_card_base,
                    R.color.trade_buy,
                    R.color.state_warning,
                    R.color.text_primary,
                    R.color.text_secondary,
                    R.color.pnl_profit,
                    R.color.pnl_loss,
                    R.color.text_inverse,
                    R.color.text_secondary,
                    0,
                    0,
                    0
            )
    };

    private UiPaletteManager() {
    }

    public static Palette resolve(Context context) {
        int selectedId = 0;
        try {
            selectedId = ConfigManager.getInstance(context.getApplicationContext()).getColorPalette();
        } catch (Exception ignored) {
        }
        return findById(context, normalizePaletteId(selectedId));
    }

    public static String[] labels() {
        String[] labels = new String[PALETTES.length];
        for (int i = 0; i < PALETTES.length; i++) {
            labels[i] = PALETTES[i].label;
        }
        return labels;
    }

    public static Palette[] all(@NonNull Context context) {
        Palette[] palettes = new Palette[PALETTES.length];
        for (int i = 0; i < PALETTES.length; i++) {
            palettes[i] = PALETTES[i].resolve(context);
        }
        return palettes;
    }

    public static Palette findById(@NonNull Context context, int paletteId) {
        int normalizedId = normalizePaletteId(paletteId);
        for (PaletteDefinition palette : PALETTES) {
            if (palette.id == normalizedId) {
                return palette.resolve(context);
            }
        }
        return PALETTES[0].resolve(context);
    }

    // 按正式容器角色创建基础背景，后续页面和弹层应优先从这里取外壳。
    public static GradientDrawable createSurfaceForRole(@NonNull Context context,
                                                        @NonNull Palette palette,
                                                        @NonNull ContainerSurfaceRole role) {
        switch (role) {
            case PAGE_CANVAS: {
                GradientDrawable drawable = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{palette.surfaceStart, palette.surfaceEnd});
                drawable.setCornerRadius(resolveCornerRadiusPx(context, palette.radiusLgDp));
                return drawable;
            }
            case FLOATING_SURFACE: {
                GradientDrawable drawable = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{palette.surfaceStart, palette.surfaceEnd});
                drawable.setCornerRadius(resolveCornerRadiusPx(context, palette.radiusLgDp));
                // 悬浮窗展开态继续保持细描边，但略微提高可见度，避免深色背景下边界直接消失。
                drawable.setStroke(floatingStrokeWidthPx(context), ColorUtils.blendARGB(palette.surfaceEnd, palette.stroke, 0.36f));
                return drawable;
            }
            case ROW_SURFACE:
                return createRectDrawable(
                        context,
                        palette.card,
                        palette.stroke,
                        resolveCornerRadiusPx(context, palette.radiusMdDp)
                );
            case FIELD_SURFACE:
                return createRectDrawable(
                        context,
                        palette.control,
                        palette.stroke,
                        resolveCornerRadiusPx(context, palette.radiusMdDp)
                );
            case OVERLAY_SURFACE:
            case SECTION_SURFACE:
            default:
                return createRectDrawable(
                        context,
                        palette.card,
                        palette.stroke,
                        resolveCornerRadiusPx(context, palette.radiusLgDp)
                );
        }
    }

    public static GradientDrawable createPageBackground(Context context, Palette palette) {
        return createSurfaceForRole(context, palette, ContainerSurfaceRole.PAGE_CANVAS);
    }

    public static GradientDrawable createFloatingBackground(Context context, Palette palette) {
        return createSurfaceForRole(
                context,
                palette,
                ContainerSurfaceRegistry.resolveSectionRole("floating_window")
        );
    }

    public static GradientDrawable createFilledDrawable(Context context, int fillColor) {
        Palette palette = resolve(context);
        return createRectDrawable(
                context,
                fillColor,
                android.graphics.Color.TRANSPARENT,
                resolveCornerRadiusPx(context, palette.radiusLgDp)
        );
    }

    // 统一模块、弹窗和抽屉的 surface 外观，后续边框类调整只改这里。
    public static GradientDrawable createSurfaceDrawable(Context context, int fillColor, int strokeColor) {
        Palette palette = resolve(context);
        GradientDrawable drawable = createSurfaceForRole(
                context,
                palette,
                ContainerSurfaceRegistry.resolveSectionRole("section_surface")
        );
        drawable.setColor(fillColor);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static GradientDrawable createOutlinedDrawable(Context context, int fillColor, int strokeColor) {
        Palette palette = resolve(context);
        GradientDrawable drawable = createSurfaceForRole(
                context,
                palette,
                ContainerSurfaceRegistry.resolveSectionRole("field_surface")
        );
        drawable.setColor(fillColor);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    private static GradientDrawable createRectDrawable(Context context,
                                                       int fillColor,
                                                       int strokeColor,
                                                       float cornerRadiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(cornerRadiusPx);
        drawable.setColor(fillColor);
        if (strokeColor != android.graphics.Color.TRANSPARENT) {
            drawable.setStroke(dp(context, 1), strokeColor);
        }
        return drawable;
    }

    private static int color(@NonNull Context context, @ColorRes int resId) {
        return ContextCompat.getColor(context, resId);
    }

    private static int dividerColor(@NonNull Context context) {
        return color(context, R.color.border_subtle);
    }

    private static int stateDanger(@NonNull Context context) {
        return color(context, R.color.trade_sell);
    }

    private static int stateSuccess(@NonNull Context context) {
        return color(context, R.color.pnl_profit);
    }

    private static int tradePending(@NonNull Context context) {
        return color(context, R.color.state_warning);
    }

    private static int tradeExit(@NonNull Context context) {
        return stateDanger(context);
    }

    public static GradientDrawable createListRowBackground(Context context, int fillColor, int strokeColor) {
        Palette palette = resolve(context);
        GradientDrawable drawable = createSurfaceForRole(
                context,
                palette,
                ContainerSurfaceRegistry.resolveSectionRole("list_row")
        );
        drawable.setColor(fillColor);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static GradientDrawable createSectionBackground(Context context, int fillColor, int strokeColor) {
        return createSurfaceDrawable(context, fillColor, strokeColor);
    }

    public static GradientDrawable createThemeItemDrawable(Context context, int fillColor, int strokeColor) {
        Palette palette = resolve(context);
        return createRectDrawable(
                context,
                fillColor,
                strokeColor,
                resolveCornerRadiusPx(context, palette.radiusMdDp)
        );
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
        int horizontalMargin = SpacingTokenResolver.screenEdgePx(dialog.getContext());
        ViewGroup.LayoutParams rawParams = bottomSheet.getLayoutParams();
        if (rawParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams bottomSheetParams = (ViewGroup.MarginLayoutParams) rawParams;
            bottomSheetParams.setMarginStart(horizontalMargin);
            bottomSheetParams.setMarginEnd(horizontalMargin);
            bottomSheet.setLayoutParams(bottomSheetParams);
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
        tab.setBackgroundColor(selected ? palette.primarySoft : palette.surfaceStart);
        tab.setTextColor(tintColor);
        tab.setTypeface(null, android.graphics.Typeface.NORMAL);
        applyTextAppearance(tab, R.style.TextAppearance_BinanceMonitor_ControlCompact);
        tab.setIncludeFontPadding(false);
        tab.setSingleLine(true);
        tab.setMaxLines(1);
        tab.setPadding(
                0,
                SpacingTokenResolver.rowGapPx(context),
                0,
                SpacingTokenResolver.rowGapCompactPx(context)
        );
        tab.setCompoundDrawablePadding(SpacingTokenResolver.inlineGapCompactPx(context));
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
            iconRes = R.drawable.ic_nav_analysis;
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

    // ActionButton 是所有带实体背景的文本操作入口真值。
    public static void styleActionButton(@Nullable TextView button,
                                         @NonNull Palette palette,
                                         int fillColor,
                                         int textColor,
                                         @StyleRes int textAppearanceResId,
                                         int horizontalPaddingDp,
                                         int minHeightResId) {
        if (button == null || palette == null) {
            return;
        }
        Context context = button.getContext();
        int minHeightPx = context.getResources().getDimensionPixelSize(minHeightResId);
        int horizontalPaddingPx = resolveRuntimeHorizontalPaddingPx(context, horizontalPaddingDp);
        button.setBackground(createFilledDrawable(context, fillColor));
        ViewCompat.setBackgroundTintList(button, null);
        applySubjectTextLayout(button, textColor, textAppearanceResId);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(minHeightPx);
        button.setMinimumHeight(minHeightPx);
        button.setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0);
    }

    public static void styleActionButton(@Nullable TextView button,
                                         @NonNull Palette palette,
                                         int fillColor,
                                         int textColor,
                                         @StyleRes int textAppearanceResId) {
        styleActionButton(
                button,
                palette,
                fillColor,
                textColor,
                textAppearanceResId,
                12,
                R.dimen.subject_height_md
        );
    }

    // TextTrigger 是纯文字触发器的运行时入口真值。
    public static void styleTextTrigger(@Nullable TextView button,
                                        @NonNull Palette palette,
                                        int fillColor,
                                        int textColor,
                                        @StyleRes int textAppearanceResId) {
        if (button == null) {
            return;
        }
        button.setBackground(createFilledDrawable(button.getContext(), fillColor));
        applySubjectTextLayout(button, textColor, textAppearanceResId);
    }

    // SegmentedOption 是分段选择控件的运行时真值。
    public static void styleSegmentedOption(@Nullable MaterialButton button,
                                            @NonNull Palette palette,
                                            @NonNull String text,
                                            @StyleRes int textAppearanceResId) {
        if (button == null) {
            return;
        }
        button.setText(text);
        applySubjectTextLayout(button, button.getCurrentTextColor(), textAppearanceResId);
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
        int uncheckedBg = color(button.getContext(), R.color.bg_field_base);
        int checkedText = controlSelectedText(button.getContext());
        int uncheckedText = controlUnselectedText(button.getContext());
        button.setBackgroundTintList(new ColorStateList(states, new int[]{checkedBg, uncheckedBg}));
        button.setTextColor(new ColorStateList(states, new int[]{checkedText, uncheckedText}));
        button.setStrokeColor(ColorStateList.valueOf(dividerColor(button.getContext())));
        button.setStrokeWidth(0);
        button.setRippleColor(ColorStateList.valueOf(color(button.getContext(), R.color.accent_primary)));
    }

    // 依据固定字号下的文案实际宽度给分段按钮分配比例，避免再靠微调字号补缝。
    public static void applyContentAwareButtonGroupLayout(@Nullable MaterialButtonToggleGroup group,
                                                          @Nullable MaterialButton[] buttons,
                                                          int horizontalPaddingDp) {
        if (group == null || buttons == null || buttons.length == 0) {
            return;
        }
        int availableWidth = group.getWidth() - group.getPaddingLeft() - group.getPaddingRight();
        if (availableWidth <= 0) {
            return;
        }

        Context context = group.getContext();
        int controlGapPx = SpacingTokenResolver.inlineGapCompactPx(context);
        availableWidth -= controlGapPx * Math.max(0, buttons.length - 1);
        if (availableWidth <= 0) {
            return;
        }
        int horizontalPaddingPx = resolveRuntimeHorizontalPaddingPx(context, horizontalPaddingDp);
        float[] measuredWidths = new float[buttons.length];
        float totalRequiredWidth = 0f;
        for (int i = 0; i < buttons.length; i++) {
            measuredWidths[i] = measureButtonWidth(buttons[i], horizontalPaddingPx);
            totalRequiredWidth += measuredWidths[i];
        }

        if (totalRequiredWidth <= 0f) {
            return;
        }
        for (int i = 0; i < buttons.length; i++) {
            MaterialButton button = buttons[i];
            if (button == null) {
                continue;
            }
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

    // SelectField 标签主体负责选择类控件的标签与下拉项文字真值。
    public static void styleSelectFieldLabel(@Nullable TextView label,
                                             @NonNull Palette palette,
                                             int fillColor,
                                             int textColor,
                                             @StyleRes int textAppearanceResId,
                                             int horizontalPaddingDp,
                                             int minHeightResId) {
        if (label == null) {
            return;
        }
        styleActionButton(
                label,
                palette,
                fillColor,
                textColor,
                textAppearanceResId,
                horizontalPaddingDp,
                minHeightResId
        );
        label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    }

    public static void styleSelectFieldLabel(@Nullable TextView label,
                                             @NonNull Palette palette,
                                             int fillColor,
                                             int textColor,
                                             @StyleRes int textAppearanceResId) {
        styleSelectFieldLabel(
                label,
                palette,
                fillColor,
                textColor,
                textAppearanceResId,
                12,
                R.dimen.subject_height_md
        );
        label.setPadding(
                SpacingTokenResolver.fieldPaddingPx(label.getContext()),
                0,
                SpacingTokenResolver.fieldTrailingReservePx(label.getContext()),
                0
        );
    }

    public static void styleSelectFieldLabel(@Nullable TextView label,
                                             @NonNull Palette palette,
                                             @StyleRes int textAppearanceResId) {
        if (label == null) {
            return;
        }
        applySubjectTextLayout(label, palette.textPrimary, textAppearanceResId);
    }

    // InputField 是运行时输入主体的唯一收口入口，容器与纯输入框都走这里。
    public static void styleInputField(@Nullable TextInputLayout inputLayout,
                                       @NonNull Palette palette) {
        if (inputLayout == null) {
            return;
        }
        inputLayout.setBoxBackgroundColor(palette.control);
        inputLayout.setBoxStrokeColor(Color.TRANSPARENT);
    }

    // 纯 EditText 也统一走 InputField 主链，避免页面继续手写背景、高度和文字颜色。
    public static void styleInputField(@Nullable EditText input,
                                       @NonNull Palette palette,
                                       @StyleRes int textAppearanceResId) {
        if (input == null) {
            return;
        }
        Context context = input.getContext();
        int horizontalPaddingPx = SpacingTokenResolver.fieldPaddingPx(context);
        int minHeightPx = dimenPx(context, R.dimen.subject_height_md);
        input.setBackground(createFilledDrawable(context, palette.control));
        applyTextAppearance(input, textAppearanceResId);
        input.setTextColor(palette.textPrimary);
        input.setHintTextColor(palette.textSecondary);
        input.setMinHeight(minHeightPx);
        input.setMinimumHeight(minHeightPx);
        input.setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0);
        input.setGravity((input.getGravity() & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_VERTICAL);
    }

    // ToggleChoice 统一复选与切换类选择控件的文字和选中色。
    public static void styleToggleChoice(@Nullable CompoundButton button,
                                         @NonNull Palette palette) {
        if (button == null) {
            return;
        }
        button.setBackground(null);
        button.setTextColor(palette.textPrimary);
        CompoundButtonCompat.setButtonTintList(button, createCompoundButtonTintList(palette));
    }

    // PickerWheel 统一数字滚轮的文字颜色和字号入口。
    public static void applyPickerWheelTextStyle(@Nullable NumberPicker picker,
                                                 int textColor,
                                                 @StyleRes int textAppearanceResId) {
        if (picker == null) {
            return;
        }
        try {
            java.lang.reflect.Field selectorWheelPaintField =
                    NumberPicker.class.getDeclaredField("mSelectorWheelPaint");
            selectorWheelPaintField.setAccessible(true);
            Paint paint = (Paint) selectorWheelPaintField.get(picker);
            if (paint != null) {
                paint.setColor(textColor);
                paint.setAlpha(255);
                if (textAppearanceResId != 0) {
                    TextAppearanceScaleResolver.applyTextSize(
                            paint,
                            picker.getContext(),
                            textAppearanceResId
                    );
                }
            }
        } catch (Exception ignored) {
        }
        for (int index = 0; index < picker.getChildCount(); index++) {
            View child = picker.getChildAt(index);
            if (child instanceof android.widget.EditText) {
                android.widget.EditText editText = (android.widget.EditText) child;
                editText.setTextColor(textColor);
                editText.setHintTextColor(textColor);
                editText.setAlpha(1f);
                if (textAppearanceResId != 0) {
                    TextAppearanceScaleResolver.applyTextAppearance(editText, textAppearanceResId);
                    editText.setTextColor(textColor);
                    editText.setHintTextColor(textColor);
                }
            }
        }
    }

    private static void applySubjectTextLayout(@NonNull TextView textView,
                                               int textColor,
                                               @StyleRes int textAppearanceResId) {
        textView.setTextColor(textColor);
        applyTextAppearance(textView, textAppearanceResId);
        textView.setTypeface(null, android.graphics.Typeface.NORMAL);
        textView.setGravity(Gravity.CENTER);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setIncludeFontPadding(false);
        textView.setSingleLine(true);
        textView.setMaxLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setPaintFlags(textView.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
    }

    private static void applyRecursively(View view, Palette palette) {
        Context context = view.getContext();
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            card.setRadius(resolveCornerRadiusPx(context, palette.radiusLgDp));
            card.setCardElevation(0f);
            card.setStrokeColor(palette.stroke);
            card.setStrokeWidth(dp(context, 1));
            card.setCardBackgroundColor(palette.card);
        } else if (view instanceof TextInputLayout) {
            styleInputField((TextInputLayout) view, palette);
        } else if (view instanceof SeekBar) {
            SeekBar seekBar = (SeekBar) view;
            seekBar.setProgressTintList(ColorStateList.valueOf(palette.primary));
            seekBar.setThumbTintList(ColorStateList.valueOf(palette.primary));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(palette.stroke));
        } else if (view instanceof CompoundButton) {
            styleToggleChoice((CompoundButton) view, palette);
        } else if (view instanceof Button && !(view instanceof MaterialButton)) {
            Button button = (Button) view;
            if (button.getCurrentTextColor() != color(context, R.color.text_inverse)) {
                applyLegacyOutlinedButtonSubject(button, palette);
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
        return resolve(context).control;
    }

    public static int neutralStroke(Context context) {
        return resolve(context).stroke;
    }

    public static int neutralText(Context context) {
        return resolve(context).textPrimary;
    }

    public static int subtleText(Context context) {
        return resolve(context).textSecondary;
    }

    public static int controlSelectedText(Context context) {
        return resolve(context).controlSelectedText;
    }

    public static int controlUnselectedText(Context context) {
        return resolve(context).controlUnselectedText;
    }

    // 统一运行时 TextView 的字号入口，避免页面直接 setTextSize 写死数值。
    public static void applyTextAppearance(@Nullable TextView textView, @StyleRes int textAppearanceResId) {
        if (textView == null) {
            return;
        }
        TextAppearanceScaleResolver.applyTextAppearance(textView, textAppearanceResId);
    }

    public static float radiusSmPx(Context context, @Nullable Palette palette) {
        Palette activePalette = palette == null ? resolve(context) : palette;
        return resolveCornerRadiusPx(context, activePalette.radiusSmDp);
    }

    public static float radiusMdPx(Context context, @Nullable Palette palette) {
        Palette activePalette = palette == null ? resolve(context) : palette;
        return resolveCornerRadiusPx(context, activePalette.radiusMdDp);
    }

    public static float radiusLgPx(Context context, @Nullable Palette palette) {
        Palette activePalette = palette == null ? resolve(context) : palette;
        return resolveCornerRadiusPx(context, activePalette.radiusLgDp);
    }

    public static int strokeWidthPx(Context context) {
        return dp(context, 1);
    }

    // 悬浮窗展开态边框单独略加粗，保证深色背景下也能看出轻微层次。
    private static int floatingStrokeWidthPx(Context context) {
        return dp(context, 1);
    }

    // 主题设置入口已删除，历史 palette id 统一回落到默认主题。
    private static int normalizePaletteId(int paletteId) {
        return 0;
    }

    private static float measureButtonWidth(@Nullable MaterialButton button,
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
        return probe.measureText(label) + horizontalPaddingPx * 2f;
    }

    // 将运行时主体入口上的语义内边距映射回正式 spacing token。
    private static int resolveRuntimeHorizontalPaddingPx(@NonNull Context context, int horizontalPaddingDp) {
        if (horizontalPaddingDp == 12) {
            return SpacingTokenResolver.fieldPaddingPx(context);
        }
        if (horizontalPaddingDp == 8) {
            return SpacingTokenResolver.fieldPaddingCompactPx(context);
        }
        return dp(context, horizontalPaddingDp);
    }

    // 普通 Button 兼容分支只保留外框背景差异，文字与排版真值统一回到 TextTrigger 主体入口。
    private static void applyLegacyOutlinedButtonSubject(@NonNull Button button,
                                                         @NonNull Palette palette) {
        styleTextTrigger(
                button,
                palette,
                palette.card,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        button.setBackground(createOutlinedDrawable(button.getContext(), palette.card, palette.stroke));
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

    private static int dimenPx(@NonNull Context context, int dimenResId) {
        return context.getResources().getDimensionPixelSize(dimenResId);
    }

    private static float resolveCornerRadiusPx(Context context, int radiusDp) {
        return dp(context, radiusDp);
    }
}
