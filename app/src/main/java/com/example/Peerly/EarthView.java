package com.example.Peerly;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

public class EarthView extends View {

    private Paint oceanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint landPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint atmoPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float rotation   = 0f;
    private ValueAnimator rotAnim;
    
    // Zoom factor: 0.0 = Star-like, 1.0 = Full Earth
    private float zoomFactor = 1.0f;

    // Realistic continent blobs: [relX, relY, relRx, relRy, tiltDeg]
    private final float[][] continents = {
        { -0.15f, -0.25f, 0.35f, 0.25f,  15f }, // N. America
        { -0.05f,  0.25f, 0.22f, 0.30f, -10f }, // S. America
        {  0.25f, -0.15f, 0.28f, 0.25f,  20f }, // Eurasia
        {  0.28f,  0.20f, 0.25f, 0.32f,   0f }, // Africa
        {  0.55f,  0.40f, 0.18f, 0.15f, -15f }, // Australia
        {  0.00f,  0.85f, 0.60f, 0.15f,   0f }, // Antarctica
    };

    public EarthView(Context ctx) { super(ctx); init(); }
    public EarthView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }

    private void init() {
        landPaint.setColor(0xFF2E5A27); // Deep Forest Green
        cloudPaint.setColor(0x88FFFFFF); // Semi-transparent white
        
        rotAnim = ValueAnimator.ofFloat(0f, (float)(Math.PI * 2));
        rotAnim.setDuration(45_000);
        rotAnim.setRepeatCount(ValueAnimator.INFINITE);
        rotAnim.setInterpolator(new LinearInterpolator());
        rotAnim.addUpdateListener(a -> {
            rotation = (float) a.getAnimatedValue();
            invalidate();
        });
        rotAnim.start();
    }

    public void setZoomFactor(float zoom) {
        this.zoomFactor = zoom;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        
        // Base radius for full Earth
        float fullR = Math.min(w, h) / 2f * 0.85f;
        
        // Actual radius based on zoom
        float r = (zoomFactor < 0.05f) ? (fullR * 0.02f) : (fullR * zoomFactor);
        
        if (zoomFactor < 0.05f) {
            // Draw as a bright star
            Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            starPaint.setColor(Color.WHITE);
            starPaint.setShadowLayer(15f, 0, 0, Color.WHITE);
            canvas.drawCircle(cx, cy, r, starPaint);
            return;
        }

        // Realistic Ocean (Deep blue with subtle gradient, no purple)
        RadialGradient ocean = new RadialGradient(
            cx - r*0.1f, cy - r*0.15f, r * 1.2f,
            new int[]{ 0xFF1B3D6D, 0xFF0B1B33, 0xFF050B1A },
            new float[]{ 0f, 0.6f, 1f },
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
        
        // Land
        for (float[] c : continents) {
            canvas.save();
            canvas.rotate(c[4]);
            canvas.drawOval(
                new RectF((c[0]-c[2])*r, (c[1]-c[3])*r,
                          (c[0]+c[2])*r, (c[1]+c[3])*r),
                landPaint);
            canvas.restore();
        }
        
        // Very subtle clouds
        canvas.rotate(15); // Offset clouds slightly
        canvas.drawOval(new RectF(-r*0.8f, -r*0.2f, r*0.4f, r*0.1f), cloudPaint);
        canvas.drawOval(new RectF(r*0.1f, r*0.3f, r*0.9f, r*0.5f), cloudPaint);
        
        canvas.restore();

        // Professional Atmosphere (Thin, soft blue)
        RadialGradient atmo = new RadialGradient(
            cx, cy, r * 1.1f,
            new int[]{ 0x005AAFFF, 0x1A5AAFFF, 0x005AAFFF },
            new float[]{ 0.88f/1.1f, 0.95f/1.1f, 1f },
            Shader.TileMode.CLAMP);
        atmoPaint.setShader(atmo);
        canvas.drawCircle(cx, cy, r * 1.1f, atmoPaint);

        // Low-gloss Highlight (Reduced size and alpha)
        RadialGradient shine = new RadialGradient(
            cx - r*0.3f, cy - r*0.4f, r * 0.8f,
            new int[]{ 0x22FFFFFF, 0x00000000 },
            null, Shader.TileMode.CLAMP);
        Paint shineP = new Paint(Paint.ANTI_ALIAS_FLAG);
        shineP.setShader(shine);
        canvas.drawCircle(cx, cy, r, shineP);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (rotAnim != null) rotAnim.cancel();
    }
}
