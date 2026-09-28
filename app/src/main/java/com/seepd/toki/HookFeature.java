package com.seepd.toki;

import android.content.SharedPreferences;

import java.lang.reflect.Executable;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Shared access to the single libxposed module instance used by every feature group. */
abstract class HookFeature {
    private static final String TAG = "Toki";
    private static volatile String hostVersionName = "unknown";
    private final XposedModule module;

    HookFeature(XposedModule module) {
        this.module = module;
        adoptHostVersion(module);
    }

    private static void adoptHostVersion(XposedModule module) {
        if (!"unknown".equals(hostVersionName) || module == null) {
            return;
        }
        try {
            java.lang.reflect.Field field =
                    module.getClass().getDeclaredField("activeTikTokVersion");
            field.setAccessible(true);
            Object value = field.get(module);
            if (value instanceof String && !((String) value).isEmpty()) {
                hostVersionName = (String) value;
            }
        } catch (Throwable ignored) {
            // Version stays "unknown"; callers fall back to legacy paths.
        }
    }

    protected static boolean isTikTok4713() {
        return "47.1.3".equals(hostVersionName);
    }

    protected static boolean isTikTok4643() {
        return "46.4.3".equals(hostVersionName);
    }

    protected static String hostTikTokVersion() {
        return hostVersionName;
    }

    protected final XposedInterface.HookBuilder hook(Executable executable) {
        return module.hook(executable);
    }

    protected final SharedPreferences getRemotePreferences(String name) {
        return module.getRemotePreferences(name);
    }

    protected final void logInfo(String message) {
        module.log(4, TAG, message);
    }

    protected final void logError(String message, Throwable error) {
        module.log(6, TAG, message, error);
    }
}
