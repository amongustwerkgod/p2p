package com.example.Peerly;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class EarthView extends View {

    private Paint oceanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint landPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint atmoPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float rotation   = 0f;
    private ValueAnimator rotAnim;

    // Continent blobs: [relX, relY, relRx, relRy, tiltDeg]
    private final float[][] continents = {
        { -0.08f, -0.18f, 0.27f, 0.22f,  10f },
        { -0.48f, -0.20f, 0.18f, 0.30f, -5f  },
        {  0.08f,  0.22f, 0.20f, 0.17f,  15f },
        { -0.40f,  0.32f, 0.15f, 0.12f,   5f },
        {  0.36f, -0.30f, 0.14f, 0.22f, -10f },
    };

    public EarthView(Context ctx) { super(ctx); init(); }
    public EarthView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }

    private void init() {
        landPaint.setColor(0xFF1e6640);
        rotAnim = ValueAnimator.ofFloat(0f, (float)(Math.PI * 2));
        rotAnim.setDuration(30_000);
        rotAnim.setRepeatCount(ValueAnimator.INFINITE);
        rotAnim.setInterpolator(new LinearInterpolator());
        rotAnim.addUpdateListener(a -> {
            rotation = (float) a.getAnimatedValue();
            invalidate();
        });
        rotAnim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r  = Math.min(w, h) / 2f * 0.88f;

        // Ocean gradient - Fixed to use 6-argument constructor compatible with int[] and API 21+
        RadialGradient ocean = new RadialGradient(
            cx - r*0.2f, cy - r*0.25f, r,
            new int[]{ 0xFF1a3a6a, 0xFF0d2040, 0xFF060f20 },
            new float[]{ 0f, 0.5f, 1f },
            Shader.TileMode.CLAMP);
        oceanPaint.setShader(ocean);
        canvas.drawCircle(cx, cy, r, oceanPaint);

        // Clip and draw continents
        canvas.save();
        Path clip = new Path();
        clip.addCircle(cx, cy, r, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.translate(cx, cy);
        canvas.rotate((float) Math.toDegrees(rotation));
        for (float[] c : continents) {
            canvas.save();
            canvas.rotate(c[4]);
            canvas.drawOval(
                new RectF((c[0]-c[2])*r, (c[1]-c[3])*r,
                          (c[0]+c[2])*r, (c[1]+c[3])*r),
                landPaint);
            canvas.restore();
        }
        canvas.restore();

        // Atmosphere ring - Fixed to use 6-argument constructor
        RadialGradient atmo = new RadialGradient(
            cx, cy, r * 1.12f,
            new int[]{ 0x003C78FF, 0x1A3C78FF, 0x003C78FF },
            new float[]{ 0.85f/1.12f, (0.85f+1.12f)/(2*1.12f), 1f },
            Shader.TileMode.CLAMP);
        atmoPaint.setShader(atmo);
        canvas.drawCircle(cx, cy, r * 1.12f, atmoPaint);

        // Shine highlight - Fixed to use 6-argument constructor
        RadialGradient shine = new RadialGradient(
            cx - r*0.25f, cy - r*0.30f, r,
            new int[]{ 0x2D96C8FF, 0x00000000 },
            null, Shader.TileMode.CLAMP);
        shinePaint.setShader(shine);
        canvas.drawCircle(cx, cy, r, shinePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (rotAnim != null) rotAnim.cancel();
    }
}
