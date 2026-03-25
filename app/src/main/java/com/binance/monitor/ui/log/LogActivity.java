package com.binance.monitor.ui.log;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.data.model.AppLogEntry;
import com.binance.monitor.databinding.ActivityLogBinding;
import com.binance.monitor.ui.adapter.LogAdapter;
import com.binance.monitor.util.FormatUtils;

public class LogActivity extends AppCompatActivity {

    private ActivityLogBinding binding;
    private LogViewModel viewModel;
    private LogAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LogViewModel.class);
        adapter = new LogAdapter(new LogAdapter.Callback() {
            @Override
            public void onCopy(AppLogEntry entry) {
                copyToClipboard(entry);
            }
        });

        binding.recyclerLogs.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLogs.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        binding.btnDeleteSelected.setOnClickListener(v -> {
            viewModel.deleteSelected(adapter.getSelectedIds());
            adapter.clearSelection();
        });

        viewModel.getLogs().observe(this, logs -> {
            adapter.submitList(logs);
            binding.tvLogsEmpty.setVisibility(logs == null || logs.isEmpty()
                    ? android.view.View.VISIBLE
                    : android.view.View.GONE);
        });
    }

    private void copyToClipboard(AppLogEntry entry) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            String value = "[" + entry.getLevel() + "] "
                    + FormatUtils.formatDateTime(entry.getTimestamp())
                    + "\n"
                    + entry.getMessage();
            manager.setPrimaryClip(ClipData.newPlainText("log", value));
            Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show();
        }
    }
}
