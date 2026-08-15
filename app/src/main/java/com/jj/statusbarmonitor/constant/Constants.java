package com.jj.statusbarmonitor.constant;

/**
 * 全局常量。按职责分子类，避免魔法字符串散落各处。
 * <p>
 * 包结构（当前无需调整）：
 * <ul>
 *   <li>{@code xposed} — LSPosed 入口，注入 SystemUI</li>
 *   <li>{@code bridge} — 模块进程与 SystemUI 之间的 Remote / 反色桥接</li>
 *   <li>{@code collector} — 性能数据采集（SystemUI 内 + 模块 root GPU）</li>
 *   <li>{@code service} — 广播与拉起采集线程</li>
 *   <li>{@code ui} / {@code ui.view} — 调试页与状态栏监视器视图</li>
 *   <li>{@code utils} — 反射、su、{@link com.jj.statusbarmonitor.utils.LogUtils} 日志</li>
 * </ul>
 */
public final class Constants {

    public static final String TAG = "StatusBarMonitor";

    private Constants() {}

    /** 时间间隔、阈值等运行参数 */
    public static final class Config {
        /**
         * 是否输出调试日志（logcat 标签 {@link Constants#TAG}）。
         * 发布版建议 {@code false}；排查问题时改为 {@code true} 后重新编译安装。
         */
        public static final boolean LOG_ENABLED = false;

        /** 数据采集与 UI 刷新间隔（毫秒） */
        public static final int UPDATE_INTERVAL_MS = 1000;
        /** {@code su} 命令超时（毫秒） */
        public static final int EXEC_TIMEOUT_MS = 8000;
        /** 等待 XposedService 绑定时，每隔多少次 tick 打一条 warn 日志 */
        public static final int SERVICE_BIND_WARN_EVERY_TICKS = 5;
        /** FPS 显示：低于此值保留一位小数，否则显示整数 */
        public static final float FPS_INTEGER_THRESHOLD = 100f;
        /** 未写入 Remote 前的占位标记 */
        public static final int UNPUBLISHED = Integer.MIN_VALUE;

        private Config() {}
    }

    /** libxposed RemotePreferences / openRemoteFile */
    public static final class Remote {
        public static final String PREFS_NAME = "perf";
        public static final String KEY_GPU_FREQ_MHZ = "gpu_freq_mhz";
        public static final String KEY_GPU_USAGE_PERCENT = "gpu_usage_pct";
        public static final String KEY_PREVENT_AUTO_HIDE = "prevent_auto_hide";
        public static final String KEY_UPDATED_AT = "updated_at";
        /** openRemoteFile 备用通道（与 RemotePreferences 双写） */
        public static final String GPU_FREQ_FILE = "gpu_freq.txt";

        private Remote() {}
    }

    /** 作用域相关包名 */
    public static final class Package {
        public static final String SYSTEM_UI = "com.android.systemui";
        public static final String MODULE = "com.jj.statusbarmonitor";

        private Package() {}
    }

    /** Manifest 组件全限定名 */
    public static final class Component {
        /** EarlyInitProvider authority，外部 query 可拉起模块进程 */
        public static final String EARLY_INIT_AUTHORITY =
                Package.MODULE + ".earlyinit";

        private Component() {}
    }

    /**
     * SystemUI 反射 / Hook 用类名、方法名、布局 id。
     * 目标环境：Android 12 + MIUI 13。
     */
    public static final class SystemUi {
        public static final String FRAGMENT_COLLAPSED_PKG =
                "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment";
        public static final String FRAGMENT_COLLAPSED_LEGACY =
                "com.android.systemui.statusbar.phone.CollapsedStatusBarFragment";

        public static final String CLASS_DEPENDENCY = "com.android.systemui.Dependency";
        public static final String CLASS_DARK_DISPATCHER_POLICY =
                "com.android.systemui.statusbar.policy.DarkIconDispatcher";
        public static final String CLASS_DARK_RECEIVER_POLICY =
                CLASS_DARK_DISPATCHER_POLICY + "$DarkReceiver";
        public static final String CLASS_DARK_DISPATCHER_IMPL =
                "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl";
        public static final String CLASS_STATUS_BAR_ICON_VIEW =
                "com.android.systemui.statusbar.StatusBarIconView";

        public static final String METHOD_DEPENDENCY_GET = "get";
        public static final String METHOD_ON_VIEW_CREATED = "onViewCreated";
        public static final String METHOD_ON_DESTROY_VIEW = "onDestroyView";
        public static final String METHOD_ON_DARK_CHANGED = "onDarkChanged";
        public static final String METHOD_APPLY_ICON_TINT = "applyIconTint";
        public static final String METHOD_APPLY_DARK_INTENSITY = "applyDarkIntensity";
        public static final String METHOD_ADD_DARK_RECEIVER = "addDarkReceiver";
        public static final String METHOD_REMOVE_DARK_RECEIVER = "removeDarkReceiver";
        public static final String METHOD_APPLY_DARK = "applyDark";

        /** 状态栏全屏自动隐藏控制器 */
        public static final String CLASS_AUTO_HIDE_CONTROLLER =
                "com.android.systemui.statusbar.phone.AutoHideController";
        public static final String METHOD_TOUCH_AUTO_HIDE = "touchAutoHide";
        public static final String METHOD_RESUME_AUTO_HIDE = "resumeAutoHide";
        public static final String METHOD_HIDE = "hide";

        /** {@code status_bar} 根容器，监视器注入于此 */
        public static final String ID_STATUS_BAR = "status_bar";
        /** 采样已有图标 tint 时的候选 id */
        public static final String[] ID_TINT_REFERENCE = {
                "statusIcons", "system_icon_area", "battery", "clock"
        };

        /** DarkIconDispatcherImpl 上图标颜色字段（MIUI 可能混淆命名） */
        public static final String[] FIELD_ICON_TINT = {"mIconTint", "iconTint"};

        private SystemUi() {}
    }

    /** CPU 频率 sysfs（SystemUI 进程可读 scaling_cur_freq） */
    public static final class Cpu {
        public static final String DEVICE_DIR = "/sys/devices/system/cpu";
        public static final String CPUFREQ_DIR = DEVICE_DIR + "/cpufreq";
        public static final String SCALING_CUR_FREQ = "scaling_cur_freq";
        /** sysfs 值为 kHz，除以该系数得到 MHz 整数 */
        public static final int KHZ_TO_MHZ = 1000;

        private Cpu() {}
    }

    /** 高通 KGSL GPU sysfs（模块进程 root 读取） */
    public static final class Gpu {
        public static final String KGSL_DEVICE = "/sys/class/kgsl/kgsl-3d0";

        /**
         * GPU 频率节点，按优先级排列。
         * clock_mhz  — 部分驱动直接给 MHz（浮点）
         * devfreq/cur_freq — 标准 devfreq，单位 Hz，需 ÷ 1_000_000
         * cur_gpu_clock_freq — 高通老驱动，单位 Hz，需 ÷ 1_000_000
         */
        public static final String[] FREQ_PATHS = {
                KGSL_DEVICE + "/clock_mhz",
                KGSL_DEVICE + "/devfreq/cur_freq",
                KGSL_DEVICE + "/cur_gpu_clock_freq",
                "/sys/class/devfreq/kgsl-3d0/cur_freq",
        };

        /**
         * true  表示该路径的值单位为 Hz，需除以 1_000_000 转换为 MHz；
         * false 表示单位已是 MHz（clock_mhz 节点）。
         * 与 FREQ_PATHS 一一对应。
         */
        public static final boolean[] FREQ_IS_HZ = {false, true, true, true};

        /**
         * GPU 占用节点，按优先级排列。
         * gpu_busy_percentage — 返回 "N 100" 格式，N 即占用百分比
         * gpu_load            — 部分老驱动，直接返回整数百分比
         */
        public static final String[] BUSY_PATHS = {
                KGSL_DEVICE + "/gpu_busy_percentage",
                KGSL_DEVICE + "/gpu_load",
        };

        private Gpu() {}
    }

    /** 面板实测 FPS 节点（小米 / 高通等） */
    public static final class Fps {
        public static final float MAX_VALID = 1000f;
        public static final String[] SYSFS_PATHS = {
                "/sys/class/drm/sde-crtc-0/measured_fps",
                "/sys/class/graphics/fb0/measured_fps",
                "/sys/class/drm/res_info_fps",
                "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/card0-DSI-1/measured_fps",
        };
        /** collectFpsData 失败时由 Choreographer 兜底的标记值 */
        public static final float READ_FAILED = -1f;

        private Fps() {}
    }

    /** {@code /proc}、thermal 等路径 */
    public static final class Proc {
        public static final String STAT = "/proc/stat";
        public static final String MEMINFO = "/proc/meminfo";
        public static final String THERMAL_CLASS = "/sys/class/thermal";
        public static final String[] CPU_THERMAL_KEYWORDS = {"cpu", "soc", "mtktscpu"};

        private Proc() {}
    }

    /** 状态栏监视器 UI 尺寸与宽度探针 */
    public static final class Ui {
        public static final float TEXT_SIZE_SP = 7f;
        public static final float MONITOR_BAR_HEIGHT_DP = 8f;
        public static final float DIVIDER_WIDTH_DP = 8f;
        public static final float BAR_HORIZONTAL_MARGIN_DP = 2f;
        public static final float CPU_BAR_WIDTH_DP = 24f;
        public static final float GPU_BAR_WIDTH_DP = 5f;
        public static final float BAR_HEIGHT_DP = 6f;
        public static final float BAR_SPACING_PX = 2f;
        public static final float BAR_HIGH_USAGE_THRESHOLD = 85f;

        public static final int COLOR_BAR_NORMAL = 0xFF4285F4;
        public static final int COLOR_BAR_HIGH = 0xFFEA4335;
        public static final int COLOR_BAR_BG = 0x40888888;

        /** 等宽字体下测量固定列宽用的最长样例（勿用 ems） */
        public static final String PROBE_USAGE = "99%";
        /** 最大频率长度，如 3333 */
        public static final String PROBE_CPU_FREQ = "9999";
        public static final String PROBE_GPU = "999";
        public static final String PROBE_FPS = "99.9";
        public static final String PROBE_PCT = "99%";
        public static final String PROBE_TEMP = "99.9°C";
        public static final String PROBE_POWER = "+99.99W";

        public static final String GPU_NA = "N/A";

        private Ui() {}
    }

    /** 后台线程名 */
    public static final class ThreadName {
        public static final String GPU_COLLECTOR = "GpuCollector";
        public static final String PERF_COLLECTOR = "PerfCollector";
        public static final String EXEC_READER = "ExecUtils-reader";

        private ThreadName() {}
    }
}
