package com.weicheng.soundhaptic.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.weicheng.soundhaptic.R;

/** Reusable Material switch row; the entire row is a single accessible click target. */
public final class SettingSwitchView extends LinearLayout {
    private final TextView titleView;
    private final TextView summaryView;
    private final MaterialSwitch toggle;

    public SettingSwitchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(56));
        setPadding(0, dp(6), 0, dp(6));
        LayoutInflater.from(context).inflate(R.layout.view_setting_switch, this, true);
        titleView = findViewById(R.id.setting_title);
        summaryView = findViewById(R.id.setting_summary);
        toggle = findViewById(R.id.setting_switch);
        setOnClickListener(view -> toggle.toggle());
    }

    public void configure(String title, String summary, boolean checked) {
        titleView.setText(title);
        summaryView.setText(summary);
        toggle.setContentDescription(title);
        toggle.setChecked(checked);
    }

    public boolean isChecked() {
        return toggle.isChecked();
    }

    public void setChecked(boolean checked) {
        toggle.setChecked(checked);
    }

    public void setOnCheckedChanged(Runnable listener) {
        toggle.setOnCheckedChangeListener((button, checked) -> listener.run());
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        titleView.setEnabled(enabled);
        summaryView.setEnabled(enabled);
        toggle.setEnabled(enabled);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
