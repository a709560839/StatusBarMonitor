package com.jj.statusbarmonitor.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jj.statusbarmonitor.collector.PerformanceCollector;
import com.jj.statusbarmonitor.constant.Constants;

import java.util.Locale;

/**
 * 状态栏顶部性能监视条：CPU/GPU 柱条 + 文字指标。
 * 运行于 SystemUI 进程，由 {@link com.jj.statusbarmonitor.xposed.ModuleMain} 注入。
 */
public class MonitorView extends LinearLayout {

    private TextView cpuIcon;
    private MultiCoreBarView cpuBar;
    private TextView cpuFreqMinText;
    private TextView cpuFreqMaxText;
    private TextView cpuUsageText;
    private TextView cpuLeftParentheses;
    private TextView cpuTextView;
    private TextView cpuRightParentheses;
    private TextView gpuLeftParentheses;
    private TextView gpuRightParentheses;

    private TextView gpuIcon;
    private MultiCoreBarView gpuBar;
    private TextView gpuFreqText;
    private TextView gpuUsageText;

    private TextView fpsIcon;
    private TextView fpsText;

    private TextView ramIcon;
    private TextView ramText;
    private TextView zramIcon;
    private TextView zramText;

    private TextView batTempIcon;
    private TextView batTempText;
    private TextView batPowerIcon;
    private TextView batPowerText;

    private int currentTint = Color.WHITE;
    private PerformanceCollector collector;

    public MonitorView(Context context) {
        super(context);
        init(context);
    }

    /**
     * 构建子 View 与 {@link PerformanceCollector}
     */
    private void init(Context context) {
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER);

        cpuIcon = createIconView(context, "C");
        cpuBar = new MultiCoreBarView(context);
        setupBarView(cpuBar, dp2px(Constants.Ui.CPU_BAR_WIDTH_DP), dp2px(Constants.Ui.BAR_HEIGHT_DP));
        cpuUsageText = createValueTextView(context, Constants.Ui.PROBE_USAGE, Gravity.CENTER);
        cpuLeftParentheses = createParenthesesTextView(context,"(");
        cpuFreqMinText = createValueTextView(context, Constants.Ui.PROBE_CPU_FREQ, Gravity.CENTER);
        cpuTextView = createParenthesesTextView(context,"-");
        cpuFreqMaxText = createValueTextView(context, Constants.Ui.PROBE_CPU_FREQ, Gravity.CENTER);
        cpuRightParentheses = createParenthesesTextView(context,")");

        gpuIcon = createIconView(context, "G");
        gpuBar = new MultiCoreBarView(context);
        setupBarView(gpuBar, dp2px(Constants.Ui.GPU_BAR_WIDTH_DP), dp2px(Constants.Ui.BAR_HEIGHT_DP));
        gpuUsageText = createValueTextView(context, Constants.Ui.PROBE_USAGE, Gravity.CENTER);
        gpuLeftParentheses = createParenthesesTextView(context,"(");
        gpuFreqText = createValueTextView(context, Constants.Ui.PROBE_GPU, Gravity.CENTER);
        gpuRightParentheses = createParenthesesTextView(context,")");

        ramIcon = createIconView(context, "R");
        ramText = createValueTextView(context, Constants.Ui.PROBE_PCT, Gravity.START);
        zramIcon = createIconView(context, "Z");
        zramText = createValueTextView(context, Constants.Ui.PROBE_PCT, Gravity.START);

        batTempIcon = createIconView(context, "T");
        batTempText = createValueTextView(context, Constants.Ui.PROBE_TEMP, Gravity.START);
        batPowerIcon = createIconView(context, "P");
        batPowerText = createValueTextView(context, Constants.Ui.PROBE_POWER, Gravity.START);

        fpsIcon = createIconView(context, "F");
        fpsText = createValueTextView(context, Constants.Ui.PROBE_FPS, Gravity.START);

        addCpuRow();
        addGpuRow();
        addTextOnlyRow(ramIcon, ramText);
        addTextOnlyRow(zramIcon, zramText);
        addTextOnlyRow(batTempIcon, batTempText);
        addTextOnlyRow(batPowerIcon, batPowerText);
        addTextOnlyRow(fpsIcon, fpsText);

        applyTintColor();

        collector = new PerformanceCollector(context, this::updateData);
    }

    /**
     * CPU：使用率与最大频率分两个 TextView，避免单宽列右侧留空
     */
    private void addCpuRow() {
        addView(cpuIcon);
        addView(cpuBar);
        addView(cpuUsageText);
        addView(cpuLeftParentheses);
        addView(cpuFreqMinText);
        addView(cpuTextView);
        addView(cpuFreqMaxText);
        addView(cpuRightParentheses);
        addDivider();
    }

    private void addGpuRow() {
        addView(gpuIcon);
        addView(gpuBar);
        addView(gpuUsageText);
        addView(gpuLeftParentheses);
        addView(gpuFreqText);
        addView(gpuRightParentheses);
        addDivider();
    }

    private void addTextOnlyRow(View icon, View text) {
        addView(icon);
        addView(text);
        addDivider();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (collector != null) {
            collector.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (collector != null) {
            collector.stop();
        }
    }

    private TextView createTextView(Context context) {
        TextView tv = new TextView(context);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, Constants.Ui.TEXT_SIZE_SP);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setIncludeFontPadding(false);
        tv.setSingleLine(true);
        return tv;
    }

    /**
     * 用样例字符串测量像素宽度，固定列宽，避免数值变化时布局跳动。
     */
    private TextView createValueTextView(Context context, String widthProbe, int gravity) {
        TextView tv = createTextView(context);
        int widthPx = (int) Math.ceil(tv.getPaint().measureText(widthProbe));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setGravity(gravity);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView createParenthesesTextView(Context context, String text) {
        TextView tv = createTextView(context);
        tv.setText(text);
        return tv;
    }

    /**
     * 从右边补充空格到于探针等长，等宽字体下实现左对齐固定宽度
     */
    private static String fixed(String widthProbe, String value) {
        int width = widthProbe.length();
        if (value.length() >= width) {
            return value.substring(0, width);
        }
        StringBuilder sb = new StringBuilder(width);
        sb.append(value);
        for (int i = value.length(); i < width; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String formatFps(float fps) {
        if (fps >= Constants.Config.FPS_INTEGER_THRESHOLD) {
            return String.format(Locale.US, "%.0f", fps);
        }
        return String.format(Locale.US, "%.1f", fps);
    }

    @SuppressLint("SetTextI18n")
    private TextView createIconView(Context context, String label) {
        TextView tv = createTextView(context);
        tv.setText(label + ":");
        return tv;
    }

    private void setupBarView(MultiCoreBarView bar, int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        int margin = dp2px(Constants.Ui.BAR_HORIZONTAL_MARGIN_DP);
        params.leftMargin = margin;
        params.rightMargin = margin;
        bar.setLayoutParams(params);
    }

    private void addDivider() {
        View divider = new View(getContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(dp2px(Constants.Ui.DIVIDER_WIDTH_DP), 1));
        addView(divider);
    }

    private int dp2px(float dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 由 {@link PerformanceCollector} 周期性回调，刷新各指标文字与柱条
     */
    public void updateData(PerformanceCollector.PerformanceData data) {
        cpuBar.updateUsages(data.cpuCoreUsages);
        gpuBar.updateUsages(data.gpuCoreUsages);

        cpuUsageText.setText(String.format(Locale.US, "%d%%", (int) data.cpuTotalUsage));
        cpuFreqMinText.setText(String.format(Locale.US, "%d", data.cpuFreqMinMhz));
        cpuFreqMaxText.setText(String.format(Locale.US, "%d", data.cpuFreqMaxMhz));

        gpuFreqText.setText(String.format(Locale.US, "%d", data.gpuFreq));
        gpuUsageText.setText(String.format(Locale.US, "%d%%", data.gpuTotalUsage));

        fpsText.setText(fixed(Constants.Ui.PROBE_FPS, formatFps(data.fps)));
        ramText.setText(fixed(Constants.Ui.PROBE_PCT, String.format(Locale.US, "%d%%", (int) data.ramUsagePercent)));
        zramText.setText(fixed(Constants.Ui.PROBE_PCT, String.format(Locale.US, "%d%%", (int) data.zramUsagePercent)));
        batTempText.setText(fixed(Constants.Ui.PROBE_TEMP, String.format(Locale.US, "%.1f°C", data.batteryTemp)));
        batPowerText.setText(fixed(Constants.Ui.PROBE_POWER, String.format(Locale.US, "%+.2fW", data.batteryPowerW)));
    }

    /**
     * 由 {@link com.jj.statusbarmonitor.bridge.StatusBarTintBridge} 同步状态栏反色
     */
    public void onColorsChanged(int tint) {
        currentTint = tint;
        applyTintColor();
    }

    private void applyTintColor() {
        int t = currentTint;

        cpuBar.setTintColor(t);
        gpuBar.setTintColor(t);

        cpuIcon.setTextColor(t);
        cpuUsageText.setTextColor(t);
        cpuFreqMinText.setTextColor(t);
        cpuTextView.setTextColor(t);
        cpuFreqMaxText.setTextColor(t);
        cpuLeftParentheses.setTextColor(t);
        cpuRightParentheses.setTextColor(t);

        gpuIcon.setTextColor(t);
        gpuFreqText.setTextColor(t);
        gpuUsageText.setTextColor(t);
        gpuLeftParentheses.setTextColor(t);
        gpuRightParentheses.setTextColor(t);

        fpsIcon.setTextColor(t);
        fpsText.setTextColor(t);

        ramIcon.setTextColor(t);
        ramText.setTextColor(t);

        zramIcon.setTextColor(t);
        zramText.setTextColor(t);

        batTempIcon.setTextColor(t);
        batTempText.setTextColor(t);

        batPowerIcon.setTextColor(t);
        batPowerText.setTextColor(t);
    }
}
