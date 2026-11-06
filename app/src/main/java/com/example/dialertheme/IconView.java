package com.example.dialertheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Vector-style icons drawn by code, with optional neon glow. */
public class IconView extends View {
    public static final int CONTACTS = 0, KEYPAD = 1, RECENTS = 2, SETTINGS = 3, PHONE = 4,
            END = 5, MIC = 6, MIC_OFF = 7, SPEAKER = 8, PAUSE = 9, CHAT = 10, BACK = 11,
            SEARCH = 12, CHECK = 13;

    private final int type;
    private int color;
    private boolean glow;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public IconView(Context c, int type, int color, boolean glow) {
        super(c);
        this.type = type;
        this.color = color;
        this.glow = glow;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setStyle(int color, boolean glow) {
        this.color = color;
        this.glow = glow;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int d = Ui.dp(getContext(), 24);
        setMeasuredDimension(resolveSize(d, w), resolveSize(d, h));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        if (s <= 0) return;
        cv.save();
        cv.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        cv.scale(s, s);
        switch (type) {
            case CONTACTS: {
                Path pa = new Path();
                pa.addCircle(12, 8, 3.6f, Path.Direction.CW);
                pa.moveTo(5, 20);
                pa.cubicTo(5, 15.5f, 8.5f, 14, 12, 14);
                pa.cubicTo(15.5f, 14, 19, 15.5f, 19, 20);
                layer(cv, pa, false);
                break;
            }
            case KEYPAD: {
                Path pa = new Path();
                float[] v = {6, 12, 18};
                for (float x : v) for (float y : v) pa.addCircle(x, y, 1.7f, Path.Direction.CW);
                layer(cv, pa, true);
                break;
            }
            case RECENTS: {
                Path pa = new Path();
                pa.addCircle(12, 12, 8, Path.Direction.CW);
                pa.moveTo(12, 7);
                pa.lineTo(12, 12);
                pa.lineTo(15.5f, 14);
                layer(cv, pa, false);
                break;
            }
            case SETTINGS: {
                Path pa = new Path();
                pa.addCircle(12, 12, 3, Path.Direction.CW);
                pa.addCircle(12, 12, 6.2f, Path.Direction.CW);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                    pa.moveTo(12 + c * 6.2f, 12 + sn * 6.2f);
                    pa.lineTo(12 + c * 8.8f, 12 + sn * 8.8f);
                }
                layer(cv, pa, false);
                break;
            }
            case PHONE:
                layer(cv, phone(), true);
                break;
            case END:
                cv.save();
                cv.rotate(135, 12, 12);
                layer(cv, phone(), true);
                cv.restore();
                break;
            case MIC:
            case MIC_OFF: {
                Path pa = new Path();
                pa.addRoundRect(new RectF(9, 3.5f, 15, 13.5f), 3, 3, Path.Direction.CW);
                pa.moveTo(6.5f, 11);
                pa.cubicTo(6.5f, 17, 17.5f, 17, 17.5f, 11);
                pa.moveTo(12, 16.5f);
                pa.lineTo(12, 20.5f);
                pa.moveTo(9, 20.5f);
                pa.lineTo(15, 20.5f);
                if (type == MIC_OFF) {
                    pa.moveTo(4.5f, 4.5f);
                    pa.lineTo(19.5f, 19.5f);
                }
                layer(cv, pa, false);
                break;
            }
            case SPEAKER: {
                Path pa = new Path();
                pa.moveTo(4, 9.5f);
                pa.lineTo(8, 9.5f);
                pa.lineTo(12.5f, 5.5f);
                pa.lineTo(12.5f, 18.5f);
                pa.lineTo(8, 14.5f);
                pa.lineTo(4, 14.5f);
                pa.close();
                pa.moveTo(15.5f, 9);
                pa.quadTo(17.5f, 12, 15.5f, 15);
                pa.moveTo(17.5f, 7);
                pa.quadTo(20.5f, 12, 17.5f, 17);
                layer(cv, pa, false);
                break;
            }
            case PAUSE: {
                Path pa = new Path();
                pa.addRoundRect(new RectF(6.5f, 5, 10, 19), 1, 1, Path.Direction.CW);
                pa.addRoundRect(new RectF(14, 5, 17.5f, 19), 1, 1, Path.Direction.CW);
                layer(cv, pa, true);
                break;
            }
            case CHAT: {
                Path pa = new Path();
                pa.addRoundRect(new RectF(4, 5, 20, 16), 2.5f, 2.5f, Path.Direction.CW);
                pa.moveTo(8, 16);
                pa.lineTo(8, 20);
                pa.lineTo(12, 16);
                layer(cv, pa, false);
                break;
            }
            case BACK: {
                Path pa = new Path();
                pa.moveTo(9, 6.5f);
                pa.lineTo(19.5f, 6.5f);
                pa.lineTo(19.5f, 17.5f);
                pa.lineTo(9, 17.5f);
                pa.lineTo(3.5f, 12);
                pa.close();
                pa.moveTo(12.5f, 9.5f);
                pa.lineTo(16.5f, 14.5f);
                pa.moveTo(12.5f, 14.5f);
                pa.lineTo(16.5f, 9.5f);
                layer(cv, pa, false);
                break;
            }
            case SEARCH: {
                Path pa = new Path();
                pa.addCircle(10.5f, 10.5f, 5.5f, Path.Direction.CW);
                pa.moveTo(14.6f, 14.6f);
                pa.lineTo(19.5f, 19.5f);
                layer(cv, pa, false);
                break;
            }
            case CHECK: {
                Path pa = new Path();
                pa.moveTo(5, 12.5f);
                pa.lineTo(10, 17.5f);
                pa.lineTo(19, 7);
                layer(cv, pa, false);
                break;
            }
            default:
                break;
        }
        cv.restore();
    }

    private Path phone() {
        Path pa = new Path();
        pa.moveTo(6.62f, 10.79f);
        pa.cubicTo(8.06f, 13.62f, 10.38f, 15.93f, 13.21f, 17.38f);
        pa.lineTo(15.41f, 15.18f);
        pa.cubicTo(15.68f, 14.91f, 16.08f, 14.82f, 16.43f, 14.94f);
        pa.cubicTo(17.55f, 15.31f, 18.76f, 15.51f, 20f, 15.51f);
        pa.cubicTo(20.55f, 15.51f, 21f, 15.96f, 21f, 16.51f);
        pa.lineTo(21f, 20f);
        pa.cubicTo(21f, 20.55f, 20.55f, 21f, 20f, 21f);
        pa.cubicTo(10.61f, 21f, 3f, 13.39f, 3f, 4f);
        pa.cubicTo(3f, 3.45f, 3.45f, 3f, 4f, 3f);
        pa.lineTo(7.5f, 3f);
        pa.cubicTo(8.05f, 3f, 8.5f, 3.45f, 8.5f, 4f);
        pa.cubicTo(8.5f, 5.25f, 8.7f, 6.45f, 9.07f, 7.57f);
        pa.cubicTo(9.18f, 7.92f, 9.10f, 8.31f, 8.82f, 8.59f);
        pa.lineTo(6.62f, 10.79f);
        pa.close();
        return pa;
    }

    private void layer(Canvas cv, Path path, boolean fill) {
        if (glow) {
            float[] w = {4.6f, 3.2f, 2.2f};
            int[] a = {28, 55, 110};
            for (int i = 0; i < 3; i++) {
                p.setStyle(fill ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
                p.setStrokeWidth(w[i]);
                p.setColor(Ui.alpha(color, a[i]));
                cv.drawPath(path, p);
            }
        }
        p.setColor(color);
        p.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        cv.drawPath(path, p);
    }
}
