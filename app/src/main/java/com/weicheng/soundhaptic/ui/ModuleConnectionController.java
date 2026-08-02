package com.weicheng.soundhaptic.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.weicheng.soundhaptic.HapticSettings;
import com.weicheng.soundhaptic.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.libxposed.service.XposedService;

/** Owns LSPosed connection status and scope repair, independent from the settings form. */
public final class ModuleConnectionController {
    private static final String OBSOLETE_SCREENSHOT_SCOPE = "com.miui.screenshot";
    private static final List<String> REQUIRED_SCOPES = Arrays.asList(
            "com.android.systemui",
            "com.miui.home",
            "com.android.settings",
            "miui.systemui.plugin");

    public interface PreferencesListener {
        void onPreferences(SharedPreferences preferences, String errorMessage);
    }

    private final Activity activity;
    private final int mutedColor;
    private final Chip statusChip;
    private final Button repairButton;
    private final PreferencesListener preferencesListener;
    private XposedService service;

    public ModuleConnectionController(Activity activity, Chip statusChip,
            Button repairButton,
            PreferencesListener preferencesListener) {
        this.activity = activity;
        this.mutedColor = MaterialColors.getColor(statusChip,
                com.google.android.material.R.attr.colorOutline);
        this.statusChip = statusChip;
        this.repairButton = repairButton;
        this.preferencesListener = preferencesListener;
        repairButton.setOnClickListener(view -> requestScopes());
    }

    public void onServiceStateChanged(XposedService newService) {
        service = newService;
        activity.runOnUiThread(() -> bindService(newService));
    }

    private void bindService(XposedService newService) {
        if (newService == null) {
            preferencesListener.onPreferences(null, "设置暂未连接");
            updateStatus("LSPosed 未连接", "请在管理器中启用模块", mutedColor, false);
            return;
        }
        removeObsoleteScopes(newService);
        try {
            SharedPreferences preferences =
                    newService.getRemotePreferences(HapticSettings.GROUP);
            preferencesListener.onPreferences(preferences, null);
        } catch (RuntimeException error) {
            preferencesListener.onPreferences(null, "配置读取失败");
        }

        boolean complete = newService.getScope().containsAll(REQUIRED_SCOPES);
        if (complete) {
            updateStatus("模块已连接", "作用域完整", successColor(), false);
        } else {
            updateStatus("作用域不完整", "部分系统触感可能无法生效", warningColor(), true);
        }
    }

    private static void removeObsoleteScopes(XposedService service) {
        try {
            if (service.getScope().contains(OBSOLETE_SCREENSHOT_SCOPE)) {
                service.removeScope(Arrays.asList(OBSOLETE_SCREENSHOT_SCOPE));
            }
        } catch (RuntimeException ignored) {
            // Scope cleanup must never prevent settings from connecting.
        }
    }

    private void requestScopes() {
        XposedService current = service;
        if (current == null) {
            updateStatus("LSPosed 未连接", "无法申请作用域", mutedColor, false);
            return;
        }
        List<String> missing = missingScopes(current.getScope());
        if (missing.isEmpty()) {
            updateStatus("模块已连接", "作用域完整", successColor(), false);
            return;
        }
        statusChip.setText(R.string.scope_waiting_confirmation);
        current.requestScope(missing, new XposedService.OnScopeEventListener() {
            @Override
            public void onScopeRequestApproved(List<String> approved) {
                activity.runOnUiThread(() -> updateStatus(
                        "作用域已批准", "重启系统界面后生效", successColor(), false));
            }

            @Override
            public void onScopeRequestFailed(String message) {
                activity.runOnUiThread(() -> updateStatus(
                        "作用域申请失败", message, errorColor(), true));
            }
        });
    }

    private void updateStatus(String title, String summary, int color, boolean showRepair) {
        statusChip.setText(activity.getString(R.string.module_status_format, title, summary));
        statusChip.setChipIconTint(ColorStateList.valueOf(color));
        repairButton.setVisibility(showRepair ? View.VISIBLE : View.GONE);
    }

    private static List<String> missingScopes(List<String> current) {
        ArrayList<String> missing = new ArrayList<>();
        for (String required : REQUIRED_SCOPES) {
            if (!current.contains(required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    private int successColor() {
        return MaterialColors.getColor(statusChip,
                com.google.android.material.R.attr.colorTertiary);
    }

    private int warningColor() {
        return MaterialColors.getColor(statusChip,
                com.google.android.material.R.attr.colorSecondary);
    }

    private int errorColor() {
        return MaterialColors.getColor(statusChip,
                android.R.attr.colorError);
    }
}
