package com.app.mycity.ui.feed;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewParent;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomImageView extends AppCompatImageView {

    private final Matrix matrix = new Matrix();
    private float scale = 1f;
    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 4f;

    private float lastX, lastY;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public ZoomImageView(Context ctx) { super(ctx); init(ctx); }
    public ZoomImageView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(ctx); }
    public ZoomImageView(Context ctx, AttributeSet attrs, int def) { super(ctx, attrs, def); init(ctx); }

    private void init(Context ctx) {
        setScaleType(ScaleType.FIT_CENTER);
        setImageMatrix(matrix);

        scaleDetector = new ScaleGestureDetector(ctx,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float newScale = Math.max(MIN_SCALE, Math.min(scale * d.getScaleFactor(), MAX_SCALE));
                float factor = newScale / scale;
                matrix.postScale(factor, factor, d.getFocusX(), d.getFocusY());
                scale = newScale;
                clamp();
                setImageMatrix(matrix);
                disallowParentIfNeeded();
                return true;
            }
        });

        gestureDetector = new GestureDetector(ctx,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (scale > MIN_SCALE + 0.1f) {
                    matrix.reset();
                    scale = MIN_SCALE;
                } else {
                    float target = 2.5f;
                    float f = target / scale;
                    matrix.postScale(f, f, e.getX(), e.getY());
                    scale = target;
                    clamp();
                }
                setImageMatrix(matrix);
                disallowParentIfNeeded();
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        scaleDetector.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = ev.getX();
                lastY = ev.getY();
                disallowParentIfNeeded();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && scale > MIN_SCALE + 0.05f) {
                    float dx = ev.getX() - lastX;
                    float dy = ev.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    clamp();
                    setImageMatrix(matrix);
                }
                lastX = ev.getX();
                lastY = ev.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                disallowParentIfNeeded();
                break;
        }
        return true;
    }

    private void clamp() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        float[] v = new float[9];
        matrix.getValues(v);
        float tx = v[Matrix.MTRANS_X];
        float ty = v[Matrix.MTRANS_Y];
        float sx = v[Matrix.MSCALE_X];
        float sy = v[Matrix.MSCALE_Y];
        float dw = getDrawable().getIntrinsicWidth() * sx;
        float dh = getDrawable().getIntrinsicHeight() * sy;
        float dx = 0, dy = 0;

        if (dw <= getWidth()) {
            dx = (getWidth() - dw) / 2f - tx;
        } else {
            if (tx > 0) dx = -tx;
            else if (tx + dw < getWidth()) dx = getWidth() - (tx + dw);
        }
        if (dh <= getHeight()) {
            dy = (getHeight() - dh) / 2f - ty;
        } else {
            if (ty > 0) dy = -ty;
            else if (ty + dh < getHeight()) dy = getHeight() - (ty + dh);
        }
        if (dx != 0 || dy != 0) matrix.postTranslate(dx, dy);
    }

    private void disallowParentIfNeeded() {
        ViewParent p = getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(scale > MIN_SCALE + 0.05f);
    }

    public void reset() {
        matrix.reset();
        scale = MIN_SCALE;
        setImageMatrix(matrix);
    }
}
