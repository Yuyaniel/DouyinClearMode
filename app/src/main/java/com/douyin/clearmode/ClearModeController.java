package com.douyin.clearmode;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
/**
 * 清爽模式核心控制器（Hook 进程侧）。
 *
 * <p><b>定位思路（v3）</b>：宿主经过 R8 混淆后，视频 View 的类名不再是
 * SurfaceView/TextureView，<b>按类名匹配必然失效</b>。因此改用 instanceof 判定。
 *
 * <p>播放页层级为「页面容器 → 视频层 + 控件层」，所以：
 * 用 instanceof 找到视频 View → 取其父容器 → 父容器下其余可见子节点即叠加控件。
 */
public final class ClearModeController {
    private static final String TAG = "ClearMode";
    private static volatile boolean sClearMode = false;
    private static final Map<View, Boolean> HIDDEN_BY_US =
            Collections.synchronizedMap(new WeakHashMap<View, Boolean>());
    /** 叠加控件最小尺寸。 */
    private static final int MIN_SIZE_PX = 32;
    private ClearModeController() {
    }
    public static void enterClear(View root) {
        sClearMode = true;
        applyClear(root, true);
        Log.i(TAG, "event=enter_clear");
    }
    public static void exitClear(View root) {
        sClearMode = false;
        restoreAll();
        Log.i(TAG, "event=exit_clear");
    }
    public static void onPlaying(View root) {
        if (sClearMode && ClearModePrefs.clearOnPlay()) {
            applyClear(root, true);
        }
    }
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
            Log.i(TAG, "event=no_overlay_found root=" + root.getClass().getName());
            return;
        }
        int hidden = 0;
        for (View v : targets) {
            if (v.getVisibility() == View.GONE) {
                continue;
            }
            v.setVisibility(View.GONE);
            HIDDEN_BY_US.put(v, Boolean.TRUE);
            hidden++;
            Log.i(TAG, "event=hide_view class=" + v.getClass().getName()
                    + " w=" + v.getWidth() + " h=" + v.getHeight());
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
     * 定位叠加控件：instanceof 找到视频 View，取其父容器下的其余可见子节点。
     */
    private static List<View> findOverlays(View root) {
        View video = findVideoView(root);
        if (video == null) {
            Log.i(TAG, "event=no_video_view");
            return Collections.emptyList();
        }
        ViewParent vp = video.getParent();
        if (!(vp instanceof ViewGroup)) {
            return Collections.emptyList();
        }
        ViewGroup parent = (ViewGroup) vp;
        Set<View> result = new LinkedHashSet<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View sibling = parent.getChildAt(i);
            if (sibling == video) {
                continue;
            }
            if (isAcceptable(sibling)) {
                result.add(sibling);
            }
        }
        Log.i(TAG, "event=video_found class=" + video.getClass().getName()
                + " parent=" + parent.getClass().getName()
                + " children=" + parent.getChildCount());
        return new ArrayList<>(result);
    }
    /** 可作为叠加控件：可见、够大、自身及子树不含视频 View。 */
    private static boolean isAcceptable(View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) {
            return false;
        }
        if (v.getWidth() < MIN_SIZE_PX || v.getHeight() < MIN_SIZE_PX) {
            return false;
        }
        // 绝不隐藏含视频的子树（防「画面全灰」）
        return !containsVideo(v);
    }
    /**
     * 递归找视频 View。instanceof 判定，兼容混淆后的类名。
     */
    private static View findVideoView(View root) {
        if (root == null) {
            return null;
        }
        if (isVideoView(root)) {
            return root;
        }
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup g = (ViewGroup) root;
        for (int i = 0; i < g.getChildCount(); i++) {
            View found = findVideoView(g.getChildAt(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    private static boolean isVideoView(View v) {
        return v instanceof SurfaceView || v instanceof TextureView;
    }
    private static boolean containsVideo(View v) {
        if (v == null) {
            return false;
        }
        if (isVideoView(v)) {
            return true;
        }
        if (!(v instanceof ViewGroup)) {
            return false;
        }
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            if (containsVideo(g.getChildAt(i))) {
                return true;
            }
        }
        return false;
    }
}