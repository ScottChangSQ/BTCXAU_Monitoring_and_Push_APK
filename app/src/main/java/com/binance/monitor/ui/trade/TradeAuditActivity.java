/*
 * 交易追踪页，负责展示最近交易列表、按 traceId 查询和时间线复制。
 * 与 TradeAuditViewModel、TradeReplayFormatter、MarketChartTradeDialogCoordinator 协同工作。
 */
package com.binance.monitor.ui.trade;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ActivityTradeAuditBinding;
import com.binance.monitor.ui.theme.UiPaletteManager;

import java.util.List;

public class TradeAuditActivity extends AppCompatActivity {
    public static final String EXTRA_TRACE_ID = "extra_trace_id";

    private ActivityTradeAuditBinding binding;
    private TradeAuditViewModel viewModel;
    private String latestCopyText = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTradeAuditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(TradeAuditViewModel.class);
        setupActions();
        observeViewModel();
        applyPaletteStyles();
        String initialTraceId = readInitialTraceId();
        if (!initialTraceId.isEmpty()) {
            binding.etTradeAuditLookup.setText(initialTraceId);
            viewModel.lookup(initialTraceId);
        }
        viewModel.refreshRecent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
    }

    public static void open(@NonNull Context context, @Nullable String traceId) {
        Intent intent = new Intent(context, TradeAuditActivity.class);
        intent.putExtra(EXTRA_TRACE_ID, traceId == null ? "" : traceId);
        if (!(context instanceof AppCompatActivity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private void setupActions() {
        binding.btnTradeAuditBack.setOnClickListener(v -> finish());
        binding.btnTradeAuditLookup.setOnClickListener(v -> viewModel.lookup(readLookupInput()));
        binding.btnTradeAuditCopy.setOnClickListener(v -> copyReplayText());
    }

    private void observeViewModel() {
        viewModel.getRecentEntries().observe(this, this::bindRecentEntries);
        viewModel.getReplayContent().observe(this, replay -> {
            if (replay == null) {
                return;
            }
            latestCopyText = replay.getCopyText();
            binding.tvTradeAuditDetailTitle.setText(replay.getDisplayTitle());
            binding.tvTradeAuditDetail.setText(replay.getSummaryText() + "\n\n" + replay.getTimelineText());
            binding.tvTradeAuditEmpty.setVisibility(android.view.View.GONE);
        });
        viewModel.getCurrentTraceId().observe(this, traceId -> {
            if (traceId != null && !traceId.trim().isEmpty()) {
                binding.etTradeAuditLookup.setText(traceId);
                binding.etTradeAuditLookup.setSelection(traceId.length());
            }
        });
    }

    private void bindRecentEntries(@Nullable List<TradeAuditEntry> entries) {
        binding.layoutTradeAuditRecentList.removeAllViews();
        if (entries == null || entries.isEmpty()) {
            binding.tvTradeAuditEmpty.setVisibility(android.view.View.VISIBLE);
            return;
        }
        binding.tvTradeAuditEmpty.setVisibility(android.view.View.GONE);
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        for (TradeAuditEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            TextView itemView = new TextView(this);
            itemView.setText(buildRecentItemText(entry));
            itemView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            itemView.setTextAppearance(this, R.style.TextAppearance_BinanceMonitor_BodyCompact);
            itemView.setPadding(dp(12), dp(10), dp(12), dp(10));
            itemView.setTextColor(palette.textPrimary);
            itemView.setBackground(UiPaletteManager.createListRowBackground(this, palette.surfaceEnd, palette.stroke));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (binding.layoutTradeAuditRecentList.getChildCount() > 0) {
                params.topMargin = dp(8);
            }
            itemView.setLayoutParams(params);
            itemView.setOnClickListener(v -> viewModel.lookup(entry.getTraceId()));
            binding.layoutTradeAuditRecentList.addView(itemView);
        }
    }

    @NonNull
    private String buildRecentItemText(@NonNull TradeAuditEntry entry) {
        return entry.getTraceId()
                + "\n"
                + (entry.getActionSummary().isEmpty() ? entry.getAction() : entry.getActionSummary())
                + "\n"
                + entry.getStage()
                + " | "
                + entry.getStatus();
    }

    private void copyReplayText() {
        if (latestCopyText.trim().isEmpty()) {
            Toast.makeText(this, R.string.trade_audit_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("trade_audit", latestCopyText));
            Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private String readInitialTraceId() {
        String value = getIntent().getStringExtra(EXTRA_TRACE_ID);
        return value == null ? "" : value.trim();
    }

    @NonNull
    private String readLookupInput() {
        return binding.etTradeAuditLookup.getText() == null
                ? ""
                : binding.etTradeAuditLookup.getText().toString().trim();
    }

    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        binding.btnTradeAuditBack.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.btnTradeAuditBack.setTextColor(palette.textPrimary);
        UiPaletteManager.styleInputField(binding.inputLayoutTradeAuditLookup, palette);
        UiPaletteManager.styleActionButton(
                binding.btnTradeAuditLookup,
                palette,
                palette.primarySoft,
                UiPaletteManager.controlSelectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnTradeAuditCopy,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        binding.tvTradeAuditTitle.setTextColor(palette.textPrimary);
        binding.tvTradeAuditSubtitle.setTextColor(palette.textSecondary);
        binding.tvTradeAuditRecentTitle.setTextColor(palette.textPrimary);
        binding.tvTradeAuditDetailTitle.setTextColor(palette.textPrimary);
        binding.tvTradeAuditDetail.setTextColor(palette.textPrimary);
        binding.tvTradeAuditEmpty.setTextColor(palette.textSecondary);
        binding.cardTradeAuditRecent.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardTradeAuditDetail.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
