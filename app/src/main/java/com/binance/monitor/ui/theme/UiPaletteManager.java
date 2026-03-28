package com.binance.monitor.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.core.content.ContextCompat;

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
                        String xauHex) {
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
        }
    }

    private static final Palette[] PALETTES = new Palette[]{
            new Palette(0, "默认主题（专业蓝）", "#F4F7FB", "#EAF1FB", "#FFFFFF", "#F8FAFC", "#D8E1EC", "#2563EB", "#DBEAFE", "#0284C7", "#1D4ED8"),
            new Palette(1, "森林主题", "#F2FBF7", "#E2F6EF", "#FFFFFF", "#F5FCF8", "#CFE7DD", "#0F766E", "#CCFBF1", "#0F766E", "#0891B2"),
            new Palette(2, "落日主题", "#FFF8F1", "#FFEEDC", "#FFFFFF", "#FFF9F3", "#F2D3B4", "#EA580C", "#FFEDD5", "#D97706", "#EA580C"),
            new Palette(3, "紫幕主题", "#F8F6FF", "#ECE7FF", "#FFFFFF", "#FAF8FF", "#D9D2F4", "#7C3AED", "#EDE9FE", "#6D28D9", "#8B5CF6"),
            new Palette(4, "玫瑰主题", "#FFF6FA", "#FCE7F3", "#FFFFFF", "#FFF8FB", "#EBC9DA", "#BE185D", "#FBCFE8", "#BE185D", "#7C3AED")
    };

    private UiPaletteManager() {
    }

    public static Palette resolve(Context context) {
        int stored = ConfigManager.getInstance(context).getColorPalette();
        if (stored < 0 || stored >= PALETTES.length) {
            stored = 0;
        }
        return PALETTES[stored];
    }

    public static String[] labels() {
        String[] labels = new String[PALETTES.length];
        for (int i = 0; i < PALETTES.length; i++) {
            labels[i] = PALETTES[i].label;
        }
        return labels;
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
                new int[]{palette.card, palette.primarySoft});
        drawable.setCornerRadius(dp(context, 14));
        drawable.setStroke(dp(context, 1), palette.stroke);
        return drawable;
    }

    public static GradientDrawable createFilledDrawable(Context context, int fillColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 10));
        drawable.setColor(fillColor);
        drawable.setStroke(dp(context, 1), fillColor);
        return drawable;
    }

    public static GradientDrawable createOutlinedDrawable(Context context, int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 10));
        drawable.setColor(fillColor);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static void applyPageTheme(View root, Palette palette) {
        if (root == null) {
            return;
        }
        root.setBackground(createPageBackground(root.getContext(), palette));
        applyRecursively(root, palette);
    }

    private static void applyRecursively(View view, Palette palette) {
        Context context = view.getContext();
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            card.setCardBackgroundColor(palette.card);
            card.setStrokeColor(palette.stroke);
        } else if (view instanceof TextInputLayout) {
            TextInputLayout inputLayout = (TextInputLayout) view;
            inputLayout.setBoxBackgroundColor(palette.control);
            inputLayout.setBoxStrokeColor(palette.stroke);
        } else if (view instanceof SeekBar) {
            SeekBar seekBar = (SeekBar) view;
            seekBar.setProgressTintList(ColorStateList.valueOf(palette.primary));
            seekBar.setThumbTintList(ColorStateList.valueOf(palette.primary));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(palette.stroke));
        } else if (view instanceof Button && !(view instanceof MaterialButton)) {
            Button button = (Button) view;
            if (button.getCurrentTextColor() != ContextCompat.getColor(context, R.color.white)) {
                button.setBackground(createOutlinedDrawable(context, palette.card, palette.stroke));
                button.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
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

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
