package com.personal.clipviewer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** 태그처럼 가로로 늘어놓다가 넘치면 다음 줄로 */
public class FlowLayout extends ViewGroup {

    private final int gap;

    public FlowLayout(Context c, int gapPx) {
        super(c);
        gap = gapPx;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            c.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (x > 0 && x + c.getMeasuredWidth() > width) {
                x = 0;
                y += lineH + gap;
                lineH = 0;
            }
            x += c.getMeasuredWidth() + gap;
            lineH = Math.max(lineH, c.getMeasuredHeight());
        }
        setMeasuredDimension(MeasureSpec.getSize(w), y + lineH + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            if (x > 0 && x + c.getMeasuredWidth() > width) {
                x = 0;
                y += lineH + gap;
                lineH = 0;
            }
            int left = getPaddingLeft() + x, top = getPaddingTop() + y;
            c.layout(left, top, left + c.getMeasuredWidth(), top + c.getMeasuredHeight());
            x += c.getMeasuredWidth() + gap;
            lineH = Math.max(lineH, c.getMeasuredHeight());
        }
    }
}
