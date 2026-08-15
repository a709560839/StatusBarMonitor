package com.jj.statusbarmonitor.collector;

import android.os.Handler;
import android.os.HandlerThread;
import com.jj.statusbarmonitor.App;
import com.jj.statusbarmonitor.bridge.PerfRemoteWriter;
import com.jj.statusbarmonitor.constant.Constants;
import com.jj.statusbarmonitor.utils.LogUtils;

import io.github.libxposed.service.XposedService;

/**
 * 模块 App 进程内后台 GPU 采集（无前台 Service）。
 * <p>
 * 流程：等待 {@link App#getXposedService()} 绑定 → root 读 sysfs → 数值变化时写入 Remote。
 */
public final class GpuCollectorWorker {

    private static HandlerThread workerThread;
    private static Handler workerHandler;
    private static volatile boolean running;
    private static int waitServiceTicks;

    /** 上次已成功写入 Remote 的值 */
    private static int lastPublishedMhz = Constants.Config.UNPUBLISHED;
    private static int lastPublishedUsage = Constants.Config.UNPUBLISHED;

    private static final Runnable collectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            tick();
            workerHandler.postDelayed(this, Constants.Config.UPDATE_INTERVAL_MS);
        }
    };

    private GpuCollectorWorker() {}

    public static synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        ensureThread();
        workerHandler.removeCallbacks(collectRunnable);
        workerHandler.post(collectRunnable);
        LogUtils.i("GpuCollectorWorker started");
    }

    public static synchronized void stop() {
        running = false;
        lastPublishedMhz = Constants.Config.UNPUBLISHED;
        lastPublishedUsage = Constants.Config.UNPUBLISHED;
        if (workerHandler != null) {
            workerHandler.removeCallbacks(collectRunnable);
        }
        LogUtils.i("GpuCollectorWorker stopped");
    }

    private static void ensureThread() {
        if (workerThread != null && workerThread.isAlive()) {
            return;
        }
        workerThread = new HandlerThread(Constants.ThreadName.GPU_COLLECTOR);
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    private static void tick() {
        XposedService service = App.getXposedService();
        if (service == null) {
            waitServiceTicks++;
            if (waitServiceTicks % Constants.Config.SERVICE_BIND_WARN_EVERY_TICKS == 1) {
                LogUtils.w("waiting for XposedService bind…");
            }
            return;
        }
        waitServiceTicks = 0;

        int gpuMhz = GpuRootReader.readFreqMhz();
        int gpuUsage = GpuRootReader.readUsagePercent();

        // 频率与占用均未变化则跳过写入，减轻 Remote 开销
        if (gpuMhz == lastPublishedMhz && gpuUsage == lastPublishedUsage) {
            return;
        }

        boolean written = PerfRemoteWriter.publish(service, gpuMhz, gpuUsage);
        if (written) {
            lastPublishedMhz = gpuMhz;
            lastPublishedUsage = gpuUsage;
        } else if (gpuMhz > 0) {
            LogUtils.w("remote publish failed, gpuMhz=" + gpuMhz);
        }
    }
}
