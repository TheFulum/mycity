package com.app.mycity.ui.splash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * View с анимированными плавающими кружками на фоне сплэш-экрана.
 */
public class ParticleView extends View {

    private static final int PARTICLE_COUNT = 18;
    private static final long FRAME_MS = 16; // ~60fps (было 32 / ~30fps)

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Цвета кружков (соответствуют акцентам приложения)
    private final int[] COLORS = {
            0x4A5B5FBE, // синий (ещё ярче)
            0x4AC0392B, // красный (ещё ярче)
            0x4A27AE60, // зелёный (ещё ярче)
            0x3A9E9E9E, // серый (ещё ярче)
            0x5A5B5FBE,
            0x5AC0392B,
    };

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            update();
            invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    public ParticleView(Context context) {
        super(context);
    }

    public ParticleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w > 0 && h > 0) {
            initParticles(w, h);
        }
    }

    private void initParticles(int w, int h) {
        particles.clear();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(createParticle(w, h, true));
        }
    }

    private Particle createParticle(int w, int h, boolean randomY) {
        Particle p = new Particle();
        p.x = rnd.nextFloat() * w;
        p.y = randomY ? rnd.nextFloat() * h : h + 60;
        // Крупнее, чтобы читалось на фоне
        p.radius = 28 + rnd.nextFloat() * 88;
        p.speedY = -(0.55f + rnd.nextFloat() * 1.3f);
        p.speedX = (rnd.nextFloat() - 0.5f) * 0.65f;
        p.color = COLORS[rnd.nextInt(COLORS.length)];
        p.maxH = h;
        p.maxW = w;
        return p;
    }

    private void update() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);
            p.x += p.speedX;
            p.y += p.speedY;

            // Если улетел вверх — пересоздаём снизу
            if (p.y + p.radius < 0) {
                particles.set(i, createParticle(w, h, false));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Particle p : particles) {
            paint.setColor(p.color);
            canvas.drawCircle(p.x, p.y, p.radius, paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(frameRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(frameRunnable);
    }

    private static class Particle {
        float x, y, radius, speedX, speedY;
        int color;
        int maxH, maxW;
    }
}