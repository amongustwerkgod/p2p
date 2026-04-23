package com.example.Peerly;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.util.Log;
import android.view.*;
import android.view.animation.*;
import java.util.*;

/**
 * Polished HubView with improved visuals, glow effects, and smoother transitions.
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

    private Paint oceanPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint landPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint atmoPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint shinePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint peerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint peerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint peerTextPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint peerLabelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint starPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint hintPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint usernamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static class Star { float x, y, r, phase, speed; }
    private List<Star> stars = new ArrayList<>();

    private static class Peer {
        String name; float angle, dist, bobPhase;
        float screenX, screenY;
        float entryScale = 0f; // For entry animation
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

    private String username;

    private final float[][] CONTINENTS = {
        { -0.08f, -0.18f, 0.27f, 0.22f,  10f },
        { -0.48f, -0.20f, 0.18f, 0.30f,  -5f },
        {  0.08f,  0.22f, 0.20f, 0.17f,  15f },
        { -0.40f,  0.32f, 0.15f, 0.12f,   5f },
        {  0.36f, -0.30f, 0.14f, 0.22f, -10f },
    };

    public HubView(Context context, String username) {
        super(context);
        this.username = username;
        init();
    }

    private void init() {
        landPaint.setColor(0xFF2E8B57); // Sea Green
        
        peerBorderPaint.setStyle(Paint.Style.STROKE);
        peerBorderPaint.setStrokeWidth(2f);
        peerBorderPaint.setColor(0xFF7070FF);
        
        peerTextPaint.setColor(0xFFFFFFFF);
        peerTextPaint.setTextAlign(Paint.Align.CENTER);
        peerTextPaint.setFakeBoldText(true);
        peerTextPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        
        peerLabelPaint.setColor(0xFFA0A0FF);
        peerLabelPaint.setTextAlign(Paint.Align.CENTER);
        peerLabelPaint.setTypeface(Typeface.MONOSPACE);
        
        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setColor(0x257070FF);
        dashedPaint.setStrokeWidth(1.5f);
        dashedPaint.setPathEffect(new DashPathEffect(new float[]{10f, 15f}, 0));
        
        hintPaint.setColor(0xFF7070FF);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTypeface(Typeface.MONOSPACE);
        hintPaint.setLetterSpacing(0.1f);
        
        usernamePaint.setColor(0xFF8080B0);
        usernamePaint.setTextAlign(Paint.Align.CENTER);
        usernamePaint.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        usernamePaint.setLetterSpacing(0.2f);

        glowPaint.setStyle(Paint.Style.FILL);

        frameAnim = ValueAnimator.ofFloat(0f, 1f);
        frameAnim.setDuration(1000);
        frameAnim.setRepeatCount(ValueAnimator.INFINITE);
        frameAnim.setInterpolator(new LinearInterpolator());
        frameAnim.addUpdateListener(a -> {
            t = a.getCurrentPlayTime() / 1000f;
            earthRotation = t * 0.08f;
            invalidate();
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w; H = h; cx = W/2f; cy = H/2f;
        earthR = Math.min(W, H) * 0.22f;
        if (!isSinking && !isRising) earthY = cy * 0.82f;

        stars.clear();
        Random rnd = new Random(123);
        for (int i = 0; i < 180; i++) {
            Star s = new Star();
            s.x = rnd.nextFloat() * W;
            s.y = rnd.nextFloat() * H;
            s.r = rnd.nextFloat() * 1.2f + 0.4f;
            s.phase = rnd.nextFloat() * (float)(Math.PI * 2);
            s.speed = rnd.nextFloat() * 1.2f + 0.3f;
            stars.add(s);
        }

        peerTextPaint.setTextSize(earthR * 0.32f);
        peerLabelPaint.setTextSize(earthR * 0.18f);
        hintPaint.setTextSize(earthR * 0.16f);
        usernamePaint.setTextSize(earthR * 0.18f);
        starPaint.setColor(0xFFB0B0FF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Dark space gradient background
        Paint bgPaint = new Paint();
        RadialGradient bgGrad = new RadialGradient(cx, cy, Math.max(W, H),
                new int[]{0xFF08081A, 0xFF03030F}, null, Shader.TileMode.CLAMP);
        bgPaint.setShader(bgGrad);
        canvas.drawRect(0, 0, W, H, bgPaint);

        // Stars with smooth twinkle
        for (Star s : stars) {
            float a = 0.15f + 0.5f * (float) Math.sin(s.phase + t * s.speed);
            starPaint.setAlpha((int)(Math.max(0, Math.min(1, a)) * 255));
            canvas.drawCircle(s.x, s.y, s.r, starPaint);
        }

        float ey = earthY;

        // Dashed lines to peers
        for (Peer p : peers) {
            float bob = 15f * (float) Math.sin(t * 2.2f + p.bobPhase);
            float orbitX = (float)Math.cos(p.angle) * earthR * 2.1f;
            float orbitY = (float)Math.sin(p.angle) * earthR * 0.55f;
            p.screenX = cx + orbitX;
            p.screenY = ey + orbitY + bob;
            
            dashedPaint.setAlpha((int)(p.entryScale * 37)); // 0.15 alpha
            canvas.drawLine(p.screenX, p.screenY, cx, ey, dashedPaint);
        }

        drawEarth(canvas, cx, ey, earthR);

        // Polished Hint Text
        float hintAlpha = 0.3f + 0.3f * (float) Math.sin(t * 2.5f);
        hintPaint.setAlpha((int)(Math.max(0, Math.min(1, hintAlpha)) * 255));
        canvas.drawText("TAP THE EARTH TO BROADCAST", cx, ey + earthR * 1.65f, hintPaint);

        // Peer Bubbles with entry animation and glow
        float pr = earthR * 0.28f;
        for (Peer p : peers) {
            if (p.entryScale < 1f) p.entryScale += 0.05f; // Simple smooth entry
            
            float scale = p.entryScale;
            float currentPr = pr * scale;
            
            // Subtle glow under peer
            RadialGradient glow = new RadialGradient(p.screenX, p.screenY, currentPr * 1.8f,
                    new int[]{0x407070FF, 0x007070FF}, null, Shader.TileMode.CLAMP);
            glowPaint.setShader(glow);
            canvas.drawCircle(p.screenX, p.screenY, currentPr * 1.8f, glowPaint);

            // Peer Bubble
            RadialGradient bg = new RadialGradient(
                    p.screenX, p.screenY, currentPr,
                    new int[]{ 0xFF1E1E42, 0xFF0D0D1A }, null, Shader.TileMode.CLAMP);
            peerBgPaint.setShader(bg);
            canvas.drawCircle(p.screenX, p.screenY, currentPr, peerBgPaint);
            canvas.drawCircle(p.screenX, p.screenY, currentPr, peerBorderPaint);
            
            if (p.name != null && p.name.length() > 0 && scale > 0.5f) {
                peerTextPaint.setAlpha((int)(scale * 255));
                peerLabelPaint.setAlpha((int)(scale * 255));
                canvas.drawText(String.valueOf(Character.toUpperCase(p.name.charAt(0))),
                    p.screenX, p.screenY + peerTextPaint.getTextSize() * 0.35f, peerTextPaint);
                canvas.drawText(p.name.toLowerCase(), p.screenX, p.screenY + currentPr + peerLabelPaint.getTextSize() * 1.4f, peerLabelPaint);
            }
        }

        canvas.drawText("@" + username.toLowerCase(), cx, 110, usernamePaint);
    }

    private void drawEarth(Canvas canvas, float cx, float cy, float r) {
        // Outer Atmosphere glow
        RadialGradient atmoGlow = new RadialGradient(cx, cy, r * 1.35f,
                new int[]{0x253C78FF, 0x003C78FF}, null, Shader.TileMode.CLAMP);
        atmoPaint.setShader(atmoGlow);
        canvas.drawCircle(cx, cy, r * 1.35f, atmoPaint);

        // Earth Body
        RadialGradient ocean = new RadialGradient(cx - r*0.1f, cy - r*0.1f, r * 1.1f,
                new int[]{ 0xFF1A3A6A, 0xFF060F20 }, new float[]{0.2f, 1f}, Shader.TileMode.CLAMP);
        oceanPaint.setShader(ocean);
        canvas.drawCircle(cx, cy, r, oceanPaint);

        // Continents
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
        canvas.restore();

        // Inner Shine / Atmosphere Edge
        RadialGradient shine = new RadialGradient(cx - r*0.2f, cy - r*0.3f, r * 0.9f,
                new int[]{ 0x4096C8FF, 0x00000000 }, null, Shader.TileMode.CLAMP);
        shinePaint.setShader(shine);
        canvas.drawCircle(cx, cy, r, shinePaint);
    }

    public void sinkEarth(Runnable after) {
        afterSink = after;
        isSinking = true;
        sinkAnim = ValueAnimator.ofFloat(earthY, H + 300);
        sinkAnim.setDuration(600);
        sinkAnim.setInterpolator(new AccelerateInterpolator(1.5f));
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
        float target = cy * 0.82f;
        isRising = true;
        riseAnim = ValueAnimator.ofFloat(H + 300, target);
        riseAnim.setDuration(900);
        riseAnim.setInterpolator(new OvershootInterpolator(0.7f));
        riseAnim.addUpdateListener(a -> { earthY = (float) a.getAnimatedValue(); invalidate(); });
        riseAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { isRising = false; }
        });
        riseAnim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float mx = e.getX(), my = e.getY();
        float pr = earthR * 0.35f; // Larger touch target
        for (Peer p : peers) {
            if (Math.hypot(mx - p.screenX, my - p.screenY) < pr + 10) {
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

    public void resumeAnimations() { if (!frameAnim.isRunning()) frameAnim.start(); }
    public void pauseAnimations()  { frameAnim.pause(); }

    @Override
    protected void onAttachedToWindow()  { super.onAttachedToWindow(); frameAnim.start(); }
    @Override
    protected void onDetachedFromWindow() { super.onDetachedFromWindow(); frameAnim.cancel(); }

    public void setPeers(Set<String> activeNames) {
        peers.clear();
        int i = 0;
        int count = activeNames.size();
        float step = (float)(Math.PI * 2) / Math.max(1, count);
        Random rnd = new Random();
        for (String name : activeNames) {
            Peer p = new Peer();
            p.name = name;
            p.angle = i * step;
            p.bobPhase = rnd.nextFloat() * 10f;
            p.entryScale = 0f; // Reset entry animation
            peers.add(p);
            i++;
        }
        invalidate();
    }
}
