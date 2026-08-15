package com.jj.statusbarmonitor.utils;

import android.util.Log;

import com.jj.statusbarmonitor.constant.Constants;

import io.github.libxposed.api.XposedModule;

/**
 * 统一日志出口，由 {@link Constants.Config#LOG_ENABLED} 控制是否输出。
 */
public final class LogUtils {

    private LogUtils() {}

    public static boolean isEnabled() {
        return Constants.Config.LOG_ENABLED;
    }

    public static void d(String msg) {
        if (isEnabled()) {
            Log.d(Constants.TAG, msg);
        }
    }

    public static void i(String msg) {
        if (isEnabled()) {
            Log.i(Constants.TAG, msg);
        }
    }

    public static void w(String msg) {
        if (isEnabled()) {
            Log.w(Constants.TAG, msg);
        }
    }

    public static void w(String msg, Throwable t) {
        if (isEnabled()) {
            Log.w(Constants.TAG, msg, t);
        }
    }

    public static void e(String msg) {
        if (isEnabled()) {
            Log.e(Constants.TAG, msg);
        }
    }

    public static void e(String msg, Throwable t) {
        if (isEnabled()) {
            Log.e(Constants.TAG, msg, t);
        }
    }

    /** Xposed 模块日志（同时写入 logcat，便于 adb 过滤） */
    public static void xposed(XposedModule module, int level, String msg) {
        if (!isEnabled() || module == null) {
            return;
        }
        module.log(level, Constants.TAG, msg);
        writeLogcat(level, msg, null);
    }

    public static void xposed(XposedModule module, int level, String msg, Throwable t) {
        if (!isEnabled() || module == null) {
            return;
        }
        module.log(level, Constants.TAG, msg, t);
        writeLogcat(level, msg, t);
    }

    private static void writeLogcat(int level, String msg, Throwable t) {
        switch (level) {
            case Log.DEBUG:
                if (t != null) Log.d(Constants.TAG, msg, t);
                else Log.d(Constants.TAG, msg);
                break;
            case Log.INFO:
                if (t != null) Log.i(Constants.TAG, msg, t);
                else Log.i(Constants.TAG, msg);
                break;
            case Log.WARN:
                if (t != null) Log.w(Constants.TAG, msg, t);
                else Log.w(Constants.TAG, msg);
                break;
            case Log.ERROR:
            default:
                if (t != null) Log.e(Constants.TAG, msg, t);
                else Log.e(Constants.TAG, msg);
                break;
        }
    }
}
