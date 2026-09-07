package com.nothingnessn.retrochat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (context == null || intent == null) return;
            String action = intent.getAction();
            if (action == null) return;
            if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                    && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
                return;
            }
            if (!Prefs.isLoggedIn(context)) return;

            if (Build.VERSION.SDK_INT < 15) return;
            PollService.start(context.getApplicationContext());
        } catch (Throwable ignored) {

        }
    }
}
