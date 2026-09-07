package com.nothingnessn.retrochat;

import android.app.Application;
import android.content.Context;
import android.os.Build;

public class RetroChatApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        try {
            Class<?> c = Class.forName("androidx.multidex.MultiDex");
            c.getMethod("install", Context.class).invoke(null, this);
        } catch (Throwable ignored) {

        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NotifyHelper.ensureChannels(this);
        } catch (Throwable ignored) {}

        try {
            if (Prefs.isLoggedIn(this) && Build.VERSION.SDK_INT >= 16) {
                PollService.start(this);
            }
        } catch (Throwable ignored) {}
    }
}
