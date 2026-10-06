package com.douyin.clearmode;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 模块 App 的 Application。
 * 负责注册 libxposed service 监听，使模块 UI 能通过
 * {@link XposedService} 访问框架能力（Remote Preferences / scope / Hot Reload）。
 */
public class App extends Application implements XposedServiceHelper.OnServiceListener {

    private static final String TAG = "ClearMode";

    /** 框架服务实例；未绑定时为 null。 */
    private static volatile XposedService sService;

    public static XposedService getService() {
        return sService;
    }

    /** 供 UI 判断 service 是否已绑定。 */
    public static boolean isServiceBound() {
        return sService != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 只注册一次监听
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        sService = service;
    }

    @Override
    public void onServiceDied(XposedService service) {
        if (sService == service) {
            sService = null;
        }
    }
}
