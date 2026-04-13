package com.binance.monitor.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.binance.monitor.R;
import com.binance.monitor.data.local.ConfigManager;
import com.google.android.material.button.MaterialButton;
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
            new Palette(0, "金融专业风", "#061018", "#0D1B24", "#12202D", "#18293B", "#27425D", "#23C26B", "#123123", "#28D97A", "#F5B34B", "#F1F7FF", "#8AA8BE", "#28D97A", "#F05D5E"),
            new Palette(1, "复古风", "#EEE2C8", "#E4D2AF", "#F4E9D2", "#EADBBC", "#B99E6A", "#7B5E3B", "#D7C3A0", "#8B6D45", "#B88746", "#3E2E1F", "#8A7155", "#4F8A5B", "#B85C38"),
            new Palette(2, "币安风格", "#11161F", "#181F2A", "#1B2430", "#202C3C", "#37465D", "#F0B90B", "#3A2D00", "#29C46A", "#F5C45B", "#ECF3FF", "#92A3B8", "#29C46A", "#F6465D"),
            new Palette(3, "TradingView风格", "#0C1017", "#151B23", "#171F2A", "#1F2935", "#304256", "#42A5F5", "#14273A", "#2EC7C9", "#F9D65C", "#E7EEF7", "#8FA0B6", "#2EC7C9", "#E05A6E"),
            new Palette(4, "浅色风格", "#F7F9FC", "#EDEFF5", "#FFFFFF", "#EEF2F8", "#CFD8E6", "#3B82F6", "#D9E8FF", "#3B82F6", "#D8A94A", "#172033", "#637089", "#16A34A", "#DC2626")
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
                new int[]{palette.card, palette.control});
        drawable.setCornerRadius(0f);
        drawable.setStroke(dp(context, 1), palette.stroke);
        return drawable;
    }

    public static GradientDrawable createFilledDrawable(Context context, int fillColor) {
        return createRectDrawable(context, fillColor, fillColor, 0);
    }

    public static GradientDrawable createOutlinedDrawable(Context context, int fillColor, int strokeColor) {
        return createRectDrawable(context, fillColor, strokeColor, 0);
    }

    private static GradientDrawable createRectDrawable(Context context,
                                                       int fillColor,
                                                       int strokeColor,
                                                       int cornerDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, cornerDp));
        drawable.setColor(fillColor);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static GradientDrawable createListRowBackground(Context context, int fillColor, int strokeColor) {
        return createRectDrawable(context, fillColor, strokeColor, 0);
    }

    public static GradientDrawable createSectionBackground(Context context, int fillColor, int strokeColor) {
        return createRectDrawable(context, fillColor, strokeColor, 0);
    }

    public static GradientDrawable createThemeItemDrawable(Context context, int fillColor, int strokeColor) {
        return createRectDrawable(context, fillColor, strokeColor, 0);
    }

    public static void applyPageTheme(View root, Palette palette) {
        if (root == null) {
            return;
        }
        root.setBackground(createPageBackground(root.getContext(), palette));
        applyRecursively(root, palette);
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
        int tintColor = selected ? palette.primary : palette.textSecondary;
        tab.setBackgroundColor(Color.TRANSPARENT);
        tab.setTextColor(tintColor);
        tab.setTypeface(null, android.graphics.Typeface.NORMAL);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tab.setIncludeFontPadding(false);
        tab.setSingleLine(true);
        tab.setMaxLines(1);
        tab.setPadding(0, dp(context, 6), 0, dp(context, 4));
        tab.setCompoundDrawablePadding(dp(context, 2));
        tab.setGravity(android.view.Gravity.CENTER);
        Drawable topIcon = resolveBottomNavIconDrawable(context, tab.getId(), tintColor);
        tab.setCompoundDrawablesRelativeWithIntrinsicBounds(null, topIcon, null, null);
    }

    // 为底部导航统一分配图标并跟随当前主题着色。
    @Nullable
    private static Drawable resolveBottomNavIconDrawable(Context context, int viewId, int tintColor) {
        int iconRes;
        if (viewId == R.id.tabMarketMonitor) {
            iconRes = R.drawable.ic_nav_monitor;
        } else if (viewId == R.id.tabMarketChart) {
            iconRes = R.drawable.ic_nav_chart;
        } else if (viewId == R.id.tabAccountStats) {
            iconRes = R.drawable.ic_nav_account;
        } else if (viewId == R.id.tabAccountPosition) {
            iconRes = R.drawable.ic_nav_account;
        } else if (viewId == R.id.tabSettings) {
            iconRes = R.drawable.ic_nav_settings;
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
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setTextColor(selected ? palette.primary : palette.textSecondary);
        button.setTypeface(null, android.graphics.Typeface.NORMAL);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        button.setPaintFlags(selected
                ? (button.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG)
                : (button.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
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
            card.setStrokeWidth(0);
            card.setCardBackgroundColor(palette.surfaceEnd);
        } else if (view instanceof TextInputLayout) {
            TextInputLayout inputLayout = (TextInputLayout) view;
            inputLayout.setBoxBackgroundColor(palette.control);
            inputLayout.setBoxStrokeColor(palette.stroke);
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
}
