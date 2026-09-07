package com.nothingnessn.retrochat;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class PollService extends Service {

    public static final String ACTION_CHATS_UPDATED =
            "com.nothingnessn.retrochat.CHATS_UPDATED";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable loop;
    private boolean startedFg = false;

    public static void start(Context ctx) {
        if (ctx == null) return;
        try {
            if (!Prefs.isLoggedIn(ctx)) return;
            Intent i = new Intent(ctx, PollService.class);

            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Throwable ignored) {}
    }

    public static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, PollService.class));
        } catch (Throwable ignored) {}
    }

    public static void notifyUi(Context ctx) {
        try {
            Intent i = new Intent(ACTION_CHATS_UPDATED);

            i.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(i);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NotifyHelper.ensureChannels(this);
            enterForeground();
        } catch (Throwable ignored) {}
        loop = new Runnable() {
            @Override
            public void run() {
                try {
                    pollOnce();
                } catch (Throwable ignored) {}
                handler.postDelayed(this, 8000);
            }
        };
        handler.postDelayed(loop, 3000);
    }

    private void enterForeground() {
        if (startedFg) return;
        startedFg = true;
        try {
            android.app.Notification n = NotifyHelper.buildForeground(this);

            startForeground(NotifyHelper.ID_FOREGROUND, n);
        } catch (Throwable t) {

            startedFg = false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            enterForeground();
        } catch (Throwable ignored) {}
        return START_STICKY;
    }

    private void pollOnce() {
        try {
            if (!Prefs.isLoggedIn(this)) {
                stopSelf();
                return;
            }
            final String uuid = Prefs.getUUID(this);
            final Context app = getApplicationContext();
            Network.fetchChats(uuid, new Network.Callback() {
                @Override
                public void onSuccess(String result) {
                    try {
                        boolean changed = apply(app, result);
                        if (changed) notifyUi(app);
                    } catch (Throwable ignored) {}
                }
                @Override
                public void onError(String error) {}
            });
        } catch (Throwable ignored) {}
    }

    private static boolean apply(Context ctx, String result) {
        if (result == null || result.startsWith("ERROR") || result.equals("EMPTY")) return false;
        long last = Prefs.getPollTs(ctx);
        long max = last;
        boolean anyNotify = false;
        boolean anyUpsert = false;
        LocalDb db = null;
        try {
            db = new LocalDb(ctx);
            String[] lines = result.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.length() == 0) continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 5) continue;
                String kind = p[0];
                if (!UiUtil.looksLikeProtocolKind(kind)) continue;
                String id = p[1];
                String title = p[2];
                String preview = p[3];
                long ts = 0;
                try { ts = Long.parseLong(p[4].trim()); } catch (Exception ignored) {}
                boolean fromMe = p.length > 6 && "1".equals(p[6]);
                boolean group = "group".equals(kind);

                if (!UiUtil.looksLikeRealPeer(id)) continue;
                if (ts > max) max = ts;
                db.upsertChat(id, title, preview, "", ts > 0 ? ts : System.currentTimeMillis(),
                        group, group ? id : null);
                anyUpsert = true;
                if (last > 0 && ts > last && !fromMe) {
                    NotifyHelper.showMessage(ctx, id, title, preview, group);
                    anyNotify = true;
                }
            }
        } finally {
            if (db != null) {
                try { db.close(); } catch (Throwable ignored) {}
            }
        }
        if (max > last) {
            Prefs.setPollTs(ctx, max);
            return true;
        }
        if (last == 0 && max > 0) {
            Prefs.setPollTs(ctx, max);
            return anyUpsert;
        }
        return anyNotify || anyUpsert;
    }

    @Override
    public void onDestroy() {
        if (loop != null) handler.removeCallbacks(loop);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
