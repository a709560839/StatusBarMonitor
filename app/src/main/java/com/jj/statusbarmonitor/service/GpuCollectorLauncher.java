package com.jj.statusbarmonitor.service;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import com.jj.statusbarmonitor.collector.GpuCollectorWorker;
import com.jj.statusbarmonitor.constant.Constants;
import com.jj.statusbarmonitor.utils.LogUtils;

/**
 * 拉起模块 App 进程并启动 {@link GpuCollectorWorker}。
 * SystemUI 无法可靠 su，故 GPU 采集在模块进程完成后经 Remote 传给 SystemUI。
 */
public final class GpuCollectorLauncher {

    private GpuCollectorLauncher() {}

    /** 模块进程内直接启动采集线程 */
    public static void startInProcess() {
        GpuCollectorWorker.start();
    }

    /**
     * 通过 ContentResolver 查询 {@link EarlyInitProvider} 拉起模块进程。
     * 系统会自动启动宿主进程并触发 Provider.onCreate()，不依赖广播。
     */
    public static void wakeModuleByProvider(Context context) {
        if (context == null) {
            return;
        }
        try {
            Uri uri = Uri.parse("content://" + Constants.Component.EARLY_INIT_AUTHORITY);
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                cursor.close();
            }
            LogUtils.i("wakeModuleByProvider: ok");
        } catch (Throwable t) {
            LogUtils.w("wakeModuleByProvider failed", t);
        }
    }

    /**
     * 外进程唤醒：通过 ContentProvider 拉起模块进程；
     * 若已在模块进程则直接 start。
     */
    public static void wakeFromExternal(Context context) {
        wakeModuleByProvider(context);
        if (context != null && Constants.Package.MODULE.equals(context.getPackageName())) {
            startInProcess();
        }
    }
}


