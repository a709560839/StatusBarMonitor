package com.jj.statusbarmonitor.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.jj.statusbarmonitor.bridge.PerfBridge;
import com.jj.statusbarmonitor.bridge.StatusBarTintBridge;
import com.jj.statusbarmonitor.constant.Constants;
import com.jj.statusbarmonitor.service.GpuCollectorLauncher;
import com.jj.statusbarmonitor.ui.view.MonitorView;
import com.jj.statusbarmonitor.utils.LogUtils;
import com.jj.statusbarmonitor.utils.ReflectUtils;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * LSPosed 模块入口：在 SystemUI 注入 {@link MonitorView}，在模块进程启动 GPU 采集。
 */
public class ModuleMain extends XposedModule {

    private MonitorView monitorView;
    private StatusBarTintBridge tintBridge;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        LogUtils.xposed(this, Log.DEBUG, "onPackageReady: " + pkg);

        // 模块自身进程：启动 root GPU 采集线程
        if (Constants.Package.MODULE.equals(pkg)) {
            LogUtils.xposed(this, Log.INFO, "Module process ready");
            GpuCollectorLauncher.startInProcess();
            return;
        }

        if (!Constants.Package.SYSTEM_UI.equals(pkg)) {
            return;
        }

        LogUtils.xposed(this, Log.INFO, "SystemUI loaded, starting hook...");

        // SystemUI 侧绑定 Remote，用于读取 GPU 等跨进程数据
        try {
            PerfBridge.bindModule(this);
        } catch (Throwable t) {
            LogUtils.xposed(this, Log.ERROR, "PerfBridge bind failed", t);
        }

        ClassLoader classLoader = param.getClassLoader();
        tintBridge = new StatusBarTintBridge(classLoader);
        hookMiui12StatusBarTint(classLoader);
        hookPreventStatusBarAutoHide(classLoader);

        // 注入折叠状态栏 Fragment（新版 / 旧版类名各试一次）
        boolean hooked = tryHookFragment(classLoader, Constants.SystemUi.FRAGMENT_COLLAPSED_PKG);
        if (!hooked) {
            hooked = tryHookFragment(classLoader, Constants.SystemUi.FRAGMENT_COLLAPSED_LEGACY);
        }
        if (!hooked) {
            LogUtils.xposed(this, Log.ERROR, "Failed to hook CollapsedStatusBarFragment");
        }
    }

    /**
     * Hook Fragment 生命周期：在 onViewCreated 注入监视器，在 onDestroyView 解除反色注册。
     */
    private boolean tryHookFragment(ClassLoader classLoader, String className) {
        LogUtils.xposed(this, Log.DEBUG, "tryHookFragment: " + className);
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);

            Method onViewCreated = findOnViewCreated(clazz);
            if (onViewCreated == null) {
                LogUtils.xposed(this, Log.WARN, "onViewCreated not found in " + className);
                return false;
            }

            hook(onViewCreated).intercept(chain -> {
                Object result = chain.proceed();
                View root = (View) chain.getArg(0);
                if (root != null) {
                    injectMonitorView(root);
                }
                return result;
            });

            for (Method m : clazz.getDeclaredMethods()) {
                if (Constants.SystemUi.METHOD_ON_DESTROY_VIEW.equals(m.getName())
                        && m.getParameterTypes().length == 0) {
                    hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        if (tintBridge != null) {
                            tintBridge.detach();
                        }
                        return result;
                    });
                    break;
                }
            }

            LogUtils.xposed(this, Log.INFO, "Successfully hooked " + className);
            return true;
        } catch (ClassNotFoundException e) {
            LogUtils.xposed(this, Log.DEBUG, "Class not found: " + className);
            return false;
        } catch (Exception e) {
            LogUtils.xposed(this, Log.ERROR, "Error hooking " + className, e);
            return false;
        }
    }

    private static Method findOnViewCreated(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (Constants.SystemUi.METHOD_ON_VIEW_CREATED.equals(m.getName())
                    && m.getParameterTypes().length == 2
                    && m.getParameterTypes()[0] == View.class) {
                return m;
            }
        }
        return null;
    }

    /**
     * 将 {@link MonitorView} 加到 {@code R.id.status_bar} 顶部居中，并注册反色桥接。
     */
    private void injectMonitorView(View root) {
        Context context = root.getContext();
        View statusBarRoot = ReflectUtils.findViewByIdName(root, Constants.SystemUi.ID_STATUS_BAR);

        if (!(statusBarRoot instanceof FrameLayout)) {
            LogUtils.xposed(this, Log.ERROR, "status_bar not found or not FrameLayout: " + statusBarRoot);
            return;
        }

        FrameLayout statusBar = (FrameLayout) statusBarRoot;

        // 重复注入时先移除旧实例
        if (monitorView != null && monitorView.getParent() != null) {
            if (tintBridge != null) {
                tintBridge.detach();
            }
            statusBar.removeView(monitorView);
        }

        // 唤醒模块进程，确保 GPU root 采集与 Remote 写入运行
        // 必须等用户解锁后才能拉起（防止 Direct Boot 下系统阻塞 Provider 查询）
        wakeCollectorSafely(context);

        monitorView = new MonitorView(context);

        int viewHeight = dp2px(context, Constants.Ui.MONITOR_BAR_HEIGHT_DP);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                viewHeight);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        monitorView.setLayoutParams(lp);

        statusBar.addView(monitorView);

        if (tintBridge != null) {
            tintBridge.attach(monitorView, statusBar);
        }

        LogUtils.xposed(this, Log.INFO, "MonitorView injected");
    }

    private void wakeCollectorSafely(Context context) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        if (userManager != null && userManager.isUserUnlocked()) {
            GpuCollectorLauncher.wakeFromExternal(context);
        } else {
            LogUtils.xposed(this, Log.INFO, "Device locked, waiting for USER_UNLOCKED to wake collector");
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                        LogUtils.xposed(ModuleMain.this, Log.INFO, "USER_UNLOCKED received, waking collector");
                        GpuCollectorLauncher.wakeFromExternal(ctx);
                        try {
                            ctx.unregisterReceiver(this);
                        } catch (Throwable t) {
                            // Ignore
                        }
                    }
                }
            };
            context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        }
    }

    /**
     * MIUI 12 / A12：监听图标 tint 变化并同步到监视器。
     */
    private void hookMiui12StatusBarTint(ClassLoader classLoader) {
        try {
            Class<?> impl = Class.forName(Constants.SystemUi.CLASS_DARK_DISPATCHER_IMPL, false, classLoader);
            for (Method m : impl.getDeclaredMethods()) {
                String name = m.getName();
                if (!Constants.SystemUi.METHOD_APPLY_ICON_TINT.equals(name)
                        && !Constants.SystemUi.METHOD_APPLY_DARK_INTENSITY.equals(name)) {
                    continue;
                }
                if (m.getParameterTypes().length > 1) {
                    continue;
                }
                hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    if (tintBridge != null) {
                        tintBridge.onDispatcherTint(chain.getThisObject());
                    }
                    return result;
                });
                LogUtils.xposed(this, Log.INFO, "Hooked DarkIconDispatcherImpl." + name);
            }
        } catch (Throwable t) {
            LogUtils.xposed(this, Log.WARN, "DarkIconDispatcherImpl hook skipped", t);
        }

        try {
            Class<?> iconView = Class.forName(Constants.SystemUi.CLASS_STATUS_BAR_ICON_VIEW, false, classLoader);
            for (Method m : iconView.getDeclaredMethods()) {
                if (!Constants.SystemUi.METHOD_ON_DARK_CHANGED.equals(m.getName())) {
                    continue;
                }
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 3 || params[1] != float.class || params[2] != int.class) {
                    continue;
                }
                hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    if (tintBridge != null) {
                        tintBridge.onIconDarkChanged((Integer) chain.getArg(2));
                    }
                    return result;
                });
                LogUtils.xposed(this, Log.INFO, "Hooked StatusBarIconView.onDarkChanged");
                break;
            }
        } catch (Throwable t) {
            LogUtils.xposed(this, Log.WARN, "StatusBarIconView hook skipped", t);
        }
    }

    /**
     * Android 12 / MIUI 13：全屏模式下拉状态栏后阻止自动隐藏。
     * Hook SystemUI 的 AutoHideController 拦截隐藏倒计时及隐藏动作。
     */
    private void hookPreventStatusBarAutoHide(ClassLoader classLoader) {
        try {
            Class<?> autoHideClass = Class.forName(Constants.SystemUi.CLASS_AUTO_HIDE_CONTROLLER, false, classLoader);
            int count = 0;
            for (Method m : autoHideClass.getDeclaredMethods()) {
                String name = m.getName();
                if (Constants.SystemUi.METHOD_TOUCH_AUTO_HIDE.equals(name)
                        || Constants.SystemUi.METHOD_RESUME_AUTO_HIDE.equals(name)
                        || Constants.SystemUi.METHOD_HIDE.equals(name)) {
                    hook(m).intercept(chain -> {
                        if (PerfBridge.isPreventAutoHideEnabled()) {
                            LogUtils.xposed(this, Log.INFO, "Prevented status bar auto-hide: " + name);
                            return null;
                        }
                        return chain.proceed();
                    });
                    count++;
                }
            }
            LogUtils.xposed(this, Log.INFO, "Hooked AutoHideController methods count: " + count);
        } catch (ClassNotFoundException e) {
            LogUtils.xposed(this, Log.WARN, "AutoHideController class not found", e);
        } catch (Throwable t) {
            LogUtils.xposed(this, Log.ERROR, "Failed to hook AutoHideController", t);
        }
    }

    private static int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
