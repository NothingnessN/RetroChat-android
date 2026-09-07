package com.nothingnessn.retrochat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

public final class AppTheme {

    public static final int ID_DEFAULT = 0;
    public static final int ID_AURORA = 1;
    public static final int ID_AERO = 2;
    public static final int ID_MIRC = 3;
    public static final int ID_MIDNIGHT = 4;

    public final int id;
    public final String name;
    public final int bg;
    public final int headerBg;
    public final int headerText;
    public final int bubbleMine;
    public final int bubbleOther;
    public final int bubbleText;
    public final int bubbleTextOther;
    public final int timeText;
    public final int inputBg;
    public final int inputBarBg;
    public final int accent;
    public final int accent2;
    public final int statusText;
    public final int replyBg;
    public final int replyBar;
    public final int replyText;
    public final int chatRowTitle;
    public final int chatRowPreview;
    public final int borderSoft;
    public final boolean terminalMode;
    public final int cornerRadiusDp;

    private AppTheme(int id, String name,
                     int bg, int headerBg, int headerText,
                     int bubbleMine, int bubbleOther,
                     int bubbleText, int bubbleTextOther, int timeText,
                     int inputBg, int inputBarBg,
                     int accent, int accent2, int statusText,
                     int replyBg, int replyBar, int replyText,
                     int chatRowTitle, int chatRowPreview, int borderSoft,
                     boolean terminalMode, int cornerRadiusDp) {
        this.id = id;
        this.name = name;
        this.bg = bg;
        this.headerBg = headerBg;
        this.headerText = headerText;
        this.bubbleMine = bubbleMine;
        this.bubbleOther = bubbleOther;
        this.bubbleText = bubbleText;
        this.bubbleTextOther = bubbleTextOther;
        this.timeText = timeText;
        this.inputBg = inputBg;
        this.inputBarBg = inputBarBg;
        this.accent = accent;
        this.accent2 = accent2;
        this.statusText = statusText;
        this.replyBg = replyBg;
        this.replyBar = replyBar;
        this.replyText = replyText;
        this.chatRowTitle = chatRowTitle;
        this.chatRowPreview = chatRowPreview;
        this.borderSoft = borderSoft;
        this.terminalMode = terminalMode;
        this.cornerRadiusDp = cornerRadiusDp;
    }

    public static AppTheme get(int id) {
        switch (id) {
            case ID_AURORA: return aurora();
            case ID_AERO: return aero();
            case ID_MIRC: return mirc();
            case ID_MIDNIGHT: return midnight();
            default: return defaultTheme();
        }
    }

    public static AppTheme[] all() {
        return new AppTheme[]{ defaultTheme(), aurora(), aero(), mirc(), midnight() };
    }

    public static AppTheme defaultTheme() {
        return new AppTheme(ID_DEFAULT, "Default",
                0xFF000000, 0xFF660099, 0xFFFFFFFF,
                0xFF660099, 0xFF1E1E1E,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFBBBBBB,
                0xFF1E1E1E, 0xFF121212,
                0xFFFF08FC, 0xFF660099, 0xFFAAAAAA,
                0xFF2A1A3A, 0xFFFF08FC, 0xFFE0B0FF,
                0xFFFFFFFF, 0xFFCCCCCC, 0x33FFFFFF,
                false, 16);
    }

    public static AppTheme aurora() {

        return new AppTheme(ID_AURORA, "Aurora",
                0xFF050510, 0xCC660099, 0xFFFFFFFF,
                0xFF7B1FA2, 0xFF15202B,
                0xFFFFFFFF, 0xFFE8F5E9, 0xFFB0BEC5,
                0xFF1A1A2E, 0xFF0D0D1A,
                0xFFFF8CFF, 0xFF3BE8A7, 0xFF90A4AE,
                0xFF1E1530, 0xFF3BE8A7, 0xFFB2FFDF,
                0xFFFF8CFF, 0xFFB0BEC5, 0x443BE8A7,
                false, 20);
    }

    public static AppTheme aero() {

        return new AppTheme(ID_AERO, "Aero Glass",
                0xFF0A1A30, 0xAA3A8BC8, 0xFFFFFFFF,
                0xB35BA3D9, 0xD0F0F7FF,
                0xFFFFFFFF, 0xFF0D2137, 0xFF4A6A88,
                0xCCF5FAFF, 0x993A7AB0,
                0xFF9AD4F5, 0xFF2B6CB0, 0xFFE3F2FD,
                0xB3E3F2FD, 0xFF7EC8F3, 0xFF16324D,
                0xFFFFFFFF, 0xFFD0E6F5, 0xB3FFFFFF,
                false, 18);
    }

    public static AppTheme mirc() {

        return new AppTheme(ID_MIRC, "mIRC Amber",
                0xFF000000, 0xFF111111, 0xFFFFB000,
                0xFF000000, 0xFF000000,
                0xFFE0E0E0, 0xFFE0E0E0, 0xFF555555,
                0xFF0A0A0A, 0xFF000000,
                0xFFFFB000, 0xFF00E5FF, 0xFF555555,
                0xFF111111, 0xFFFFB000, 0xFFFFB000,
                0xFFFFB000, 0xFFAAAAAA, 0xFF333333,
                true, 0);
    }

    public static AppTheme midnight() {
        return new AppTheme(ID_MIDNIGHT, "Midnight Purple",
                0xFF0A0814, 0xFF1A1030, 0xFFE1BEE7,
                0xFF6A0DAD, 0xFF1D162B,
                0xFFFFFFFF, 0xFFE1BEE7, 0xFFB39DDB,
                0xFF161022, 0xFF0F0C1A,
                0xFFE1BEE7, 0xFF9C27B0, 0xFFCE93D8,
                0xFF251A38, 0xFFE1BEE7, 0xFFD1C4E9,
                0xFFE1BEE7, 0xFFB39DDB, 0x55E1BEE7,
                false, 18);
    }

    public GradientDrawable bubbleDrawable(boolean mine) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(mine ? bubbleMine : bubbleOther);
        float r = cornerRadiusDp;
        if (terminalMode) {
            d.setCornerRadius(0);
        } else {

            float rad = r * 2.5f;
            d.setCornerRadius(rad);
            if (id == ID_AERO) {

                d.setStroke(2, 0x99FFFFFF);
            } else if (borderSoft != 0 && id == ID_MIDNIGHT) {
                d.setStroke(2, borderSoft);
            }
        }
        return d;
    }

    public android.graphics.drawable.Drawable glassBubble(boolean mine, float density) {
        float rad = Math.max(8f, cornerRadiusDp * density);

        GradientDrawable body = new GradientDrawable();
        body.setShape(GradientDrawable.RECTANGLE);
        body.setCornerRadius(rad);
        if (mine) {
            body.setColor(0xB35BA3D9);
        } else {
            body.setColor(0xCCE8F4FC);
        }
        body.setStroke(Math.max(1, (int) (1.5f * density)), 0xA0FFFFFF);

        GradientDrawable shine = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ 0x66FFFFFF, 0x11FFFFFF, 0x00FFFFFF });
        shine.setShape(GradientDrawable.RECTANGLE);
        shine.setCornerRadius(rad);

        android.graphics.drawable.Drawable[] layers =
                new android.graphics.drawable.Drawable[]{ body, shine };
        android.graphics.drawable.LayerDrawable ld =
                new android.graphics.drawable.LayerDrawable(layers);
        return ld;
    }

    public GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public void applyTerminalFont(TextView tv) {
        if (tv == null) return;
        if (terminalMode) {
            tv.setTypeface(Typeface.MONOSPACE);
        } else {
            tv.setTypeface(Typeface.SANS_SERIF);
        }
    }

    public static int dp(View v, int dp) {
        float d = v.getResources().getDisplayMetrics().density;
        return (int) (dp * d + 0.5f);
    }
}
