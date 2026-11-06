package com.example.dialertheme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/** Synthwave style background: starry sky, glowing sun and a perspective grid. */
public class NeonArtDrawable extends Drawable {
    private final AppTheme t;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NeonArtDrawable(AppTheme t) {
        this.t = t;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        float u = w / 360f;
        float horizon = h * 0.66f;
        float cx = w / 2f;
        float r = w * 0.27f;
        float cy = horizon - r * 0.2f;

        // sky
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, horizon, t.bg, t.bg2, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon, p);
        p.setShader(null);

        // stars
        Random rnd = new Random(7);
        for (int i = 0; i < 45; i++) {
            float x = rnd.nextFloat() * w;
            float y = rnd.nextFloat() * horizon * 0.8f;
            float rr = (0.6f + rnd.nextFloat() * 1.3f) * u;
            p.setColor(Ui.alpha(t.text, 60 + rnd.nextInt(90)));
            c.drawCircle(x, y, rr, p);
        }

        // sun glow + sun
        p.setShader(new RadialGradient(cx, cy, r * 2.1f, Ui.alpha(t.accent, 80),
                Ui.alpha(t.accent, 0), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 2.1f, p);
        p.setShader(null);

        c.save();
        c.clipRect(0, 0, w, horizon);
        p.setShader(new LinearGradient(0, cy - r, 0, cy + r, Ui.alpha(t.primary, 150),
                Ui.alpha(t.accent, 175), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
        c.restore();

        // ground
        p.setShader(new LinearGradient(0, horizon, 0, h, t.bg2, t.bg, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, w, h, p);
        p.setShader(null);

        // grid
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * u);
        p.setColor(Ui.alpha(t.primary, 90));
        for (int i = 1; i <= 9; i++) {
            float f = i / 9f;
            float y = horizon + (h - horizon) * f * f;
            c.drawLine(0, y, w, y, p);
        }
        for (int k = -9; k <= 9; k++) {
            c.drawLine(cx, horizon, cx + k * w * 0.2f, h, p);
        }

        // horizon line with glow
        p.setStrokeWidth(7f * u);
        p.setColor(Ui.alpha(t.primary, 50));
        c.drawLine(0, horizon, w, horizon, p);
        p.setStrokeWidth(2.5f * u);
        p.setColor(Ui.alpha(t.primary, 210));
        c.drawLine(0, horizon, w, horizon, p);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
