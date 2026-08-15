package com.jj.statusbarmonitor.utils;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射与按资源名查找 View（用于 SystemUI 注入，无 R.id 编译期引用）。
 */
public final class ReflectUtils {

    private ReflectUtils() {}

    public static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    public static Object getObjectField(Object obj, String fieldName) throws Exception {
        return getField(obj.getClass(), fieldName).get(obj);
    }

    public static int getIntField(Object obj, String fieldName) throws Exception {
        return getField(obj.getClass(), fieldName).getInt(obj);
    }

    /** {@code Dependency.get(Class)} 等静态单参调用 */
    public static Object callStaticNoArgs(Class<?> clazz, String methodName, Object arg) throws Exception {
        Method method = clazz.getMethod(methodName, Class.class);
        return method.invoke(null, arg);
    }

    /**
     * 在 SystemUI 包名下按字符串 id 名查找子 View（如 {@code status_bar}）。
     */
    public static View findViewByIdName(View parent, String idName) {
        if (parent == null || idName == null) {
            return null;
        }
        int resId = parent.getResources().getIdentifier(
                idName, "id", parent.getContext().getPackageName());
        if (resId != 0) {
            return parent.findViewById(resId);
        }
        return null;
    }
}
