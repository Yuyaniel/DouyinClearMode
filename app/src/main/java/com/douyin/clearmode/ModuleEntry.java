package com.douyin.clearmode;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 抖音「清爽模式」模块 Hook 入口。
 *
 * <p>技术要点（按 LSPosed-Mod-Dev skill 规范）：
 * <ul>
 *   <li>目标包名 {@link #TARGET_PACKAGE}（com.ss.android.ugc.awemf）。</li>
 *   <li>目标进程为默认进程（与包名一致），需判断 processName 与 packageName。</li>
 *   <li>使用 {@code PackageReadyParam.getClassLoader()} 解析目标类，不用模块自身 ClassLoader。</li>
 *   <li>Hook：MainActivity.dispatchTouchEvent（四宫格长按手势）、OnUIPlayListener（播放状态）。</li>
 *   <li>作用域固定为抖音主包，不 Hook system_server。</li>
 * </ul>
 */
public final class ModuleEntry extends XposedModule {

    private static final String TAG = "ClearMode";
    private static final String TARGET_PACKAGE = "com.ss.android.ugc.awemf";

    /** 左上角宫格长按手势参数。 */
    private static final float TOP_LEFT_QUARTER_X = 0.25f;
    private static final float TOP_LEFT_QUARTER_Y = 0.25f;
    private static final long LONG_PRESS_MS = 600L;

    private volatile boolean installed;
    /** 播放回调对象不是 Activity，缓存最近一次 DecorView 作为回退根。 */
    private static volatile java.lang.ref.WeakReference<View> sLastRoot;

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "event=module_loaded process=" + param.getProcessName()
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName());
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        installHooks(param.getClassLoader());
    }

    private synchronized void installHooks(ClassLoader classLoader) {
        if (installed) {
            log(Log.INFO, TAG, "event=install_skipped reason=already_installed");
            return;
        }
        try {
            // 1) 手势层：Hook MainActivity.dispatchTouchEvent
            hookGesture(classLoader);
            // 2) 播放状态：Hook OnUIPlayListener 的 onPlaying / onPlayPause
            hookPlayState(classLoader);

            installed = true;
            log(Log.INFO, TAG, "event=hook_registered");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "event=install_failed", t);
        }
    }

    /**
     * Hook 主页宿主 Activity 的手势，识别左上角宫格长按进入/退出清爽模式。
     */
    private void hookGesture(ClassLoader classLoader) throws Exception {
        Class<?> mainActivity = classLoader.loadClass(
                "com.ss.android.ugc.aweme.main.MainActivity");
        var dispatch = mainActivity.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        dispatch.setAccessible(true);

        // 记录上一次 down 事件的按下时间与坐标
        final long[] downTime = {0L};
        final float[] downX = {0f};
        final float[] downY = {0f};
        final boolean[] longPressHandled = {false};

        hook(dispatch)
                .setId("clear_mode_gesture")
                .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                .intercept(chain -> {
                    Object thisObj = chain.getThisObject();
                    MotionEvent event = (MotionEvent) chain.getArg(0);
                    if (thisObj instanceof Activity) {
                        Activity activity = (Activity) thisObj;
                        if (event != null) {
                            handleTouch(activity, event, downTime, downX, downY, longPressHandled);
                        }
                    }
                    return chain.proceed();
                });

        log(Log.INFO, TAG, "event=hook_gesture_registered class=MainActivity#dispatchTouchEvent");
    }

    private static void handleTouch(Activity activity, MotionEvent event,
                                    long[] downTime, float[] downX, float[] downY,
                                    boolean[] longPressHandled) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downTime[0] = event.getDownTime();
            downX[0] = event.getX();
            downY[0] = event.getY();
            longPressHandled[0] = false;
        } else if (action == MotionEvent.ACTION_UP) {
            long now = event.getEventTime();
            float x = event.getX();
            float y = event.getY();
            long elapsed = now - downTime[0];
            boolean inTopLeft = isTopLeft(activity, downX[0], downY[0], x, y);
            if (inTopLeft && elapsed >= LONG_PRESS_MS && !longPressHandled[0]) {
                longPressHandled[0] = true;
                toggle(activity);
            }
        }
    }

    /**
     * 判断按下与抬起是否都落在屏幕左上角 1/4 宫格内。
     */
    private static boolean isTopLeft(Activity activity, float downX, float downY,
                                     float upX, float upY) {
        float w = activity.getResources().getDisplayMetrics().widthPixels;
        float h = activity.getResources().getDisplayMetrics().heightPixels;
        float maxX = w * TOP_LEFT_QUARTER_X;
        float maxY = h * TOP_LEFT_QUARTER_Y;
        return downX <= maxX && downY <= maxY
                && upX <= maxX && upY <= maxY;
    }

    private static void toggle(Activity activity) {
        View root = activity.getWindow().getDecorView();
        sLastRoot = new java.lang.ref.WeakReference<>(root);
        if (ClearModeController.isClearMode()) {
            ClearModeController.exitClear(root);
        } else {
            ClearModeController.enterClear(root);
        }
    }

    /**
     * Hook 播放状态，实现「播放隐藏控件 / 暂停显示控件」。
     */
    private void hookPlayState(ClassLoader classLoader) throws Exception {
        Class<?> listener = classLoader.loadClass(
                "com.ss.android.ugc.aweme.player.sdk.api.OnUIPlayListener");

        // onPlaying(String) → 播放中
        try {
            var onPlaying = listener.getDeclaredMethod("onPlaying", String.class);
            onPlaying.setAccessible(true);
            hook(onPlaying)
                    .setId("clear_mode_on_playing")
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(chain -> {
                        View root = currentRoot(chain.getThisObject());
                        ClearModeController.onPlaying(root);
                        return chain.proceed();
                    });
        } catch (NoSuchMethodException e) {
            log(Log.WARN, TAG, "event=method_not_found method=onPlaying", e);
        }

        // onPlayPause(String) → 暂停
        try {
            var onPlayPause = listener.getDeclaredMethod("onPlayPause", String.class);
            onPlayPause.setAccessible(true);
            hook(onPlayPause)
                    .setId("clear_mode_on_play_pause")
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(chain -> {
                        View root = currentRoot(chain.getThisObject());
                        ClearModeController.onPause(root);
                        return chain.proceed();
                    });
        } catch (NoSuchMethodException e) {
            log(Log.WARN, TAG, "event=method_not_found method=onPlayPause", e);
        }

        log(Log.INFO, TAG, "event=hook_play_state_registered class=OnUIPlayListener");
    }

    /**
     * 从监听器对象回溯到其宿主 Activity，取 DecorView 作为控件定位根。
     */
    private static View currentRoot(Object owner) {
        if (owner instanceof Activity) {
            View decor = ((Activity) owner).getWindow().getDecorView();
            sLastRoot = new java.lang.ref.WeakReference<>(decor);
            return decor;
        }
        View cached = sLastRoot == null ? null : sLastRoot.get();
        return cached;
    }
}
