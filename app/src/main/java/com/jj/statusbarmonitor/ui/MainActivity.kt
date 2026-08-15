package com.jj.statusbarmonitor.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jj.statusbarmonitor.App
import com.jj.statusbarmonitor.collector.GpuRootReader
import com.jj.statusbarmonitor.constant.Constants
import com.jj.statusbarmonitor.service.GpuCollectorLauncher
import com.jj.statusbarmonitor.ui.theme.*
import com.jj.statusbarmonitor.utils.ExecUtils
import com.jj.statusbarmonitor.utils.LogUtils
import io.github.libxposed.service.XposedService

/**
 * 模块主界面：基于 Jetpack Compose (Material 3) 精确还原与增强 XML MaterialCardView 风格。
 */
class MainActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isPreventAutoHide by mutableStateOf(true)
    private var xposedConnected by mutableStateOf(false)
    private var gpuFreqText by mutableStateOf("-- MHz")
    private var gpuUsageText by mutableStateOf("-- %")
    private var lastUpdateText by mutableStateOf("上次同步：从未")

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            mainHandler.postDelayed(this, Constants.Config.UPDATE_INTERVAL_MS.toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        isPreventAutoHide = readPreventAutoHideSetting()

        setContent {
            StatusBarMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MdThemeSurface
                ) {
                    MainScreen(
                        modifier = Modifier.fillMaxSize(),
                        isPreventAutoHide = isPreventAutoHide,
                        onPreventAutoHideChange = { checked ->
                            isPreventAutoHide = checked
                            savePreventAutoHideSetting(checked)
                            Toast.makeText(
                                this,
                                if (checked) "已开启全屏下拉状态栏常驻" else "已恢复全屏状态栏自动隐藏",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onRestartSystemUi = { restartSystemUi() },
                        xposedConnected = xposedConnected,
                        gpuFreqText = gpuFreqText,
                        gpuUsageText = gpuUsageText,
                        lastUpdateText = lastUpdateText
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        GpuCollectorLauncher.startInProcess()
        mainHandler.post(refreshRunnable)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(refreshRunnable)
        super.onStop()
    }

    private fun restartSystemUi() {
        Toast.makeText(this, "正在请求 Root 重启 SystemUI...", Toast.LENGTH_SHORT).show()
        Thread {
            val out = ExecUtils.exec("killall com.android.systemui || pkill -f com.android.systemui")
            LogUtils.i("Restart SystemUI result: $out")
        }.start()
    }

    private fun readPreventAutoHideSetting(): Boolean {
        val svc: XposedService? = App.getXposedService()
        if (svc != null) {
            try {
                val remotePrefs = svc.getRemotePreferences(Constants.Remote.PREFS_NAME)
                if (remotePrefs.contains(Constants.Remote.KEY_PREVENT_AUTO_HIDE)) {
                    return remotePrefs.getBoolean(Constants.Remote.KEY_PREVENT_AUTO_HIDE, true)
                }
            } catch (e: Exception) {
                LogUtils.w("Failed to read KEY_PREVENT_AUTO_HIDE from RemotePreferences", e)
            }
        }
        val localPrefs = getSharedPreferences(Constants.Remote.PREFS_NAME, Context.MODE_PRIVATE)
        return localPrefs.getBoolean(Constants.Remote.KEY_PREVENT_AUTO_HIDE, true)
    }

    private fun savePreventAutoHideSetting(enabled: Boolean) {
        val localPrefs = getSharedPreferences(Constants.Remote.PREFS_NAME, Context.MODE_PRIVATE)
        localPrefs.edit().putBoolean(Constants.Remote.KEY_PREVENT_AUTO_HIDE, enabled).apply()

        val svc: XposedService? = App.getXposedService()
        if (svc != null) {
            try {
                val remotePrefs = svc.getRemotePreferences(Constants.Remote.PREFS_NAME)
                remotePrefs.edit().putBoolean(Constants.Remote.KEY_PREVENT_AUTO_HIDE, enabled).apply()
            } catch (e: Exception) {
                LogUtils.e("Failed to save KEY_PREVENT_AUTO_HIDE to RemotePreferences", e)
            }
        }
    }

    private fun refreshStatus() {
        val localFreq = GpuRootReader.readFreqMhz()
        val localUsage = GpuRootReader.readUsagePercent()

        var remoteFreq = 0
        var remoteUsage = -1
        var updated = 0L

        val svc: XposedService? = App.getXposedService()
        val connected = svc != null
        xposedConnected = connected

        if (svc != null) {
            try {
                val prefs = svc.getRemotePreferences(Constants.Remote.PREFS_NAME)
                remoteFreq = prefs.getInt(Constants.Remote.KEY_GPU_FREQ_MHZ, 0)
                remoteUsage = prefs.getInt(Constants.Remote.KEY_GPU_USAGE_PERCENT, -1)
                updated = prefs.getLong(Constants.Remote.KEY_UPDATED_AT, 0L)
            } catch (_: Exception) {
            }
        }

        val finalFreq = if (remoteFreq > 0) remoteFreq else localFreq
        val finalUsage = if (remoteUsage >= 0) remoteUsage else localUsage

        gpuFreqText = if (finalFreq > 0) "$finalFreq MHz" else "N/A"
        gpuUsageText = if (finalUsage >= 0) "$finalUsage %" else "N/A"

        lastUpdateText = when {
            updated > 0 -> {
                val diffSec = maxOf(0L, (System.currentTimeMillis() - updated) / 1000)
                "上次同步：$diffSec 秒前 (Remote)"
            }
            localFreq > 0 || localUsage >= 0 -> "数据源：本地 root 读取"
            else -> "上次同步：从未 (请检查 root 授权)"
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isPreventAutoHide: Boolean,
    onPreventAutoHideChange: (Boolean) -> Unit,
    onRestartSystemUi: () -> Unit,
    xposedConnected: Boolean,
    gpuFreqText: String,
    gpuUsageText: String,
    lastUpdateText: String
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部 Header 卡片
        HeaderCard()

        // 2. 设置区卡片
        SettingsCard(
            isPreventAutoHide = isPreventAutoHide,
            onPreventAutoHideChange = onPreventAutoHideChange,
            onRestartSystemUi = onRestartSystemUi
        )

        // 3. 运行状态卡片
        StatusCard(
            xposedConnected = xposedConnected,
            gpuFreqText = gpuFreqText,
            gpuUsageText = gpuUsageText,
            lastUpdateText = lastUpdateText
        )

        // 4. 提示与授权卡片
        GuidanceCard()
    }
}

@Composable
fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MdThemePrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "StatusBarMonitor",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MdThemeOnPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Android 12 / MIUI 13 状态栏性能监视器(其他自测)",
                fontSize = 13.sp,
                color = MdThemeOnSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsCard(
    isPreventAutoHide: Boolean,
    onPreventAutoHideChange: (Boolean) -> Unit,
    onRestartSystemUi: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardSurface
        ),
        border = BorderStroke(1.dp, CardStroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "功能设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MdThemePrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 全屏下拉保持状态栏常驻
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "全屏下拉保持状态栏常驻",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MdThemeOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "全屏应用/游戏中下拉状态栏后阻止 3 秒自动倒计时隐藏",
                        fontSize = 12.sp,
                        color = MdThemeOnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = isPreventAutoHide,
                    onCheckedChange = onPreventAutoHideChange
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = CardStroke
            )

            // 重启 SystemUI
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "重启系统界面",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MdThemeOnSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "修改设置或作用域后，点此重启 SystemUI 使 Hook 生效",
                        fontSize = 12.sp,
                        color = MdThemeOnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onRestartSystemUi,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "重启 SystemUI", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    xposedConnected: Boolean,
    gpuFreqText: String,
    gpuUsageText: String,
    lastUpdateText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardSurface
        ),
        border = BorderStroke(1.dp, CardStroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "运行状态",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MdThemePrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // LSPosed 服务绑定状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LSPosed 服务绑定：",
                    fontSize = 14.sp,
                    color = MdThemeOnSurface,
                    modifier = Modifier.weight(1f)
                )

                val badgeBg = if (xposedConnected) StatusConnectedBg else StatusDisconnectedBg
                val badgeTextColor = if (xposedConnected) StatusConnected else StatusDisconnected

                Box(
                    modifier = Modifier
                        .background(badgeBg, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (xposedConnected) "已连接" else "未连接 (Remote 离线)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = CardStroke
            )

            // GPU 数据状态
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GPU 频率 (MHz)",
                        fontSize = 12.sp,
                        color = MdThemeOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = gpuFreqText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MdThemeOnSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GPU 占用率 (%)",
                        fontSize = 12.sp,
                        color = MdThemeOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = gpuUsageText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MdThemeOnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = lastUpdateText,
                fontSize = 11.sp,
                color = MdThemeOnSurfaceVariant
            )
        }
    }
}

@Composable
fun GuidanceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardSurface
        ),
        border = BorderStroke(1.dp, CardStroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "使用须知",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MdThemeOnSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "1. Root 权限：请授权本应用以允许后台采集 GPU 指标。\n2. LSPosed 作用域：必须勾选「系统界面 (SystemUI)」。\n3. 功能变化必须重启作用域。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MdThemeOnSurfaceVariant
            )
        }
    }
}
