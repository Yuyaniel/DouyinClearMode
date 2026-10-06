package com.douyin.clearmode;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * 清爽模式核心控制器（Hook 进程侧）。
 *
 * <p>负责记录当前是否处于「清爽模式」，并对主页视频播放页的叠加控件容器执行显隐切换。
 *
 * <p>控件定位策略：宿主 APK 版本混淆、字段名不稳定，本模块采用「视图树遍历 + 特征匹配」
 * 定位播放页的叠加控件容器，而非绑定具体混淆字段名。已确认的确定性锚点
 * （MainActivity / OnUIPlayListener）负责触发，View 定位负责执行。
 */
public final class ClearModeController {

    private static final String TAG = "ClearMode";

    /** 清爽模式状态。 */
    private static volatile boolean sClearMode = false;

    private ClearModeController() {
    }

    /** 进入清爽模式（播放时隐藏控件）。 */
    public static void enterClear(View root) {
        sClearMode = true;
        applyClear(root, true);
        Log.i(TAG, "event=enter_clear");
    }

    /** 退出清爽模式（显示控件）。 */
    public static void exitClear(View root) {
        sClearMode = false;
        applyClear(root, false);
        Log.i(TAG, "event=exit_clear");
    }

    /** 播放中 → 若处于清爽模式且开启隐藏，则隐藏控件。 */
    public static void onPlaying(View root) {
        if (sClearMode && ClearModePrefs.clearOnPlay()) {
            applyClear(root, true);
        }
    }

    /** 暂停 → 显示控件。 */
    public static void onPause(View root) {
        applyClear(root, false);
    }

    public static boolean isClearMode() {
        return sClearMode;
    }

    /**
     * 对「叠加控件容器」执行显隐。
     */
    private static void applyClear(View root, boolean hide) {
        if (root == null) {
            return;
        }
        View candidate = findOverlay(root);
        if (candidate == null) {
            Log.i(TAG, "event=no_overlay_found hide=" + hide);
            return;
        }
        int target = hide ? View.GONE : View.VISIBLE;
        if (candidate.getVisibility() != target) {
            candidate.setVisibility(target);
            Log.i(TAG, "event=overlay_visibility hide=" + hide
                    + " target=" + candidate.getClass().getName());
        }
    }

    /**
     * 在视图树中定位播放页的「叠加控件容器」。
     *
     * <p>启发式匹配：叠加控件通常位于顶部/底部、尺寸较大、覆盖在视频上层。
     * 为避免误伤视频本体，宁可漏判（返回 null）也不误判。
     */
    private static View findOverlay(View root) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) root;
        View best = null;
        int bestScore = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (isLikelyVideoSurface(child)) {
                continue;
            }
            int score = scoreOverlay(child);
            if (score > bestScore) {
                bestScore = score;
                best = child;
            }
        }
        return best;
    }

    private static boolean isLikelyVideoSurface(View v) {
        String name = v.getClass().getName();
        return name.contains("SurfaceView")
                || name.contains("TextureView")
                || name.contains("KeepSurfaceTextureView")
                || name.contains("Video");
    }

    /**
     * 给叠加容器打分。规则：全屏且偏上/偏下的 ViewGroup 得分高；尺寸过小排除；视频表面排除。
     */
    private static int scoreOverlay(View v) {
        int w = v.getWidth();
        int h = v.getHeight();
        if (w <= 0 || h <= 0) {
            return 0;
        }
        int score = 0;
        if (v instanceof ViewGroup) {
            score += 8;
        }
        String name = v.getClass().getName();
        if (name.contains("Bar") || name.contains("Bottom") || name.contains("Top")
                || name.contains("Toolbar") || name.contains("Title")) {
            score += 6;
        }
        if (name.contains("Music") || name.contains("Marquee")) {
            score += 4;
        }
        if (v.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) v.getParent();
            int pw = parent.getWidth();
            int ph = parent.getHeight();
            if (pw > 0 && w >= pw * 0.8f) {
                score += 3;
            }
            if (ph > 0 && h >= ph * 0.25f) {
                score += 2;
            }
        }
        return score;
    }
}
