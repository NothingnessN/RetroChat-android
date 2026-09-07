package com.nothingnessn.retrochat;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

public final class UiUtil {

    private UiUtil() {}

    public static void setupEdgeToEdge(final Activity activity) {
        if (activity == null) return;
        try {
            activity.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        } catch (Throwable ignored) {}

        final ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
        if (content == null) return;

        try {
            content.setFitsSystemWindows(false);
        } catch (Throwable ignored) {}

        final int baseL = content.getPaddingLeft();
        final int baseT = content.getPaddingTop();
        final int baseR = content.getPaddingRight();
        final int baseB = content.getPaddingBottom();

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public android.view.WindowInsets onApplyWindowInsets(
                            View v, android.view.WindowInsets insets) {
                        android.graphics.Insets bars = insets.getInsets(
                                android.view.WindowInsets.Type.statusBars()
                                        | android.view.WindowInsets.Type.navigationBars());
                        android.graphics.Insets ime = insets.getInsets(
                                android.view.WindowInsets.Type.ime());
                        int bottom = Math.max(bars.bottom, ime.bottom);
                        v.setPadding(baseL + bars.left, baseT + bars.top,
                                baseR + bars.right, baseB + bottom);
                        return insets;
                    }
                });
                content.requestApplyInsets();
            } catch (Throwable ignored) {}
        }

        final View decor = content.getRootView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    private int lastPad = -1;

                    @Override
                    public void onGlobalLayout() {

                        try {
                            Rect visible = new Rect();
                            decor.getWindowVisibleDisplayFrame(visible);

                            int[] loc = new int[2];
                            content.getLocationOnScreen(loc);
                            int contentBottom = loc[1] + content.getHeight();
                            int overlap = contentBottom - visible.bottom;

                            int need = overlap > 80 ? overlap : 0;

                            if (need == lastPad) return;
                            lastPad = need;

                            if (Build.VERSION.SDK_INT >= 30 && need == 0) {

                                return;
                            }

                            if (need > 0) {
                                content.setPadding(baseL, baseT, baseR, baseB + need);
                            } else if (Build.VERSION.SDK_INT < 30) {
                                content.setPadding(baseL, baseT, baseR, baseB);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
    }

    public static boolean looksLikeProtocolKind(String s) {
        if (s == null) return false;
        return "private".equals(s) || "group".equals(s);
    }

    public static boolean looksLikeRealPeer(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 2) return false;
        if ("?".equals(t) || "0".equals(t) || "1".equals(t)) return false;
        if (looksLikeProtocolKind(t)) return false;
        boolean allDigits = true;
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) {
                allDigits = false;
                break;
            }
        }
        if (allDigits && t.length() < 3) return false;
        if (allDigits && t.length() >= 12 && t.length() <= 13) return false;
        return true;
    }

    public static boolean looksLikeRealMedia(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 3) return false;
        if ("0".equals(t) || "1".equals(t) || "?".equals(t)) return false;
        if (looksLikeProtocolKind(t)) return false;
        boolean allDigits = true;
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) {
                allDigits = false;
                break;
            }
        }
        if (allDigits) return false;
        return true;
    }

    public static boolean looksLikeFlagLeak(String s) {
        if (s == null) return true;
        String t = s.trim();
        return t.length() == 0 || "0".equals(t) || "1".equals(t);
    }
}
