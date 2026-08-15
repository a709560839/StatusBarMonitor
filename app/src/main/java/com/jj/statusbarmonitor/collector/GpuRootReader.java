package com.jj.statusbarmonitor.collector;

import com.jj.statusbarmonitor.constant.Constants;
import com.jj.statusbarmonitor.utils.RootShell;

/**
 * 在模块 App 进程内通过持久化 root shell 读取高通 KGSL GPU 频率与占用。
 *
 * <p>直接 FileReader 因 SELinux {@code untrusted_app} 域被拒绝访问
 * {@code vendor_sysfs_kgsl}，故改用 {@link RootShell} 在 su 域内读文件。
 * RootShell 只 fork 一次 su 进程，后续通过 pipe 复用，开销远低于每次 exec cat。
 */
public final class GpuRootReader {

    private GpuRootReader() {
    }

    /**
     * 读取当前 GPU 频率（MHz），失败返回 0。
     * 按 Constants.Gpu.FREQ_PATHS 优先级依次尝试，首个可读节点生效。
     */
    public static int readFreqMhz() {
        RootShell shell = RootShell.get();
        String[] paths = Constants.Gpu.FREQ_PATHS;
        boolean[] isHz = Constants.Gpu.FREQ_IS_HZ;
        for (int i = 0; i < paths.length; i++) {
            String line = shell.readFirstLine(paths[i]);
            if (line == null) continue;
            try {
                float val = Float.parseFloat(line.replaceAll("[^0-9.]", ""));
                if (val <= 0) continue;
                int mhz = isHz[i] ? (int) (val / 1_000_000f) : (int) val;
                if (mhz > 0) return mhz;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /**
     * 读取 GPU 占用百分比（0–100），节点不可读时返回 -1。
     * 按 Constants.Gpu.BUSY_PATHS 优先级依次尝试。
     *
     * <p>gpu_busy_percentage 格式："N 100"（N 为占用百分比）
     * <p>gpu_load 格式：直接整数百分比
     */
    public static int readUsagePercent() {
        RootShell shell = RootShell.get();
        for (String path : Constants.Gpu.BUSY_PATHS) {
            String line = shell.readFirstLine(path);
            if (line == null) continue;
            String[] parts = line.trim().split("\\s+");
            try {
                int val = Integer.parseInt(parts[0]);
                if (val >= 0) return val;
            } catch (Exception ignored) {
            }
        }
        return -1;
    }
}
