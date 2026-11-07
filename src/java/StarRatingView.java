package com.personal.clipviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/** 별 5개, 가로로 끌거나 눌러서 0.1 단위로 별점 조절 */
public class StarRatingView extends View {

    public interface Listener {
        void onChange(float rating);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private float rating;
    private Listener listener;

    public StarRatingView(Context c) {
        super(c);
        fill.setColor(0xFFFFC107);
        empty.setColor(0xFF555555);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float r) {
        rating = Math.max(0, Math.min(5, Math.round(r * 10) / 10f));
        invalidate();
        if (listener != null) listener.onChange(rating);
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, width / 5);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // 한 칸(h x h) 안에 들어가는 별 모양
        float size = h, cx = size / 2, cy = size / 2 + size * 0.04f;
        float outer = size * 0.46f, inner = outer * 0.4f;
        star.reset();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 5 * i - Math.PI / 2;
            float r = i % 2 == 0 ? outer : inner;
            float x = cx + (float) (Math.cos(a) * r), y = cy + (float) (Math.sin(a) * r);
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = getWidth() / 5f;
        for (int i = 0; i < 5; i++) {
            canvas.save();
            canvas.translate(i * cell + (cell - getHeight()) / 2, 0);
            canvas.drawPath(star, empty);
            float part = Math.max(0, Math.min(1, rating - i));
            if (part > 0) {
                // 별 모양에서 실제로 보이는 가로 범위(0.06~0.94)에 맞춰 채움
                float h = getHeight();
                float left = h * 0.06f, right = h * 0.94f;
                canvas.clipRect(0, 0, left + (right - left) * part, h);
                canvas.drawPath(star, fill);
            }
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        float cell = getWidth() / 5f;
        int i = (int) Math.min(4, Math.max(0, e.getX() / cell));
        float h = getHeight();
        float left = i * cell + (cell - h) / 2 + h * 0.06f, right = left + h * 0.88f;
        float part = Math.max(0, Math.min(1, (e.getX() - left) / (right - left)));
        setRating(i + part);
        return true;
    }
}
