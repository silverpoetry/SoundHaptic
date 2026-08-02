package com.weicheng.soundhaptic;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.weicheng.soundhaptic.databinding.ActivityMainBinding;
import com.weicheng.soundhaptic.ui.HapticPreviewController;
import com.weicheng.soundhaptic.ui.HapticSettingsBinder;
import com.weicheng.soundhaptic.ui.ModuleConnectionController;

import io.github.libxposed.service.XposedService;

/** Material companion UI. Module behavior remains in the hook and audio layers. */
public final class MainActivity extends AppCompatActivity
        implements SoundHapticApp.ServiceStateListener {
    private ActivityMainBinding binding;
    private PulsePlayer pulsePlayer;
    private HapticPreviewController previewController;
    private HapticSettingsBinder settingsBinder;
    private ModuleConnectionController connectionController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyWindowInsets();

        binding.toolbar.setSubtitle(getString(R.string.version_format, versionName()));
        pulsePlayer = new PulsePlayer(this);
        settingsBinder = new HapticSettingsBinder(binding);
        previewController = new HapticPreviewController(pulsePlayer, settingsBinder::snapshot);
        connectionController = new ModuleConnectionController(
                this,
                binding.connectionStatusChip,
                binding.repairScopeButton,
                (preferences, errorMessage) -> {
                    if (preferences != null) {
                        settingsBinder.bindPreferences(preferences);
                    } else {
                        settingsBinder.showError(errorMessage);
                    }
                });

        binding.previewButton.setOnClickListener(view -> previewController.playCurrent());
        binding.sweepButton.setOnClickListener(view -> previewController.startSweep());
        binding.stopButton.setOnClickListener(view -> previewController.stop());
        binding.tuningHeader.setOnClickListener(view -> toggleTuning());

        if (settingsBinder.applyPreviewIntent(getIntent())) {
            binding.getRoot().postDelayed(previewController::playCurrent, 450L);
        }
    }

    private void toggleTuning() {
        boolean expand = binding.tuningGroup.getVisibility() != View.VISIBLE;
        AutoTransition transition = new AutoTransition();
        transition.setDuration(220L);
        TransitionManager.beginDelayedTransition(binding.contentColumn, transition);
        binding.tuningGroup.setVisibility(expand ? View.VISIBLE : View.GONE);
        binding.tuningChevron.animate().rotation(expand ? 90f : 0f).setDuration(220L).start();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            binding.appBar.setPadding(bars.left, bars.top, bars.right, 0);
            binding.contentScroll.setPadding(bars.left, 0, bars.right, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.root);
    }

    @SuppressWarnings("deprecation")
    private String versionName() {
        try {
            String value = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int suffix = value == null ? -1 : value.indexOf('-');
            return suffix > 0 ? value.substring(0, suffix) : value;
        } catch (Exception ignored) {
            return "1.6.0";
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SoundHapticApp.addServiceStateListener(this);
    }

    @Override
    protected void onStop() {
        SoundHapticApp.removeServiceStateListener(this);
        super.onStop();
    }

    @Override
    public void onServiceStateChanged(XposedService service) {
        if (connectionController != null) {
            connectionController.onServiceStateChanged(service);
        }
    }

    @Override
    protected void onDestroy() {
        if (previewController != null) {
            previewController.close();
        }
        if (pulsePlayer != null) {
            pulsePlayer.close();
        }
        binding = null;
        super.onDestroy();
    }
}
