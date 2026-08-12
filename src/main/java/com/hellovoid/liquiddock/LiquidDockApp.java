package com.hellovoid.liquiddock;

import android.app.Application;
import android.content.SharedPreferences;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Module-process service bridge used by the settings UI for API101 Remote Preferences. */
public final class LiquidDockApp extends Application
        implements XposedServiceHelper.OnServiceListener {
    private static volatile XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService value) {
        service = value;
    }

    @Override
    public void onServiceDied(XposedService value) {
        if (service == value) service = null;
    }

    public static XposedService service() {
        return service;
    }

    public static SharedPreferences remotePreferences(String group) {
        XposedService value = service;
        return value != null ? value.getRemotePreferences(group) : null;
    }
}
