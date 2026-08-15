package com.jj.statusbarmonitor;

import android.app.Application;

import com.jj.statusbarmonitor.collector.GpuCollectorWorker;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 模块 Application：绑定 libxposed {@link XposedService} 后启动 GPU 采集。
 */
public class App extends Application implements XposedServiceHelper.OnServiceListener {

    private static volatile XposedService xposedService;

    public static XposedService getXposedService() {
        return xposedService;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    /** Xposed 服务绑定成功 → 启动 Remote 写入线程 */
    @Override
    public void onServiceBind(XposedService service) {
        xposedService = service;
        GpuCollectorWorker.start();
    }

    @Override
    public void onServiceDied(XposedService service) {
        xposedService = null;
        GpuCollectorWorker.stop();
    }
}
