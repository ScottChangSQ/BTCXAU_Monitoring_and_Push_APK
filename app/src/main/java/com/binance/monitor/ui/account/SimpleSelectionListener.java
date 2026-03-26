package com.binance.monitor.ui.account;

import android.view.View;
import android.widget.AdapterView;

public class SimpleSelectionListener implements AdapterView.OnItemSelectedListener {

    private final Runnable onChange;

    public SimpleSelectionListener(Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (onChange != null) {
            onChange.run();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
