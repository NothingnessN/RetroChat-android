package com.nothingnessn.retrochat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

public class AuroraBlobView extends View {

    private static final int N = 3;
    private final float[] cx = new float[N];
    private final float[] cy = new float[N];
    private final float[] vx = new float[N];
    private final float[] vy = new float[N];
    private final float[] rad = new float[N];
    private final int[] colors = new int[]{ 0xFFFF0099, 0xFF00E676, 0xFF00E5FF };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private long lastTs;
    private boolean inited;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            float dt = lastTs == 0 ? 0.016f : Math.min(0.05f, (now - lastTs) / 1000f);
            lastTs = now;
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                for (int i = 0; i < N; i++) {
                    cx[i] += vx[i] * dt;
                    cy[i] += vy[i] * dt;
                    float r = rad[i];
                    if (cx[i] < r) { cx[i] = r; vx[i] = Math.abs(vx[i]); }
                    if (cx[i] > w - r) { cx[i] = w - r; vx[i] = -Math.abs(vx[i]); }
                    if (cy[i] < r) { cy[i] = r; vy[i] = Math.abs(vy[i]); }
                    if (cy[i] > h - r) { cy[i] = h - r; vy[i] = -Math.abs(vy[i]); }
                }
                invalidate();
            }
            handler.postDelayed(this, 66);
        }
    };

    public AuroraBlobView(Context c) { super(c); setWillNotDraw(false); }
    public AuroraBlobView(Context c, AttributeSet a) { super(c, a); setWillNotDraw(false); }

    private void ensureInit(int w, int h) {
        if (inited || w <= 0 || h <= 0) return;
        inited = true;
        float min = Math.min(w, h);
        for (int i = 0; i < N; i++) {
            rad[i] = min * (0.22f + 0.08f * i);
            cx[i] = w * (0.25f + 0.25f * i);
            cy[i] = h * (0.3f + 0.2f * (i % 2));
            float speed = min * (0.04f + 0.02f * i);
            vx[i] = (i % 2 == 0 ? 1 : -1) * speed;
            vy[i] = (i % 3 == 0 ? -1 : 1) * speed * 0.7f;
        }
    }

    public void start() {
        if (running) return;
        running = true;
        lastTs = 0;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        inited = false;
        ensureInit(w, h);
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        ensureInit(w, h);

        canvas.drawColor(0xFF050510);
        for (int i = 0; i < N; i++) {
            int col = colors[i];
            int opaque = (col & 0x00FFFFFF) | 0x99000000;
            int soft = (col & 0x00FFFFFF) | 0x33000000;
            float r = rad[i];
            RadialGradient g = new RadialGradient(
                    cx[i], cy[i], r,
                    new int[]{ opaque, soft, 0x00000000 },
                    new float[]{ 0f, 0.45f, 1f },
                    Shader.TileMode.CLAMP);
            paint.setShader(g);
            canvas.drawCircle(cx[i], cy[i], r, paint);
            paint.setShader(null);
        }
    }
}
