package com.jj.statusbarmonitor.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * 持久化 root shell。
 *
 * <p>启动一个长驻的 {@code su} 进程，通过 stdin/stdout 管道复用，
 * 避免每次读取 sysfs 都 fork+exec 的开销，同时保持 su/Magisk 的 SELinux 域权限，
 * 从而绕过 {@code untrusted_app} 对 {@code vendor_sysfs_kgsl} 的访问限制。
 *
 * <p>线程安全：内部通过 {@code synchronized} 序列化命令执行。
 */
public final class RootShell {

    /**
     * 不含任何 shell 特殊字符（< > | & ; ` $ ( ) { } [ ] * ? !），
     * 以避免被 shell 误解析为重定向/操作符。
     */
    private static final String SENTINEL = "ROOTSHELL_DONE_OK";

    private static final String[] SU_CANDIDATES = {
            "su", "/system/bin/su", "/sbin/su", "/system/xbin/su"
    };
    private static final long CMD_TIMEOUT_MS = 600;

    private static volatile RootShell sInstance;

    private Process process;
    private PrintWriter writer;
    private BufferedReader reader;
    private volatile boolean dead = false;

    private RootShell() {}

    public static RootShell get() {
        RootShell inst = sInstance;
        if (inst == null || inst.dead) {
            synchronized (RootShell.class) {
                inst = sInstance;
                if (inst == null || inst.dead) {
                    sInstance = inst = start();
                }
            }
        }
        return inst;
    }

    private static RootShell start() {
        for (String su : SU_CANDIDATES) {
            try {
                Process p = new ProcessBuilder(su)
                        .redirectErrorStream(false)
                        .start();
                RootShell shell = new RootShell();
                shell.process = p;
                shell.writer = new PrintWriter(
                        new OutputStreamWriter(p.getOutputStream(), "UTF-8"), true);
                shell.reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), "UTF-8"));
                if (shell.probe()) {
                    return shell;
                }
                p.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
        RootShell dead = new RootShell();
        dead.dead = true;
        return dead;
    }

    private boolean probe() {
        String result = exec("echo probe_ok");
        return "probe_ok".equals(result);
    }

    /**
     * 执行单行命令，返回第一行输出；失败或超时返回 null。
     *
     * <p>协议：发送命令后紧接着发 {@code echo SENTINEL}，以 SENTINEL 行作为输出结束标记。
     */
    public synchronized String exec(String command) {
        if (dead) return null;
        try {
            writer.println(command);
            writer.println("echo " + SENTINEL);

            long deadline = System.currentTimeMillis() + CMD_TIMEOUT_MS;
            String firstLine = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (System.currentTimeMillis() > deadline) {
                    markDead();
                    return null;
                }
                if (SENTINEL.equals(line)) {
                    return firstLine;
                }
                if (firstLine == null && !line.isEmpty()) {
                    firstLine = line;
                }
            }
            markDead();
            return null;
        } catch (IOException e) {
            markDead();
            return null;
        }
    }

    /**
     * 读取 sysfs / procfs 文件的第一行，失败返回 null。
     * stderr 重定向到 /dev/null，避免错误输出干扰协议。
     */
    public String readFirstLine(String path) {
        return exec("cat " + path + " 2>/dev/null");
    }

    private void markDead() {
        dead = true;
        try {
            if (process != null) process.destroyForcibly();
        } catch (Exception ignored) {
        }
    }

    public boolean isDead() {
        return dead;
    }
}
