package com.weicheng.soundhaptic.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.slider.Slider;
import com.weicheng.soundhaptic.R;

/** Reusable Material slider row with a stable integer domain and formatted value label. */
public final class SettingSliderView extends LinearLayout {
    public interface Formatter {
        String format(int value);
    }

    private final TextView titleView;
    private final TextView valueView;
    private final Slider slider;
    private Formatter formatter = String::valueOf;
    private Runnable valueCommitted;

    public SettingSliderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        int verticalPadding = dp(6);
        setPadding(0, verticalPadding, 0, verticalPadding);
        LayoutInflater.from(context).inflate(R.layout.view_setting_slider, this, true);
        titleView = findViewById(R.id.setting_title);
        valueView = findViewById(R.id.setting_value);
        slider = findViewById(R.id.setting_slider);
        slider.addOnChangeListener((view, value, fromUser) -> updateValueLabel());
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider slider) { }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                if (valueCommitted != null) {
                    valueCommitted.run();
                }
            }
        });
    }

    public void configure(String title, int min, int max, int initial, Formatter valueFormatter) {
        titleView.setText(title);
        slider.setContentDescription(title);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        // Integer values are rounded at the boundary. A continuous Material track avoids
        // hundreds of dense tick marks on ranges such as 120–350 Hz.
        slider.setStepSize(0f);
        formatter = valueFormatter == null ? String::valueOf : valueFormatter;
        setValue(initial);
    }

    public void setOnValueCommitted(Runnable listener) {
        valueCommitted = listener;
    }

    public int getValue() {
        return Math.round(slider.getValue());
    }

    public void setValue(int value) {
        float safe = Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), value));
        slider.setValue(safe);
        updateValueLabel();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        titleView.setEnabled(enabled);
        valueView.setEnabled(enabled);
        slider.setEnabled(enabled);
    }

    private void updateValueLabel() {
        valueView.setText(formatter.format(getValue()));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
