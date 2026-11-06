package com.example.dialertheme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

public class Ui {

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int alpha(int color, int a) {
        return ((a & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    public static GradientDrawable rect(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Round key background: translucent surface with neon outline. */
    public static GradientDrawable keyBg(Context c, AppTheme t) {
        GradientDrawable g = circle(alpha(t.surface, 0xCC));
        if (t.neon) g.setStroke(dp(c, 1.5f), alpha(t.primary, 0xD0));
        else g.setStroke(dp(c, 1), alpha(t.text2, 0x55));
        return g;
    }

    public static GradientDrawable avatarBg(Context c, AppTheme t) {
        GradientDrawable g = circle(alpha(t.surface, 0xE0));
        g.setStroke(dp(c, 2), alpha(t.primary, 0xE0));
        return g;
    }

    /** Filled circle with a soft halo ring. */
    public static Drawable glowButton(Context c, int color) {
        GradientDrawable outer = circle(alpha(color, 70));
        GradientDrawable inner = circle(color);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{outer, inner});
        int in = dp(c, 5);
        ld.setLayerInset(1, in, in, in, in);
        return ld;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * AppTheme.scale(c));
        tv.setTextColor(color);
        return tv;
    }

    public static void glowText(TextView tv, int color, AppTheme t) {
        if (t.neon) tv.setShadowLayer(16f, 0f, 0f, color);
    }

    public static TextView pill(Context c, String s, int bg, int fg) {
        TextView tv = text(c, s, 15, fg);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        tv.setBackground(rect(c, bg, 24));
        return tv;
    }

    /** Background for all screens: user picture, neon art, or soft gradient. */
    public static Drawable background(Context c, AppTheme t) {
        Drawable wp = Wallpaper.load(c);
        if (wp != null) {
            return new LayerDrawable(new Drawable[]{wp, new ColorDrawable(alpha(t.bg, 0xB8))});
        }
        if (t.art) return new NeonArtDrawable(t);
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{t.bg, t.bg2});
    }

    public static void styleWindow(Activity a, AppTheme t) {
        Window w = a.getWindow();
        w.setBackgroundDrawable(background(a, t));
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        int flags = 0;
        if (t.isLight()) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }
}
