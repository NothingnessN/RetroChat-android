package com.nothingnessn.retrochat;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;

public final class BgHelper {
    private BgHelper() {}

    public static void apply(Activity act, View contentRoot, AppTheme theme) {
        if (act == null || contentRoot == null) return;
        int mode = Prefs.getBgMode(act);

        contentRoot = unwrapAurora(contentRoot);

        if (mode == 1) {
            contentRoot.setBackgroundColor(Prefs.getBgColor(act));
            return;
        }
        if (mode == 2) {
            String path = Prefs.getBgImage(act);
            if (path != null) {
                File f = new File(path);
                if (f.exists()) {
                    try {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inSampleSize = 2;
                        o.inPreferredConfig = Bitmap.Config.RGB_565;
                        Bitmap bmp = BitmapFactory.decodeFile(path, o);
                        if (bmp != null) {
                            contentRoot.setBackgroundDrawable(
                                    new BitmapDrawable(act.getResources(), bmp));
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        if (theme != null && theme.id == AppTheme.ID_AURORA) {

            contentRoot.setBackgroundColor(0x00000000);
            injectAuroraBehind(act, contentRoot);
        } else if (theme != null) {
            contentRoot.setBackgroundColor(theme.bg);
        } else {
            contentRoot.setBackgroundColor(0xFF000000);
        }
    }

    private static View unwrapAurora(View contentRoot) {
        ViewGroup parent = null;
        try {
            parent = (ViewGroup) contentRoot.getParent();
        } catch (Exception ignored) {}
        if (parent == null) return contentRoot;

        if (contentRoot instanceof FrameLayout && contentRoot.getTag() != null
                && "aurora_wrap".equals(contentRoot.getTag())) {
            FrameLayout frame = (FrameLayout) contentRoot;
            View real = null;
            for (int i = 0; i < frame.getChildCount(); i++) {
                View c = frame.getChildAt(i);
                if (c instanceof AuroraBlobView) {
                    ((AuroraBlobView) c).stop();
                } else {
                    real = c;
                }
            }
            if (real != null) {
                int idx = parent.indexOfChild(frame);
                frame.removeAllViews();
                parent.removeView(frame);
                parent.addView(real, idx);
                return real;
            }
            return contentRoot;
        }

        if (parent instanceof FrameLayout && parent.getTag() != null
                && "aurora_wrap".equals(parent.getTag())) {
            FrameLayout frame = (FrameLayout) parent;
            ViewGroup grand = null;
            try { grand = (ViewGroup) frame.getParent(); } catch (Exception ignored) {}
            View real = contentRoot;
            for (int i = frame.getChildCount() - 1; i >= 0; i--) {
                View c = frame.getChildAt(i);
                if (c instanceof AuroraBlobView) {
                    ((AuroraBlobView) c).stop();
                    frame.removeViewAt(i);
                }
            }
            if (grand != null) {
                int idx = grand.indexOfChild(frame);
                frame.removeView(real);
                grand.removeView(frame);
                grand.addView(real, idx);
            }
            return real;
        }

        if (contentRoot instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) contentRoot;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View c = g.getChildAt(i);
                if (c instanceof AuroraBlobView) {
                    ((AuroraBlobView) c).stop();
                    g.removeViewAt(i);
                }
            }
        }
        return contentRoot;
    }

    private static void injectAuroraBehind(Activity act, View contentRoot) {
        ViewGroup parent;
        try {
            parent = (ViewGroup) contentRoot.getParent();
        } catch (Exception e) {
            return;
        }
        if (parent == null) return;

        if (parent instanceof FrameLayout && parent.getTag() != null
                && "aurora_wrap".equals(parent.getTag())) {

            boolean has = false;
            for (int i = 0; i < parent.getChildCount(); i++) {
                if (parent.getChildAt(i) instanceof AuroraBlobView) has = true;
            }
            if (!has) {
                AuroraBlobView v = new AuroraBlobView(act);
                parent.addView(v, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                v.start();
            }
            contentRoot.setBackgroundColor(0x00000000);
            return;
        }

        int idx = parent.indexOfChild(contentRoot);
        ViewGroup.LayoutParams oldLp = contentRoot.getLayoutParams();
        parent.removeView(contentRoot);

        FrameLayout frame = new FrameLayout(act);
        frame.setTag("aurora_wrap");
        frame.setLayoutParams(oldLp != null ? oldLp
                : new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        AuroraBlobView blobs = new AuroraBlobView(act);
        frame.addView(blobs, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        contentRoot.setBackgroundColor(0x00000000);
        frame.addView(contentRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        parent.addView(frame, idx);
        blobs.start();
    }
}
