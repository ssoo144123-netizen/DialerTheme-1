package com.example.dialertheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class AppTheme {
    public static final int GREEN = 0xFF2E9E4F;
    public static final int RED = 0xFFD93025;

    public final String name;
    public final int bg, bg2, surface, primary, accent, onPrimary, text, text2;
    public final boolean neon, art;

    AppTheme(String name, int bg, int bg2, int surface, int primary, int accent,
             int onPrimary, int text, int text2, boolean neon, boolean art) {
        this.name = name;
        this.bg = bg;
        this.bg2 = bg2;
        this.surface = surface;
        this.primary = primary;
        this.accent = accent;
        this.onPrimary = onPrimary;
        this.text = text;
        this.text2 = text2;
        this.neon = neon;
        this.art = art;
    }

    public static final AppTheme[] ALL = {
        // ---- neon themes (with synthwave art background)
        new AppTheme("نئون سایان",  0xFF0A0420, 0xFF2A0B4D, 0xFF160A33, 0xFF00F0FF, 0xFFFF2BD6, 0xFF00121A, 0xFFEFFBFF, 0xFFA79BD6, true, true),
        new AppTheme("نئون صورتی",  0xFF14001F, 0xFF40104A, 0xFF250A33, 0xFFFF4FD8, 0xFFFF9A3C, 0xFF1F001A, 0xFFFFEAFB, 0xFFD0A5DC, true, true),
        new AppTheme("سایبرپانک",   0xFF0D0221, 0xFF2D1655, 0xFF1A0B3A, 0xFFFFE600, 0xFFFF3CAC, 0xFF1A1400, 0xFFFFF8D6, 0xFFBFAEE0, true, true),
        new AppTheme("نئون سبز",    0xFF020F0A, 0xFF053322, 0xFF06201A, 0xFF39FF14, 0xFF00E5C8, 0xFF031A06, 0xFFE6FFE9, 0xFF86D3A6, true, true),
        // ---- classic themes
        new AppTheme("روشن",        0xFFFFFFFF, 0xFFF1F3F4, 0xFFF1F3F4, 0xFF1A73E8, 0xFF1A73E8, 0xFFFFFFFF, 0xFF202124, 0xFF5F6368, false, false),
        new AppTheme("تیره",        0xFF121212, 0xFF1C1C1E, 0xFF1E1E1E, 0xFF8AB4F8, 0xFF8AB4F8, 0xFF202124, 0xFFE8EAED, 0xFF9AA0A6, false, false),
        new AppTheme("مشکی کامل",   0xFF000000, 0xFF000000, 0xFF151515, 0xFFBB86FC, 0xFFBB86FC, 0xFF000000, 0xFFFFFFFF, 0xFFB0B0B0, false, false),
        new AppTheme("اقیانوس",     0xFF0B1F2E, 0xFF14405F, 0xFF12304A, 0xFF26C6DA, 0xFF26C6DA, 0xFF00222B, 0xFFE0F7FA, 0xFF80CBC4, false, false),
        new AppTheme("جنگل",        0xFF0F1F14, 0xFF1F4A31, 0xFF1B3324, 0xFF66BB6A, 0xFF66BB6A, 0xFF0B1F10, 0xFFE8F5E9, 0xFFA5D6A7, false, false),
        new AppTheme("صورتی",       0xFFFFF5F7, 0xFFFCE4EC, 0xFFFCE4EC, 0xFFD81B60, 0xFFD81B60, 0xFFFFFFFF, 0xFF3E2027, 0xFF8E5A66, false, false),
        new AppTheme("غروب",        0xFF2B1B17, 0xFF4E2D22, 0xFF3E2723, 0xFFFF7043, 0xFFFF7043, 0xFF2B0F08, 0xFFFFF3E0, 0xFFBCAAA4, false, false),
        new AppTheme("بنفش",        0xFFF8F5FF, 0xFFEDE7F6, 0xFFEDE7F6, 0xFF6A1B9A, 0xFF6A1B9A, 0xFFFFFFFF, 0xFF2A1B3D, 0xFF6E5C82, false, false),
    };

    private static final float[] SCALES = {0.9f, 1f, 1.15f, 1.3f};

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("theme2", Context.MODE_PRIVATE);
    }

    public static int index(Context c) {
        int i = prefs(c).getInt("theme", 0);
        return (i < 0 || i >= ALL.length) ? 0 : i;
    }

    public static AppTheme current(Context c) {
        return ALL[index(c)];
    }

    public static void setCurrent(Context c, int i) {
        prefs(c).edit().putInt("theme", i).apply();
    }

    public static int sizeIndex(Context c) {
        int i = prefs(c).getInt("size", 1);
        return (i < 0 || i >= SCALES.length) ? 1 : i;
    }

    public static void setSize(Context c, int i) {
        prefs(c).edit().putInt("size", i).apply();
    }

    public static float scale(Context c) {
        return SCALES[sizeIndex(c)];
    }

    public boolean isLight() {
        double lum = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg);
        return lum > 140;
    }
}
