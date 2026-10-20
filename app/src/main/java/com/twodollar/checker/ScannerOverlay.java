package com.twodollar.checker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Scanner overlay: dims the frame outside a centered bill-shaped window
 * and draws four bright corner brackets around it.
 */
public class ScannerOverlay extends View {

    private final Paint bracketPaint = new Paint();
    private final Paint scrimPaint = new Paint();
    private final RectF window = new RectF();
    private int bracketColor = Color.rgb(0x00, 0xE6, 0x76);

    public ScannerOverlay(Context c) { super(c); init(); }
    public ScannerOverlay(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        scrimPaint.setColor(Color.argb(120, 0, 0, 0));
        bracketPaint.setColor(bracketColor);
        bracketPaint.setStyle(Paint.Style.STROKE);
        bracketPaint.setStrokeWidth(dp(5));
        bracketPaint.setStrokeCap(Paint.Cap.ROUND);
        bracketPaint.setAntiAlias(true);
    }

    public void setBracketColor(int c) {
        bracketColor = c;
        bracketPaint.setColor(c);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float cx = w / 2f;
        float cy = h / 2f;
        float halfW = w * 0.40f;
        float halfH = h * 0.14f;
        window.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // dim scrim as four strips around the window (no xfermode — HW-safe)
        float l = window.left, t = window.top, r = window.right, b = window.bottom;
        canvas.drawRect(0, 0, getWidth(), t, scrimPaint);
        canvas.drawRect(0, b, getWidth(), getHeight(), scrimPaint);
        canvas.drawRect(0, t, l, b, scrimPaint);
        canvas.drawRect(r, t, getWidth(), b, scrimPaint);
        // four corner brackets
        float len = dp(44);
        canvas.drawLine(window.left, window.top + len, window.left, window.top, bracketPaint);
        canvas.drawLine(window.left, window.top, window.left + len, window.top, bracketPaint);

        canvas.drawLine(window.right - len, window.top, window.right, window.top, bracketPaint);
        canvas.drawLine(window.right, window.top, window.right, window.top + len, bracketPaint);

        canvas.drawLine(window.left, window.bottom - len, window.left, window.bottom, bracketPaint);
        canvas.drawLine(window.left, window.bottom, window.left + len, window.bottom, bracketPaint);

        canvas.drawLine(window.right, window.bottom - len, window.right, window.bottom, bracketPaint);
        canvas.drawLine(window.right - len, window.bottom, window.right, window.bottom, bracketPaint);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
