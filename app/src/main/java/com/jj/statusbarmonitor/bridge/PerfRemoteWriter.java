package com.jj.statusbarmonitor.bridge;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import com.jj.statusbarmonitor.constant.Constants;
import com.jj.statusbarmonitor.utils.LogUtils;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import io.github.libxposed.service.XposedService;

/**
 * 模块 App 进程：将 GPU 数据写入 RemotePreferences，并双写 openRemoteFile 备用文件。
 */
public final class PerfRemoteWriter {

    private PerfRemoteWriter() {}

    /**
     * @return 任一通道写入成功即为 true
     */
    public static boolean publish(XposedService service, int gpuMhz, int gpuUsage) {
        if (service == null) {
            return false;
        }
        boolean ok = writePrefs(service, gpuMhz, gpuUsage);
        if (gpuMhz > 0) {
            ok |= writeFreqFile(service, gpuMhz);
        }
        return ok;
    }

    private static boolean writePrefs(XposedService service, int gpuMhz, int gpuUsage) {
        try {
            SharedPreferences prefs = service.getRemotePreferences(Constants.Remote.PREFS_NAME);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Constants.Remote.KEY_GPU_FREQ_MHZ, gpuMhz);
            if (gpuUsage >= 0) {
                editor.putInt(Constants.Remote.KEY_GPU_USAGE_PERCENT, gpuUsage);
            }
            editor.putLong(Constants.Remote.KEY_UPDATED_AT, System.currentTimeMillis());
            return editor.commit();
        } catch (Throwable t) {
            LogUtils.e("write remote prefs failed", t);
            return false;
        }
    }

    private static boolean writeFreqFile(XposedService service, int gpuMhz) {
        try (ParcelFileDescriptor pfd = service.openRemoteFile(Constants.Remote.GPU_FREQ_FILE)) {
            try (FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor())) {
                fos.write(String.valueOf(gpuMhz).getBytes(StandardCharsets.UTF_8));
                fos.flush();
                return true;
            }
        } catch (Throwable t) {
            LogUtils.w("write remote file failed", t);
            return false;
        }
    }
}
