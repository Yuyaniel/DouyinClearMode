package com.douyin.clearmode;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
/**
 * 清爽模式核心控制器（Hook 进程侧）。
 *
 * <p>负责记录「清爽模式」状态，并对播放页的叠加控件执行显隐切换。
 *
 * <p>定位策略：宿主混淆、字段名不稳定，采用「视图树递归 + 结构特征匹配」。
 *
 * <p><b>关键约束</b>：视频画面装在一个<b>全屏 ViewGroup</b> 里。早期版本只在根布局
 * 直接子级里按「ViewGroup + 大尺寸」打分选最优，结果选中视频容器本身，GONE 之后
 * 只剩 DecorView 灰色底（表现为全屏灰遮罩）。因此现在：
 * 包含视频表面的容器整棵跳过；接近全屏的容器视为内容层绝不隐藏；
 * 只接受贴边条状控件；只恢复本模块自己隐藏过的 View。
 */
public final class ClearModeController {
    private static final String TAG = "ClearMode";
    private static volatile boolean sClearMode = false;
    /** 仅恢复本模块隐藏过的 View，避免把本就该隐藏的控件误显示。 */
    private static final Map<View, Boolean> HIDDEN_BY_US =
            Collections.synchronizedMap(new WeakHashMap<View, Boolean>());
    /** 递归深度上限。 */
    private static final int MAX_DEPTH = 12;
    private ClearModeController() {
    }
    /** 进入清爽模式。 */
    public static void enterClear(View root) {
        sClearMode = true;
        applyClear(root, true);
        Log.i(TAG, "event=enter_clear");
    }
    /** 退出清爽模式。 */
    public static void exitClear(View root) {
        sClearMode = false;
        restoreAll();
        Log.i(TAG, "event=exit_clear");
    }
    /** 播放中。 */
    public static void onPlaying(View root) {
        if (sClearMode && ClearModePrefs.clearOnPlay()) {
            applyClear(root, true);
        }
    }
    /** 暂停。 */
    public static void onPause(View root) {
        restoreAll();
    }
    public static boolean isClearMode() {
        return sClearMode;
    }
    private static void applyClear(View root, boolean hide) {
        if (root == null) {
            return;
        }
        if (!hide) {
            restoreAll();
            return;
        }
        List<View> targets = findOverlays(root);
        if (targets.isEmpty()) {
            Log.i(TAG, "event=no_overlay_found");
            return;
        }
        int hidden = 0;
        for (View v : targets) {
            if (v.getVisibility() != View.GONE) {
                v.setVisibility(View.GONE);
                HIDDEN_BY_US.put(v, Boolean.TRUE);
                hidden++;
            }
        }
        Log.i(TAG, "event=overlay_hidden count=" + hidden);
    }
    private static void restoreAll() {
        List<View> snapshot;
        synchronized (HIDDEN_BY_US) {
            snapshot = new ArrayList<>(HIDDEN_BY_US.keySet());
            HIDDEN_BY_US.clear();
        }
        int restored = 0;
        for (View v : snapshot) {
            if (v != null && v.getVisibility() != View.VISIBLE) {
                v.setVisibility(View.VISIBLE);
                restored++;
            }
        }
        Log.i(TAG, "event=overlay_restored count=" + restored);
    }
    /**
     * 在视图树中收集「叠加控件」：贴边条状、不含视频表面、非内容层。
     */
    private static List<View> findOverlays(View root) {
        List<View> result = new ArrayList<>();
        collect(root, result, 0);
        return result;
    }
    private static void collect(View v, List<View> out, int depth) {
        if (v == null || depth > MAX_DEPTH || v.getVisibility() != View.VISIBLE) {
            return;
        }
        // 视频表面本身：跳过
        if (isVideoSurface(v)) {
            return;
        }
        // 整个子树里含视频表面 → 内容层，整棵跳过（关键防误伤）
        if (containsVideoSurface(v)) {
            return;
        }
        int w = v.getWidth();
        int h = v.getHeight();
        if (w > 0 && h > 0) {
            int ph = parentHeight(v);
            int pw = parentWidth(v);
            if (pw > 0 && ph > 0) {
                boolean nearFullWidth = w >= pw * 0.9f;
                boolean nearFullHeight = h >= ph * 0.9f;
                // 接近全屏 → 内容层，绝不隐藏
                if (nearFullWidth && nearFullHeight) {
                    return;
                }
                if (isEdgeBar(v, pw, ph, w, h) && isHideable(v)) {
                    out.add(v);
                    return;
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i), out, depth + 1);
            }
        }
    }
    /** 贴边条状：靠上 / 靠下 / 靠右，且只占一侧的窄条。 */
    private static boolean isEdgeBar(View v, int pw, int ph, int w, int h) {
        float top = v.getTop();
        float left = v.getLeft();
        float right = pw - (left + w);
        float bottom = ph - (top + h);
        float eps = Math.max(8f, ph * 0.02f);
        boolean atTop = top <= eps;
        boolean atBottom = bottom <= eps;
        boolean atRight = right <= eps;
        boolean isBar;
        if (atTop || atBottom) {
            isBar = h <= ph * 0.25f;
        } else if (atRight) {
            isBar = w <= pw * 0.25f;
        } else {
            return false;
        }
        return isBar && v instanceof ViewGroup;
    }
    /** 排除不应被隐藏的控件。 */
    private static boolean isHideable(View v) {
        String name = v.getClass().getName();
        // 播放器 / 广告等系统或宿主关键容器不动
        if (name.contains("SurfaceView") || name.contains("TextureView")) {
            return false;
        }
        return v.getAlpha() > 0.1f;
    }
    /** 视频渲染表面。 */
    private static boolean isVideoSurface(View v) {
        String name = v.getClass().getName();
        return name.contains("SurfaceView")
                || name.contains("TextureView")
                || name.contains("KeepSurfaceTextureView")
                || name.contains("Video");
    }
    /** 子树中是否含视频表面（判定内容层）。 */
    private static boolean containsVideoSurface(View v) {
        if (isVideoSurface(v)) {
            return true;
        }
        if (!(v instanceof ViewGroup)) {
            return false;
        }
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            if (containsVideoSurface(g.getChildAt(i))) {
                return true;
            }
        }
        return false;
    }
    private static int parentWidth(View v) {
        View p = (View) v.getParent();
        return p == null ? 0 : p.getWidth();
    }
    private static int parentHeight(View v) {
        View p = (View) v.getParent();
        return p == null ? 0 : p.getHeight();
    }
}