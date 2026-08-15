package com.jj.statusbarmonitor.bridge;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import com.jj.statusbarmonitor.constant.Constants;
import com.jj.statusbarmonitor.utils.LogUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.github.libxposed.api.XposedModule;

/**
 * SystemUI 进程侧：读取模块 App 写入的 GPU 等性能数据。
 * 优先 {@link XposedModule#getRemotePreferences}，失败则读 {@link XposedModule#openRemoteFile}。
 */
public final class PerfBridge {

    private static volatile XposedModule module;

    private PerfBridge() {}

    /** Hook 启动时绑定模块实例，供后续读 Remote */
    public static void bindModule(XposedModule xposedModule) {
        module = xposedModule;
        try {
            SharedPreferences prefs = xposedModule.getRemotePreferences(Constants.Remote.PREFS_NAME);
            LogUtils.i("PerfBridge bound, gpu="
                    + prefs.getInt(Constants.Remote.KEY_GPU_FREQ_MHZ, 0));
        } catch (Throwable t) {
            LogUtils.e("PerfBridge bind prefs failed", t);
        }
    }

    public static int getGpuFreqMhz() {
        int fromPrefs = readFromPrefs();
        if (fromPrefs > 0) {
            return fromPrefs;
        }
        return readFromRemoteFile();
    }

    public static int getGpuUsage() {
        SharedPreferences p = getPrefs();
        if (p == null) {
            return -1;
        }
        int pct = p.getInt(Constants.Remote.KEY_GPU_USAGE_PERCENT, -1);
        return pct >= 0 ? pct : -1;
    }

    public static long getUpdatedAt() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getLong(Constants.Remote.KEY_UPDATED_AT, 0L) : 0L;
    }

    public static boolean isPreventAutoHideEnabled() {
        SharedPreferences p = getPrefs();
        return p == null || p.getBoolean(Constants.Remote.KEY_PREVENT_AUTO_HIDE, true);
    }

    private static int readFromPrefs() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getInt(Constants.Remote.KEY_GPU_FREQ_MHZ, 0) : 0;
    }

    private static SharedPreferences getPrefs() {
        XposedModule m = module;
        if (m == null) {
            return null;
        }
        try {
            return m.getRemotePreferences(Constants.Remote.PREFS_NAME);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int readFromRemoteFile() {
        XposedModule m = module;
        if (m == null) {
            return 0;
        }
        try {
            ParcelFileDescriptor pfd = m.openRemoteFile(Constants.Remote.GPU_FREQ_FILE);
            try (java.io.InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                 BufferedReader br = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line = br.readLine();
                if (line == null || line.isEmpty()) {
                    return 0;
                }
                float mhz = Float.parseFloat(line.trim().replaceAll("[^0-9.]", ""));
                return mhz > 0 ? (int) mhz : 0;
            }
        } catch (Throwable t) {
            return 0;
        }
    }
}
