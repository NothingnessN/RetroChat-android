package com.nothingnessn.retrochat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;

public class NotifyHelper {

    public static final String CH_MSG = "rc_messages";
    public static final String CH_FG = "rc_foreground";
    public static final int ID_FOREGROUND = 10;

    private static String activeChat = null;
    private static Bitmap largeIcon;

    public static synchronized void setActiveChat(String target) {
        activeChat = target;
    }

    public static synchronized String getActiveChat() {
        return activeChat;
    }

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            android.app.NotificationChannel msg = new android.app.NotificationChannel(
                    CH_MSG, "Mesajlar", NotificationManager.IMPORTANCE_HIGH);
            msg.setDescription("Yeni RetroChat mesajlari");
            msg.enableVibration(true);
            nm.createNotificationChannel(msg);
            android.app.NotificationChannel fg = new android.app.NotificationChannel(
                    CH_FG, "Baglanti", NotificationManager.IMPORTANCE_MIN);
            fg.setDescription("Arka planda dinleme");
            nm.createNotificationChannel(fg);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("deprecation")
    public static Notification buildForeground(Context ctx) {
        ensureChannels(ctx);
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = pending(ctx, 0, i);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder b = new Notification.Builder(ctx, CH_FG)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("RetroChat")
                        .setContentText("Mesajlar dinleniyor")
                        .setContentIntent(pi)
                        .setOngoing(true);
                try { b.setLargeIcon(logo(ctx)); } catch (Throwable ignored) {}
                return b.build();
            }
            if (Build.VERSION.SDK_INT >= 16) {
                Notification.Builder b = new Notification.Builder(ctx)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("RetroChat")
                        .setContentText("Mesajlar dinleniyor")
                        .setContentIntent(pi)
                        .setOngoing(true)
                        .setPriority(Notification.PRIORITY_MIN);
                try { b.setLargeIcon(logo(ctx)); } catch (Throwable ignored) {}
                return b.build();
            }

            Notification.Builder b = new Notification.Builder(ctx)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("RetroChat")
                    .setContentText("Mesajlar dinleniyor")
                    .setContentIntent(pi)
                    .setOngoing(true);
            return b.getNotification();
        } catch (Throwable t) {

            Notification.Builder b = new Notification.Builder(ctx)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("RetroChat")
                    .setContentText("Mesajlar dinleniyor")
                    .setContentIntent(pi)
                    .setOngoing(true);
            if (Build.VERSION.SDK_INT >= 16) return b.build();
            return b.getNotification();
        }
    }

    @SuppressWarnings("deprecation")
    public static void showMessage(Context ctx, String peer, String title, String preview, boolean isGroup) {
        if (peer == null) return;
        try {
            String open = getActiveChat();
            if (open != null && open.equals(peer)) return;
            ensureChannels(ctx);
            String shownTitle = title != null && title.length() > 0 ? title : peer;
            String body = preview != null ? preview : "Yeni mesaj";
            String collapsed = ellipsis(body, 48);
            Intent i = new Intent(ctx, ChatActivity.class);
            i.putExtra("title", shownTitle);
            i.putExtra("target", peer);
            i.putExtra("isGroup", isGroup);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int req = Math.abs(peer.hashCode() & 0x7fffffff);
            PendingIntent pi = pending(ctx, req, i);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                n = new Notification.Builder(ctx, CH_MSG)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle(shownTitle)
                        .setContentText(collapsed)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .build();
            } else if (Build.VERSION.SDK_INT >= 16) {
                n = new Notification.Builder(ctx)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle(shownTitle)
                        .setContentText(collapsed)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .build();
            } else {
                Notification.Builder b = new Notification.Builder(ctx)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle(shownTitle)
                        .setContentText(collapsed)
                        .setContentIntent(pi)
                        .setAutoCancel(true);
                n = b.getNotification();
            }
            nm.notify(1000 + (req % 20000), n);
        } catch (Throwable ignored) {}
    }

    public static void cancelFor(Context ctx, String peer) {
        if (peer == null) return;
        try {
            int req = Math.abs(peer.hashCode() & 0x7fffffff);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(1000 + (req % 20000));
        } catch (Throwable ignored) {}
    }

    public static String ellipsis(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static Bitmap logo(Context ctx) {
        if (largeIcon != null) return largeIcon;
        try {
            int size = 96;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF660099);
            c.drawRect(0, 0, size, size, p);
            p.setColor(0xFFFF08FC);
            c.drawRect(0, size - 10, size, size, p);
            p.setColor(Color.WHITE);
            p.setTextSize(48);
            p.setFakeBoldText(true);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("R", size / 2f, size / 2f + 16, p);
            largeIcon = bmp;
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    private static PendingIntent pending(Context ctx, int req, Intent i) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags = flags | PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(ctx, req, i, flags);
    }
}
