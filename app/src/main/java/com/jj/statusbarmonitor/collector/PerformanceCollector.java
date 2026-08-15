package com.jj.statusbarmonitor.collector;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.jj.statusbarmonitor.bridge.PerfBridge;
import com.jj.statusbarmonitor.constant.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * SystemUI 进程内性能采集：CPU / 内存 / 电池 / FPS；
 * GPU 频率与占用来自 {@link com.jj.statusbarmonitor.bridge.PerfBridge}（模块 root 写入 Remote）。
 */
public class PerformanceCollector {

    public interface OnUpdateListener {
        void onUpdate(PerformanceData data);
    }

    public static class PerformanceData {
        public List<Float> cpuCoreUsages = new ArrayList<>();
        public float cpuTotalUsage = 0f;
        /** 各核 scaling_cur_freq 最大值（MHz） */
        public int cpuFreqMaxMhz = 0;
        public int cpuFreqMinMhz = 0;
        public float cpuTemp = 0f;

        public List<Float> gpuCoreUsages = new ArrayList<>(); // Often just 1
        public int gpuFreq = 0;
        public int gpuTotalUsage = 0;

        public float fps = 0;
        public boolean isCharging = false;

        public float batteryPowerW = 0f;
        public float batteryTemp = 0f;

        public float ramUsagePercent = 0f;
        public float zramUsagePercent = 0f;
    }

    private final Context context;
    private final Handler mainHandler;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private final OnUpdateListener listener;

    private boolean isRunning = false;

    private final BatteryManager batteryManager;
    private final ActivityManager activityManager;

    // CPU calculation state
    private final long[][] lastCpuTimes = new long[16][2]; // [core][0: idle, 1: total], index 0 is total CPU

    // Battery Intent state
    private float lastBatteryTemp = 0f;
    private int lastBatteryVoltageMv = 0;
    private int lastBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;

    private int frameCount = 0;
    private long lastFpsTime = 0;
    private int currentFps = 0;

    private float lastSuccessfulFps = 0f;
    private int lastSuccessfulGpuFreq = 0;
    private int lastSuccessfulCpuFreqMaxMhz = 0;
    private int lastSuccessfulCpuFreqMinMhz = 0;

    private final android.view.Choreographer.FrameCallback frameCallback = new android.view.Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frameCount++;
            if (isRunning) {
                android.view.Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            long now = System.currentTimeMillis();
            float calcFps = 0;
            if (lastFpsTime > 0) {
                long delta = now - lastFpsTime;
                if (delta > Constants.Config.UPDATE_INTERVAL_MS) { // Use constant
                    calcFps = frameCount * 1000f / delta;
                    currentFps = (int) calcFps;
                    frameCount = 0;
                    lastFpsTime = now;
                }
            } else {
                lastFpsTime = now;
            }

            PerformanceData data = collectData();

            // sysfs 读取失败（Fps.READ_FAILED）时，用 Choreographer 估算帧率
            if (data.fps == Constants.Fps.READ_FAILED) {
                data.fps = calcFps > 0 ? calcFps : currentFps;
            }

            // 如果最终还是获取失败 (<=0)，使用上一次成功的数据
            if (data.fps <= 0) {
                data.fps = lastSuccessfulFps;
            } else {
                lastSuccessfulFps = data.fps;
            }

            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onUpdate(data);
                }
            });

            bgHandler.postDelayed(this, Constants.Config.UPDATE_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int tempInt = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                lastBatteryTemp = tempInt / 10f;
                lastBatteryVoltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                lastBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            }
        }
    };

    public PerformanceCollector(Context context, OnUpdateListener listener) {
        this.context = context;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(batteryReceiver, filter);

        // Start FPS counter on main thread
        mainHandler.post(() -> {
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback);
        });

        bgThread = new HandlerThread(Constants.ThreadName.PERF_COLLECTOR);
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
        bgHandler.post(updateRunnable);
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;

        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (Exception e) {}

        if (bgHandler != null) {
            bgHandler.removeCallbacks(updateRunnable);
        }
        if (bgThread != null) {
            bgThread.quitSafely();
        }
    }

    /** 在后台线程聚合各子项指标 */
    private PerformanceData collectData() {
        PerformanceData data = new PerformanceData();

        collectCpuData(data);
        collectGpuData(data);
        collectMemoryData(data);
        collectBatteryData(data);
        collectFpsData(data);

        return data;
    }

    private void collectCpuData(PerformanceData data) {
        try (BufferedReader br = new BufferedReader(new FileReader(Constants.Proc.STAT))) {
            String line;
            int coreIndex = 0; // 0 is total, 1..n are cores cpu0..cpuN
            while ((line = br.readLine()) != null) {
                if (line.startsWith("cpu")) {
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length < 5) continue;

                    long user = Long.parseLong(tokens[1]);
                    long nice = Long.parseLong(tokens[2]);
                    long system = Long.parseLong(tokens[3]);
                    long idle = Long.parseLong(tokens[4]);
                    long iowait = tokens.length > 5 ? Long.parseLong(tokens[5]) : 0;
                    long irq = tokens.length > 6 ? Long.parseLong(tokens[6]) : 0;
                    long softirq = tokens.length > 7 ? Long.parseLong(tokens[7]) : 0;

                    long idleTime = idle + iowait;
                    long totalTime = user + nice + system + idle + iowait + irq + softirq;

                    if (coreIndex < 16) {
                        long lastIdle = lastCpuTimes[coreIndex][0];
                        long lastTotal = lastCpuTimes[coreIndex][1];

                        long deltaIdle = idleTime - lastIdle;
                        long deltaTotal = totalTime - lastTotal;

                        float usage = 0f;
                        if (deltaTotal > 0) {
                            usage = (deltaTotal - deltaIdle) * 100f / deltaTotal;
                        }

                        if (coreIndex == 0) {
                            data.cpuTotalUsage = usage;
                        } else {
                            data.cpuCoreUsages.add(usage);
                        }

                        lastCpuTimes[coreIndex][0] = idleTime;
                        lastCpuTimes[coreIndex][1] = totalTime;
                    }
                    coreIndex++;
                }
            }
        } catch (Exception e) {
            // Ignore or log
        }

        collectCpuFreqMax(data);

        data.cpuTemp = getThermalTemp(Constants.Proc.CPU_THERMAL_KEYWORDS);
    }

    /** 读取各 policy / 各核 scaling_cur_freq，取当前最大值（MHz） */
    private void collectCpuFreqMax(PerformanceData data) {
        int maxMhz = 0;
        int minMhz = Integer.MAX_VALUE;

        File cpuFreqDir = new File(Constants.Cpu.CPUFREQ_DIR);
        if (cpuFreqDir.isDirectory()) {
            File[] policies = cpuFreqDir.listFiles((dir, name) -> name.startsWith("policy"));
            if (policies != null) {
                for (File policy : policies) {
                    int mhz = readCpuFreqMhz(
                            policy.getAbsolutePath() + "/" + Constants.Cpu.SCALING_CUR_FREQ);
                    if (mhz > 0) {
                        maxMhz = Math.max(maxMhz, mhz);
                        minMhz = Math.min(minMhz, mhz);
                    }
                }
            }
        }

        if (maxMhz == 0 || minMhz == Integer.MAX_VALUE) {
            File cpuDir = new File(Constants.Cpu.DEVICE_DIR);
            File[] cpus = cpuDir.listFiles((dir, name) -> name.matches("cpu\\d+"));
            if (cpus != null) {
                for (File cpu : cpus) {
                    int mhz = readCpuFreqMhz(
                            cpu.getAbsolutePath() + "/cpufreq/" + Constants.Cpu.SCALING_CUR_FREQ);
                    if (mhz > 0) {
                        maxMhz = Math.max(maxMhz, mhz);
                        minMhz = Math.min(minMhz, mhz);
                    }
                }
            }
        }

        if (maxMhz > 0) {
            data.cpuFreqMaxMhz = maxMhz;
            data.cpuFreqMinMhz = minMhz;
            lastSuccessfulCpuFreqMaxMhz = maxMhz;
            lastSuccessfulCpuFreqMinMhz = minMhz;
        } else if (lastSuccessfulCpuFreqMaxMhz > 0) {
            data.cpuFreqMaxMhz = lastSuccessfulCpuFreqMaxMhz;
            data.cpuFreqMinMhz = lastSuccessfulCpuFreqMinMhz;
        }
    }

    private int readCpuFreqMhz(String path) {
        String line = readFirstLine(path);
        if (line == null) {
            return 0;
        }
        try {
            long khz = Long.parseLong(line.trim());
            return (int) (khz / Constants.Cpu.KHZ_TO_MHZ);
        } catch (Exception e) {
            return 0;
        }
    }

    /** GPU 由模块进程 root 采集后经 Remote 传入 */
    private void collectGpuData(PerformanceData data) {
        int gpuMhz = PerfBridge.getGpuFreqMhz();
        if (gpuMhz <= 0) {
            gpuMhz = lastSuccessfulGpuFreq;
        } else {
            lastSuccessfulGpuFreq = gpuMhz;
        }
        data.gpuFreq = gpuMhz;

        int remoteUsage = PerfBridge.getGpuUsage();
        if (remoteUsage >= 0) {
            data.gpuCoreUsages.add((float) remoteUsage);
            data.gpuTotalUsage = remoteUsage;
        }
    }

    private void collectMemoryData(PerformanceData data) {
        if (activityManager != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(mi);
            if (mi.totalMem > 0) {
                data.ramUsagePercent = (mi.totalMem - mi.availMem) * 100f / mi.totalMem;
            }
        }

        // ZRAM
        try (BufferedReader br = new BufferedReader(new FileReader(Constants.Proc.MEMINFO))) {
            String line;
            long swapTotal = 0, swapFree = 0;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) {
                    swapTotal = parseMeminfoLine(line);
                } else if (line.startsWith("SwapFree:")) {
                    swapFree = parseMeminfoLine(line);
                }
            }
            if (swapTotal > 0) {
                data.zramUsagePercent = (swapTotal - swapFree) * 100f / swapTotal;
            }
        } catch (Exception e) {}
    }

    private void collectBatteryData(PerformanceData data) {
        data.batteryTemp = lastBatteryTemp;

        if (batteryManager != null) {
            int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);

            // 骁龙 865 通常返回 uA。这里先取绝对值。
            float currentA = Math.abs(currentNow) / 1000000f;

            // 兼容性判断：如果读出的电流大得离谱（比如 500A），那它其实是 mA
            if (currentA > 50) {
                currentA = Math.abs(currentNow) / 1000f;
            }

            float voltageV = lastBatteryVoltageMv / 1000f;
            if (voltageV <= 0) voltageV = 4.0f; // fallback

            float power = currentA * voltageV;

            // 核心修复：基于系统状态判断充放电，而不是依赖内核电流符号
            boolean isCharging = lastBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                                lastBatteryStatus == BatteryManager.BATTERY_STATUS_FULL;

            data.batteryPowerW = isCharging ? power : -power;
            data.isCharging = isCharging;
        }
    }

    /** 优先读面板 measured_fps；均失败则标记 READ_FAILED 由 Choreographer 兜底 */
    private void collectFpsData(PerformanceData data) {
        for (String path : Constants.Fps.SYSFS_PATHS) {
            String fpsStr = readFirstLine(path);
            if (fpsStr != null) {
                try {
                    fpsStr = fpsStr.replaceAll("[^0-9.]", "");
                    if (!fpsStr.isEmpty()) {
                        float fps = Float.parseFloat(fpsStr);
                        // 过滤掉异常大的值
                        if (fps > 0 && fps < Constants.Fps.MAX_VALID) {
                            data.fps = fps;
                            return;
                        }
                    }
                } catch (Exception e) {}
            }
        }

        data.fps = Constants.Fps.READ_FAILED;
    }

    private float getThermalTemp(String... keywords) {
        File dir = new File(Constants.Proc.THERMAL_CLASS);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().startsWith("thermal_zone")) {
                        String type = readFirstLine(f.getAbsolutePath() + "/type");
                        if (type != null) {
                            type = type.toLowerCase();
                            for (String kw : keywords) {
                                if (type.contains(kw)) {
                                    String tempStr = readFirstLine(f.getAbsolutePath() + "/temp");
                                    if (tempStr != null) {
                                        try {
                                            float temp = Float.parseFloat(tempStr.trim());
                                            if (temp > 1000) return temp / 1000f;
                                            if (temp > 100) return temp / 10f;
                                            return temp;
                                        } catch (Exception e) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return 0f;
    }

    private String readFirstLine(String path) {
        File f = new File(path);
        if (!f.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private long parseMeminfoLine(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception e) {}
        return 0;
    }
}
