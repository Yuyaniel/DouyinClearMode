package com.douyin.clearmode;

import android.content.SharedPreferences;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 清爽模式配置读取（Hook 进程侧）。
 *
 * <p>Hook 进程通过 {@code getRemotePreferences("default")} 读取模块 UI 写入的配置。
 * 为避免每次回调都访问 Binder，这里在模块加载时缓存配置，并在偏好变更时刷新缓存。
 * Remote Preferences 读取失败时回退到内置默认值。
 */
public final class ClearModePrefs {

    private static final String PREFS_NAME = "default";

    /** 配置 key（与模块 UI 写入的 key 一致）。 */
    public static final String KEY_ENABLE = "enable";
    public static final String KEY_CLEAR_ON_PLAY = "clear_on_play";
    public static final String KEY_LOG = "log_enabled";

    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();

    private ClearModePrefs() {
    }

    /** 初始化缓存。首次读取失败时写入默认值。 */
    public static void init(SharedPreferences prefs) {
        if (prefs == null) {
            return;
        }
        put(KEY_ENABLE, prefs.getBoolean(KEY_ENABLE, true));
        put(KEY_CLEAR_ON_PLAY, prefs.getBoolean(KEY_CLEAR_ON_PLAY, true));
        put(KEY_LOG, prefs.getBoolean(KEY_LOG, false));
    }

    /** 订阅偏好变更并刷新缓存。 */
    public static void attachListener(SharedPreferences prefs) {
        if (prefs == null) {
            return;
        }
        prefs.registerOnSharedPreferenceChangeListener(ClearModePrefs::refresh);
    }

    private static void refresh(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) {
            return;
        }
        switch (key) {
            case KEY_ENABLE:
                put(KEY_ENABLE, prefs.getBoolean(KEY_ENABLE, true));
                break;
            case KEY_CLEAR_ON_PLAY:
                put(KEY_CLEAR_ON_PLAY, prefs.getBoolean(KEY_CLEAR_ON_PLAY, true));
                break;
            case KEY_LOG:
                put(KEY_LOG, prefs.getBoolean(KEY_LOG, false));
                break;
            default:
                break;
        }
    }

    private static void put(String key, Object value) {
        CACHE.put(key, value);
    }

    /** 模块总开关。 */
    public static boolean enabled() {
        return bool(KEY_ENABLE, true);
    }

    /** 播放时是否隐藏控件。 */
    public static boolean clearOnPlay() {
        return bool(KEY_CLEAR_ON_PLAY, true);
    }

    /** 是否输出调试日志。 */
    public static boolean logEnabled() {
        return bool(KEY_LOG, false);
    }

    private static boolean bool(String key, boolean fallback) {
        Object v = CACHE.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return fallback;
    }
}
