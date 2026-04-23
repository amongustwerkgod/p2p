package com.example.Peerly;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.animation.*;
import java.util.*;

/**
 * Full-screen custom View that renders:
 *   - Animated starfield background
 *   - Rotating Earth in the center
 *   - Peer bubbles orbiting the earth with individual floating animations
 *   - Dashed lines from each peer to the earth center
 */
public class HubView extends View {

    public interface OnEarthClickedListener { void onEarthClicked(); }
    public interface OnPeerClickedListener  { void onPeerClicked(String peerName); }

    private OnEarthClickedListener earthListener;
    private OnPeerClickedListener  peerListener;
    public void setOnEarthClickedListener(OnEarthClickedListener l) { earthListener = l; }
    public void setOnPeerClickedListener(OnPeerClickedListener l)   { peerListener  = l; }

    // Dimensions
    private float W, H, cx, cy;
    private float earthR  = 0;   // set in onSizeChanged
    private float earthY  = 0;   // animated offset from center

    // Painting
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

    // Stars
    private static class Star { float x, y, r, phase, speed; }
    private List<Star> stars = new ArrayList<>();

    // Peers
    private static class Peer {
        String name; float angle, dist, bobPhase;
        float screenX, screenY; // computed each frame
    }
    private List<Peer> peers = new ArrayList<>();

    // Animation state
    private ValueAnimator frameAnim;
    private float earthRotation = 0f;
    private float t = 0; // Time in seconds

    // Sink/rise
    private boolean isSinking = false;
    private boolean isRising  = false;
    private ValueAnimator sinkAnim;
    private ValueAnimator riseAnim;
    private Runnable afterSink;

    // Username label
    private String username;
    private Paint  usernamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Continent data [relX, relY, relRx, relRy, tiltDeg]
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
        landPaint.setColor(0xFF1e6640);

        peerBorderPaint.setStyle(Paint.Style.STROKE);
        peerBorderPaint.setStrokeWidth(1.5f);
        peerBorderPaint.setColor(0xFF3030a0);

        peerTextPaint.setColor(0xFFc8c8ff);
        peerTextPaint.setTextAlign(Paint.Align.CENTER);
        peerTextPaint.setFakeBoldText(true);
        peerTextPaint.setTypeface(Typeface.MONOSPACE);

        peerLabelPaint.setColor(0xFF6060a0);
        peerLabelPaint.setTextAlign(Paint.Align.CENTER);
        peerLabelPaint.setTypeface(Typeface.MONOSPACE);

        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setColor(0x403232a0);
        dashedPaint.setStrokeWidth(1f);
        dashedPaint.setPathEffect(new DashPathEffect(new float[]{8f, 12f}, 0));

        hintPaint.setColor(0xFFC8C8FF); // Base color, alpha set in onDraw
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTypeface(Typeface.MONOSPACE);

        usernamePaint.setColor(0xFF6060a0);
        usernamePaint.setTextAlign(Paint.Align.CENTER);
        usernamePaint.setTypeface(Typeface.MONOSPACE);

        // Use a 0..1 animator that repeats to drive a smooth, continuous time 't'
        frameAnim = ValueAnimator.ofFloat(0f, 1f);
        frameAnim.setDuration(1000);
        frameAnim.setRepeatCount(ValueAnimator.INFINITE);
        frameAnim.setInterpolator(new LinearInterpolator());
        frameAnim.addUpdateListener(a -> {
            // Using currentPlayTime / 1000.0 gives us a stable 't' in seconds
            t = a.getCurrentPlayTime() / 1000f;
            earthRotation = t * 0.2f; // Smooth rotation based on time
            invalidate();
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w; H = h; cx = W/2f; cy = H/2f;
        earthR = Math.min(W, H) * 0.23f;
        if (!isSinking && !isRising) earthY = cy * 0.85f;

        stars.clear();
        Random rnd = new Random(42);
        for (int i = 0; i < 200; i++) {
            Star s = new Star();
            s.x = rnd.nextFloat() * W;
            s.y = rnd.nextFloat() * H;
            s.r = rnd.nextFloat() * 1.5f + 0.3f;
            s.phase = rnd.nextFloat() * (float)(Math.PI * 2);
            s.speed = rnd.nextFloat() * 2.0f + 0.5f; // adjusted for seconds
            stars.add(s);
        }

        peerTextPaint.setTextSize(earthR * 0.35f);
        peerLabelPaint.setTextSize(earthR * 0.20f);
        hintPaint.setTextSize(earthR * 0.18f);
        usernamePaint.setTextSize(earthR * 0.18f);
        starPaint.setColor(0xFFb4b4ff);
        
        for (Peer p : peers) p.dist = earthR * 1.5f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF03030F);

        // Stars
        for (Star s : stars) {
            float a = 0.25f + 0.45f * (float) Math.sin(s.phase + t * s.speed);
            starPaint.setAlpha((int)(Math.max(0, Math.min(1, a)) * 255));
            canvas.drawCircle(s.x, s.y, s.r, starPaint);
        }

        float ey = earthY;

        // Dashed lines
        for (Peer p : peers) {
            float bob = 12f * (float) Math.sin(t * 3.0f + p.bobPhase);
            p.screenX = cx + (float)Math.cos(p.angle) * p.dist;
            p.screenY = ey - earthR * 0.1f + (float)Math.sin(p.angle) * p.dist * 0.4f + bob;
            canvas.drawLine(p.screenX, p.screenY, cx, ey, dashedPaint);
        }

        drawEarth(canvas, cx, ey, earthR);

        // Hint text with smooth oscillation
        float hintAlpha = 0.4f + 0.3f * (float) Math.sin(t * 2.5f);
        hintPaint.setAlpha((int)(Math.max(0, Math.min(1, hintAlpha)) * 255));
        canvas.drawText("⊕  tap · world chat", cx, ey + earthR + earthR * 0.35f, hintPaint);

        // Peer bubbles
        float pr = earthR * 0.27f;
        for (Peer p : peers) {
            RadialGradient bg = new RadialGradient(
                    p.screenX, p.screenY, pr,
                    new int[]{ 0xFF2a2a70, 0xFF13132a }, null, Shader.TileMode.CLAMP);
            peerBgPaint.setShader(bg);
            canvas.drawCircle(p.screenX, p.screenY, pr, peerBgPaint);
            canvas.drawCircle(p.screenX, p.screenY, pr, peerBorderPaint);
            canvas.drawText(String.valueOf(Character.toUpperCase(p.name.charAt(0))),
                p.screenX, p.screenY + peerTextPaint.getTextSize() * 0.35f, peerTextPaint);
            canvas.drawText(p.name, p.screenX, p.screenY + pr + peerLabelPaint.getTextSize() * 1.3f, peerLabelPaint);
        }

        canvas.drawText(username, cx, earthR * 0.5f, usernamePaint);
    }

    private void drawEarth(Canvas canvas, float cx, float cy, float r) {
        RadialGradient ocean = new RadialGradient(
                cx, cy, r,
                new int[]{ 0xFF1a3a6a, 0xFF0d2040, 0xFF060f20 },
                new float[]{ 0f, 0.5f, 1f }, Shader.TileMode.CLAMP);
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
        canvas.restore();

        RadialGradient atmo = new RadialGradient(
                cx, cy, r*1.12f,
                new int[]{ 0x003C78FF, 0x1A3C78FF, 0x003C78FF }, null, Shader.TileMode.CLAMP);
        atmoPaint.setShader(atmo);
        canvas.drawCircle(cx, cy, r*1.12f, atmoPaint);

        RadialGradient shine = new RadialGradient(
                cx, cy, r,
                new int[]{ 0x2D96C8FF, 0x00000000 }, null, Shader.TileMode.CLAMP);
        shinePaint.setShader(shine);
        canvas.drawCircle(cx, cy, r, shinePaint);
    }

    public void sinkEarth(Runnable after) {
        afterSink = after;
        isSinking = true;
        sinkAnim = ValueAnimator.ofFloat(earthY, H + 200);
        sinkAnim.setDuration(500);
        sinkAnim.setInterpolator(new AccelerateInterpolator());
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
        float target = cy * 0.85f;
        earthY = H + 200;
        isRising = true;
        riseAnim = ValueAnimator.ofFloat(H + 200, target);
        riseAnim.setDuration(700);
        riseAnim.setInterpolator(new OvershootInterpolator(0.8f));
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
        float pr = earthR * 0.27f;
        for (Peer p : peers) {
            if (Math.hypot(mx - p.screenX, my - p.screenY) < pr + 8) {
                if (peerListener != null) peerListener.onPeerClicked(p.name);
                return true;
            }
        }
        if (Math.hypot(mx - cx, my - earthY) < earthR + 8) {
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
        float step = (float)(Math.PI * 2) / Math.max(1, activeNames.size());
        Random rnd = new Random();
        for (String name : activeNames) {
            if (name.equals(username)) continue;
            Peer p = new Peer();
            p.name = name;
            p.angle = i * step;
            p.dist = earthR * 1.5f;
            p.bobPhase = rnd.nextFloat() * 5f;
            peers.add(p);
            i++;
        }
        invalidate();
    }
}
