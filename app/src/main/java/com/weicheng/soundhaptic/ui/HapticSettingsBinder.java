package com.weicheng.soundhaptic.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;

import com.weicheng.soundhaptic.HapticSettings;
import com.weicheng.soundhaptic.R;
import com.weicheng.soundhaptic.audio.HapticShape;
import com.weicheng.soundhaptic.databinding.ActivityMainBinding;

import java.util.Locale;

/** Binds the Material settings form to the API102 remote-preference contract. */
public final class HapticSettingsBinder {
    private final ActivityMainBinding binding;
    private SharedPreferences preferences;
    private HapticShape selectedShape = HapticSettings.DEFAULT_SHAPE;
    private boolean loading;

    public HapticSettingsBinder(ActivityMainBinding binding) {
        this.binding = binding;
        configureViews();
        bindListeners();
        apply(HapticSettings.defaults());
    }

    public void bindPreferences(SharedPreferences newPreferences) {
        preferences = newPreferences;
        apply(HapticSettings.read(newPreferences));
        binding.settingsStatus.setText(R.string.settings_auto_apply);
    }

    public void showError(String message) {
        binding.settingsStatus.setText(message == null
                ? binding.getRoot().getContext().getString(R.string.settings_waiting)
                : message);
    }

    public HapticSettings.Snapshot snapshot() {
        return new HapticSettings.Snapshot(
                binding.enabledSetting.isChecked(),
                binding.volumeSetting.getValue(),
                binding.frequencySetting.getValue(),
                binding.durationSetting.getValue(),
                binding.strengthSetting.getValue(),
                selectedShape,
                binding.speakerSetting.isChecked());
    }

    public boolean applyPreviewIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra("preview", false)) {
            return false;
        }
        HapticSettings.Snapshot current = snapshot();
        loading = true;
        binding.frequencySetting.setValue(intent.getIntExtra(
                "frequency", current.frequencyHz));
        binding.durationSetting.setValue(intent.getIntExtra(
                "duration", current.referenceDurationMs));
        binding.strengthSetting.setValue(intent.getIntExtra(
                "strength", current.referenceStrengthPercent));
        binding.volumeSetting.setValue(intent.getIntExtra(
                "volume", current.volumePercent));
        loading = false;
        return true;
    }

    private void configureViews() {
        binding.enabledSetting.configure(
                text(R.string.enabled_title), text(R.string.enabled_summary),
                HapticSettings.DEFAULT_ENABLED);
        binding.volumeSetting.configure(
                text(R.string.volume_title), HapticSettings.MIN_VOLUME,
                HapticSettings.MAX_VOLUME, HapticSettings.DEFAULT_VOLUME,
                value -> value == 0 ? text(R.string.volume_muted)
                        : String.format(Locale.CHINA, "%d%%", value));
        binding.frequencySetting.configure(
                text(R.string.frequency_title), HapticSettings.MIN_FREQUENCY,
                HapticSettings.MAX_FREQUENCY, HapticSettings.DEFAULT_FREQUENCY,
                value -> String.format(Locale.CHINA, "%d Hz", value));
        binding.durationSetting.configure(
                text(R.string.duration_title), HapticSettings.MIN_DURATION,
                HapticSettings.MAX_DURATION, HapticSettings.DEFAULT_DURATION,
                value -> String.format(Locale.CHINA, "%d ms", value));
        binding.strengthSetting.configure(
                text(R.string.strength_title), HapticSettings.MIN_STRENGTH,
                HapticSettings.MAX_STRENGTH, HapticSettings.DEFAULT_STRENGTH,
                value -> String.format(Locale.CHINA, "%d%%", value));
        binding.speakerSetting.configure(
                text(R.string.speaker_title), text(R.string.speaker_summary),
                HapticSettings.DEFAULT_FORCE_SPEAKER);

        HapticShape[] shapes = HapticShape.values();
        String[] labels = new String[shapes.length];
        for (int index = 0; index < shapes.length; index++) {
            labels[index] = shapes[index].label;
        }
        binding.shapeInput.setAdapter(new ArrayAdapter<>(binding.getRoot().getContext(),
                android.R.layout.simple_list_item_1, labels));
        binding.shapeInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedShape = shapes[position];
            binding.shapeDescription.setText(selectedShape.description);
            persist();
        });
    }

    private void bindListeners() {
        binding.enabledSetting.setOnCheckedChanged(() -> {
            updateControlsEnabled(binding.enabledSetting.isChecked());
            persist();
        });
        binding.speakerSetting.setOnCheckedChanged(this::persist);
        binding.volumeSetting.setOnValueCommitted(this::persist);
        binding.frequencySetting.setOnValueCommitted(this::persist);
        binding.durationSetting.setOnValueCommitted(this::persist);
        binding.strengthSetting.setOnValueCommitted(this::persist);
    }

    private void apply(HapticSettings.Snapshot value) {
        loading = true;
        binding.enabledSetting.setChecked(value.enabled);
        binding.volumeSetting.setValue(value.volumePercent);
        binding.frequencySetting.setValue(value.frequencyHz);
        binding.durationSetting.setValue(value.referenceDurationMs);
        binding.strengthSetting.setValue(value.referenceStrengthPercent);
        selectedShape = value.shape;
        binding.shapeInput.setText(selectedShape.label, false);
        binding.shapeDescription.setText(selectedShape.description);
        binding.speakerSetting.setChecked(value.forceSpeaker);
        updateControlsEnabled(value.enabled);
        updateTuningSummary();
        loading = false;
    }

    private void updateControlsEnabled(boolean enabled) {
        binding.volumeSetting.setEnabled(enabled);
        binding.frequencySetting.setEnabled(enabled);
        binding.durationSetting.setEnabled(enabled);
        binding.strengthSetting.setEnabled(enabled);
        binding.shapeInputLayout.setEnabled(enabled);
        binding.speakerSetting.setEnabled(enabled);
        binding.tuningHeader.setEnabled(enabled);
        binding.tuningHeader.setAlpha(enabled ? 1f : 0.38f);
    }

    private void persist() {
        if (loading) {
            return;
        }
        SharedPreferences current = preferences;
        if (current == null) {
            binding.settingsStatus.setText(R.string.settings_waiting);
            return;
        }
        boolean committed = HapticSettings.write(current, snapshot());
        updateTuningSummary();
        binding.settingsStatus.setText(committed
                ? R.string.settings_applied : R.string.settings_save_failed);
    }

    private void updateTuningSummary() {
        binding.tuningSummary.setText(binding.getRoot().getContext().getString(
                R.string.tuning_summary_format,
                selectedShape.label,
                binding.frequencySetting.getValue(),
                binding.durationSetting.getValue()));
    }

    private String text(int resourceId) {
        return binding.getRoot().getContext().getString(resourceId);
    }
}
