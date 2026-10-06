package com.douyin.clearmode;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.libxposed.service.XposedService;

/**
 * 清爽模式模块的配置 UI。
 *
 * <p>通过 {@link App#getService()} 获取 {@link XposedService}，再经
 * {@code getRemotePreferences("default")} 写入配置，Hook 进程通过同名 Remote Preferences 读取。
 *
 * <p>只依赖核心且稳定存在的 {@code getRemotePreferences(String)} 方法；框架信息、scope、
 * 热重载等一律不在 App 侧强依赖具体 service API，避免因签名差异导致整个模块无法编译。
 * 模块名 / 作用域 / 热重载由 LSPosed 管理器负责。
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "default";

    private MaterialSwitch switchEnable;
    private MaterialSwitch switchClearOnPlay;
    private MaterialSwitch switchLog;
    private TextView status;

    private XposedService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchEnable = findViewById(R.id.switch_enable);
        switchClearOnPlay = findViewById(R.id.switch_clear_on_play);
        switchLog = findViewById(R.id.switch_log);
        MaterialButton btnSave = findViewById(R.id.btn_save);
        MaterialButton btnReload = findViewById(R.id.btn_reload);
        status = findViewById(R.id.status);

        loadPrefs();
        btnSave.setOnClickListener(v -> savePrefs());
        btnReload.setOnClickListener(v -> status.setText(
                getString(R.string.hint_gesture) + "\n（热重载请到 LSPosed 管理器触发）"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        service = App.getService();
        loadPrefs();
    }

    private void loadPrefs() {
        if (service == null) {
            switchEnable.setChecked(true);
            switchClearOnPlay.setChecked(true);
            switchLog.setChecked(false);
            status.setText(getString(R.string.status_service) + ": "
                    + getString(R.string.status_not_bound));
            return;
        }
        SharedPreferences prefs = service.getRemotePreferences(PREFS_NAME);
        switchEnable.setChecked(prefs.getBoolean(ClearModePrefs.KEY_ENABLE, true));
        switchClearOnPlay.setChecked(prefs.getBoolean(ClearModePrefs.KEY_CLEAR_ON_PLAY, true));
        switchLog.setChecked(prefs.getBoolean(ClearModePrefs.KEY_LOG, false));
        status.setText(getString(R.string.status_service) + ": 已连接");
    }

    private void savePrefs() {
        if (service == null) {
            status.setText(getString(R.string.status_not_bound));
            return;
        }
        SharedPreferences prefs = service.getRemotePreferences(PREFS_NAME);
        prefs.edit()
                .putBoolean(ClearModePrefs.KEY_ENABLE, switchEnable.isChecked())
                .putBoolean(ClearModePrefs.KEY_CLEAR_ON_PLAY, switchClearOnPlay.isChecked())
                .putBoolean(ClearModePrefs.KEY_LOG, switchLog.isChecked())
                .apply();
        status.setText(getString(R.string.status_service) + ": 已连接（已保存）");
    }
}
