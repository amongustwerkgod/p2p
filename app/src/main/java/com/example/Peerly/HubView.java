package com.example.Peerly;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.animation.*;
import java.util.*;

/**
 * HubView with realistic Earth, revolving satellites, and robust animation controls.
 */
public class HubView extends View {

    public interface OnEarthClickedListener { void onEarthClicked(); }
    public interface OnPeerClickedListener  { void onPeerClicked(String peerName); }

    private OnEarthClickedListener earthListener;
    private OnPeerClickedListener  peerListener;
    public void setOnEarthClickedListener(OnEarthClickedListener l) { earthListener = l; }
    public void setOnPeerClickedListener(OnPeerClickedListener l)   { peerListener  = l; }

    private float W, H, cx, cy;
    private float earthR  = 0;
    private float earthY  = 0;
    private float zoomFactor = 1.0f; 

    private Paint oceanPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint landPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint atmoPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint cloudPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint peerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint peerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint peerTextPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint peerLabelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint starPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint hintPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint usernamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint satellitePanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static class Star { float x, y, r, phase, speed; }
    private List<Star> stars = new ArrayList<>();

    private static class Peer {
        String name; float angle, dist, bobPhase;
        float screenX, screenY;
        float entryScale = 0f;
    }
    private List<Peer> peers = new ArrayList<>();

    private ValueAnimator frameAnim;
    private float earthRotation = 0f;
    private float t = 0;

    private boolean isSinking = false;
    private boolean isRising  = false;
    private ValueAnimator sinkAnim;
    private ValueAnimator riseAnim;
    private Runnable afterSink;

    private String username = "";

    private final float[][] CONTINENTS = {
        { -0.15f, -0.25f, 0.35f, 0.25f,  15f },
        { -0.05f,  0.25f, 0.22f, 0.30f, -10f },
        {  0.25f, -0.15f, 0.28f, 0.25f,  20f },
        {  0.28f,  0.20f, 0.25f, 0.32f,   0f },
        {  0.55f,  0.40f, 0.18f, 0.15f, -15f },
        {  0.00f,  0.85f, 0.60f, 0.15f,   0f },
    };

    public HubView(Context context) { super(context); init(); }
    public HubView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    public void setUsername(String username) {
        this.username = username;
        invalidate();
    }

    private void init() {
        landPaint.setColor(0xFF2E5A27); 
        cloudPaint.setColor(0x66FFFFFF); 
        
        peerBorderPaint.setStyle(Paint.Style.STROKE);
        peerBorderPaint.setStrokeWidth(2f);
        peerBorderPaint.setColor(0xFF7070FF);
        
        peerTextPaint.setColor(0xFFFFFFFF);
        peerTextPaint.setTextAlign(Paint.Align.CENTER);
        peerTextPaint.setFakeBoldText(true);
        peerTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        
        peerLabelPaint.setColor(0xFFCCCCFF);
        peerLabelPaint.setTextAlign(Paint.Align.CENTER);
        peerLabelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        
        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setColor(0x207070FF); 
        dashedPaint.setStrokeWidth(1.0f);
        
        hintPaint.setColor(0xFF7070FF);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hintPaint.setLetterSpacing(0.1f);
        
        usernamePaint.setColor(0xFF8080B0);
        usernamePaint.setTextAlign(Paint.Align.CENTER);
        usernamePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        usernamePaint.setLetterSpacing(0.2f);

        glowPaint.setStyle(Paint.Style.FILL);
        satellitePanelPaint.setColor(0xFF4A4AE0);

        frameAnim = ValueAnimator.ofFloat(0f, 100000f);
        frameAnim.setDuration(100000000L); // Very long duration for smooth t
        frameAnim.setRepeatCount(ValueAnimator.INFINITE);
        frameAnim.setInterpolator(new LinearInterpolator());
        frameAnim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            earthRotation = t * 0.08f;
            invalidate();
        });
    }

    public void setZoomFactor(float zoom) {
        this.zoomFactor = zoom;
        invalidate();
    }

    public void resumeAnimations() {
        if (frameAnim == null) return;
        if (frameAnim.isPaused()) {
            frameAnim.resume();
        } else if (!frameAnim.isRunning()) {
            frameAnim.start();
        }
    }

    public void pauseAnimations() {
        if (frameAnim != null && frameAnim.isRunning()) {
            frameAnim.pause();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w; H = h; cx = W/2f; cy = H/2f;
        earthR = Math.min(W, H) * 0.22f;
        if (!isSinking && !isRising) earthY = cy;

        stars.clear();
        Random rnd = new Random(123);
        for (int i = 0; i < 200; i++) {
            Star s = new Star();
            s.x = rnd.nextFloat() * W;
            s.y = rnd.nextFloat() * H;
            s.r = rnd.nextFloat() * 1.5f + 0.3f;
            s.phase = rnd.nextFloat() * (float)(Math.PI * 2);
            s.speed = rnd.nextFloat() * 1.5f + 0.5f;
            stars.add(s);
        }

        peerTextPaint.setTextSize(earthR * 0.25f);
        peerLabelPaint.setTextSize(earthR * 0.16f);
        hintPaint.setTextSize(earthR * 0.16f);
        usernamePaint.setTextSize(earthR * 0.18f);
        starPaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint bgPaint = new Paint();
        RadialGradient bgGrad = new RadialGradient(cx, cy, Math.max(W, H),
                new int[]{0xFF050B1A, 0xFF02050D}, null, Shader.TileMode.CLAMP);
        bgPaint.setShader(bgGrad);
        canvas.drawRect(0, 0, W, H, bgPaint);

        for (Star s : stars) {
            float a = 0.1f + 0.6f * (float) Math.sin(s.phase + t * s.speed);
            starPaint.setAlpha((int)(Math.max(0, Math.min(1, a)) * 255));
            canvas.drawCircle(s.x, s.y, s.r, starPaint);
        }

        float currentR = earthR * zoomFactor;
        float ey = earthY;

        if (zoomFactor > 0.9f && zoomFactor < 2.0f) {
            Paint orbitPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            orbitPathPaint.setStyle(Paint.Style.STROKE);
            orbitPathPaint.setColor(0x157070FF);
            orbitPathPaint.setStrokeWidth(2f);
            canvas.drawOval(cx - W*0.38f, ey - earthR*0.5f, cx + W*0.38f, ey + earthR*0.5f, orbitPathPaint);
        }

        drawEarth(canvas, cx, ey, currentR);

        if (zoomFactor > 0.9f && zoomFactor < 2.0f) {
            float hintAlpha = 0.3f + 0.3f * (float) Math.sin(t * 2.5f);
            hintPaint.setAlpha((int)(Math.max(0, Math.min(1, hintAlpha)) * 255));
            canvas.drawText("TAP THE EARTH TO BROADCAST", cx, ey + earthR * 1.65f, hintPaint);

            float bodyR = earthR * 0.18f;
            for (Peer p : peers) {
                if (p.entryScale < 1f) p.entryScale += 0.05f;
                float scale = p.entryScale;
                float cBr = bodyR * scale;
                float orbitX = (float)Math.cos(p.angle + t * 0.1f) * (W * 0.38f); 
                float orbitY = (float)Math.sin(p.angle + t * 0.1f) * (earthR * 0.5f);
                float bob = 8f * (float) Math.sin(t * 1.5f + p.bobPhase);
                p.screenX = cx + orbitX;
                p.screenY = ey + orbitY + bob;

                canvas.save();
                canvas.translate(p.screenX, p.screenY);
                float rotationAngle = (float) Math.toDegrees(p.angle + t * 0.1f) + 90;
                canvas.rotate(rotationAngle);
                
                float panelW = cBr * 1.8f, panelH = cBr * 0.6f;
                satellitePanelPaint.setAlpha((int)(scale * 200));
                canvas.drawRect(-cBr - panelW, -panelH/2, -cBr, panelH/2, satellitePanelPaint);
                canvas.drawRect(cBr, -panelH/2, cBr + panelW, panelH/2, satellitePanelPaint);
                
                RadialGradient bg = new RadialGradient(0, 0, cBr,
                        new int[]{ 0xFF1E1E42, 0xFF0D0D1A }, null, Shader.TileMode.CLAMP);
                peerBgPaint.setShader(bg);
                canvas.drawCircle(0, 0, cBr, peerBgPaint);
                canvas.drawCircle(0, 0, cBr, peerBorderPaint);
                
                float signalAlpha = 0.5f + 0.5f * (float)Math.sin(t * 5f + p.bobPhase);
                Paint signalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                signalPaint.setColor(0xFF40D080);
                signalPaint.setAlpha((int)(signalAlpha * 255 * scale));
                canvas.drawCircle(cBr * 0.6f, -cBr * 0.6f, 3f * scale, signalPaint);
                canvas.restore();

                if (p.name != null && p.name.length() > 0 && scale > 0.4f) {
                    peerTextPaint.setAlpha((int)(scale * 255));
                    peerLabelPaint.setAlpha((int)(scale * 200));
                    canvas.drawText(String.valueOf(Character.toUpperCase(p.name.charAt(0))),
                        p.screenX, p.screenY + peerTextPaint.getTextSize() * 0.35f, peerTextPaint);
                    String label = p.name.toLowerCase();
                    if (label.length() > 10) label = label.substring(0, 8) + "..";
                    float textWidth = peerLabelPaint.measureText(label);
                    float lx = p.screenX;
                    if (lx - textWidth/2 < 30f) lx = textWidth/2 + 30f;
                    if (lx + textWidth/2 > W - 30f) lx = W - textWidth/2 - 30f;
                    canvas.drawText(label, lx, p.screenY + cBr + peerLabelPaint.getTextSize() * 1.8f, peerLabelPaint);
                }
            }
            if (username != null && !username.isEmpty()) {
                canvas.drawText("@" + username.toLowerCase(), cx, H * 0.08f + 30, usernamePaint);
            }
        }
    }

    private void drawEarth(Canvas canvas, float cx, float cy, float r) {
        if (zoomFactor < 0.05f) {
            Paint starP = new Paint(Paint.ANTI_ALIAS_FLAG);
            starP.setColor(Color.WHITE);
            starP.setShadowLayer(15f, 0, 0, Color.WHITE);
            canvas.drawCircle(cx, cy, r, starP);
            return;
        }

        RadialGradient ocean = new RadialGradient(cx - r*0.1f, cy - r*0.15f, r * 1.2f,
                new int[]{ 0xFF1B3D6D, 0xFF0B1B33, 0xFF050B1A },
                new float[]{ 0f, 0.6f, 1f }, Shader.TileMode.CLAMP);
        oceanPaint.setShader(ocean);
        canvas.drawCircle(cx, cy, r, oceanPaint);

        canvas.save();
        Path clip = new Path();
        clip.addCircle(cx, cy, r, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.translate(cx, cy);
        canvas.rotate((float) Math.toDegrees(earthRotation));
        for (float[] c : CONTINENTS) {
            canvas.save();
            canvas.rotate(c[4]);
            canvas.drawOval(new RectF((c[0]-c[2])*r,(c[1]-c[3])*r,(c[0]+c[2])*r,(c[1]+c[3])*r), landPaint);
            canvas.restore();
        }
        canvas.rotate(15);
        canvas.drawOval(new RectF(-r*0.8f, -r*0.2f, r*0.4f, r*0.1f), cloudPaint);
        canvas.drawOval(new RectF(r*0.1f, r*0.3f, r*0.9f, r*0.5f), cloudPaint);
        canvas.restore();

        RadialGradient atmo = new RadialGradient(cx, cy, r * 1.1f,
                new int[]{ 0x005AAFFF, 0x1A5AAFFF, 0x005AAFFF },
                new float[]{ 0.88f/1.1f, 0.95f/1.1f, 1f }, Shader.TileMode.CLAMP);
        atmoPaint.setShader(atmo);
        canvas.drawCircle(cx, cy, r * 1.1f, atmoPaint);

        RadialGradient shine = new RadialGradient(cx - r*0.3f, cy - r*0.4f, r * 0.8f,
                new int[]{ 0x22FFFFFF, 0x00000000 }, null, Shader.TileMode.CLAMP);
        Paint shineP = new Paint(Paint.ANTI_ALIAS_FLAG);
        shineP.setShader(shine);
        canvas.drawCircle(cx, cy, r, shineP);
    }

    public void sinkEarth(Runnable after) {
        afterSink = after;
        isSinking = true;
        sinkAnim = ValueAnimator.ofFloat(earthY, H + 400);
        sinkAnim.setDuration(600);
        sinkAnim.setInterpolator(new AccelerateInterpolator(1.2f));
        sinkAnim.addUpdateListener(a -> { earthY = (float) a.getAnimatedValue(); invalidate(); });
        sinkAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                isSinking = false;
                if (afterSink != null) afterSink.run();
            }
        });
        sinkAnim.start();
    }

    public void riseEarth() {
        isRising = true;
        riseAnim = ValueAnimator.ofFloat(earthY, cy);
        riseAnim.setDuration(900);
        riseAnim.setInterpolator(new OvershootInterpolator(0.6f));
        riseAnim.addUpdateListener(a -> { earthY = (float) a.getAnimatedValue(); invalidate(); });
        riseAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { isRising = false; }
        });
        riseAnim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP || zoomFactor > 1.1f) return true;
        float mx = e.getX(), my = e.getY();
        for (Peer p : peers) {
            if (Math.hypot(mx - p.screenX, my - p.screenY) < earthR * 0.4f) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (peerListener != null) peerListener.onPeerClicked(p.name);
                return true;
            }
        }
        if (Math.hypot(mx - cx, my - earthY) < earthR + 20) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (earthListener != null) earthListener.onEarthClicked();
        }
        return true;
    }

    @Override protected void onAttachedToWindow()  { super.onAttachedToWindow(); resumeAnimations(); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); pauseAnimations(); }

    public void setPeers(Set<String> activeNames) {
        peers.clear();
        int i = 0;
        int count = activeNames.size();
        float step = (float)(Math.PI * 2) / Math.max(1, count);
        Random rnd = new Random();
        for (String name : activeNames) {
            Peer p = new Peer(); p.name = name; p.angle = i * step;
            p.bobPhase = rnd.nextFloat() * 10f; p.entryScale = 0f;
            peers.add(p); i++;
        }
        invalidate();
    }
}
