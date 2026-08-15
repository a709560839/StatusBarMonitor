package com.jj.statusbarmonitor.service;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * 空的可导出 ContentProvider，用于在进程被拉起时尽早触发采集初始化。
 * <p>
 * ContentProvider.onCreate() 在 Application.onCreate() 之前被系统调用，
 * 且当外部进程（如 SystemUI hook 侧）通过 ContentResolver.query() 访问
 * 该 Provider 的 authority 时，系统会自动拉起模块进程，无需等待 BOOT_COMPLETED。
 * <p>
 * 所有 query/insert/update/delete 均为空实现，仅借用进程启动的副作用。
 */
public class EarlyInitProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        GpuCollectorLauncher.startInProcess();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
