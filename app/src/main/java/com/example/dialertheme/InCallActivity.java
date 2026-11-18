package com.example.dialertheme;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class InCallActivity extends Activity {

    private AppTheme t;
    private TextView avatar, nameTv, numTv, statusTv;
    private View ring;
    private LinearLayout controls;
    private ObjectAnimator pulse;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finishScheduled = false;
    private boolean showKeypad = false;      // صفحه‌کلید DTMF درون تماس
    private FrameLayout avatarBox;           // آواتار بزرگ بالای صفحه
    private LinearLayout msgRow;             // دکمه‌های کوچک واتساپ و پیامک
    private TextView waBtn, smsBtn;
    private String dtmfSent = "";            // کدهایی که تا الان فرستاده شده

    private final CallManager.Listener listener = new CallManager.Listener() {
        @Override
        public void onChanged() {
            refresh();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Call c = CallManager.primary();
            if (c != null) statusTv.setText(statusText(c));
            handler.postDelayed(this, 1000);
        }
    };

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        t = AppTheme.current(this);
        Ui.styleWindow(this, t);

        if (CallManager.primary() == null) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(24), dp(56), dp(24), dp(28));

        // avatar with pulsing neon ring
        avatarBox = new FrameLayout(this);
        ring = new View(this);
        GradientDrawable rg = new GradientDrawable();
        rg.setShape(GradientDrawable.OVAL);
        rg.setColor(Color.TRANSPARENT);
        rg.setStroke(dp(3), t.primary);
        ring.setBackground(rg);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(136), dp(136));
        rp.gravity = Gravity.CENTER;
        avatarBox.addView(ring, rp);

        avatar = Ui.text(this, "", 44, t.primary);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.avatarBg(this, t));
        Ui.glowText(avatar, t.primary, t);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(dp(120), dp(120));
        ap.gravity = Gravity.CENTER;
        avatarBox.addView(avatar, ap);
        root.addView(avatarBox, new LinearLayout.LayoutParams(dp(180), dp(180)));

        nameTv = Ui.text(this, "", 28, t.text);
        nameTv.setTypeface(null, Typeface.BOLD);
        nameTv.setGravity(Gravity.CENTER);
        Ui.glowText(nameTv, t.accent, t);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(12);
        root.addView(nameTv, np);

        numTv = Ui.text(this, "", 16, t.text2);
        numTv.setGravity(Gravity.CENTER);
        numTv.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(numTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        msgRow = new LinearLayout(this);
        msgRow.setGravity(Gravity.CENTER);
        waBtn = Ui.pill(this, "واتساپ", t.primary, t.onPrimary);
        smsBtn = Ui.pill(this, "پیامک", t.surface, t.text);
        for (TextView mb : new TextView[]{waBtn, smsBtn}) {
            mb.setTextSize(10);
            mb.setPadding(dp(6), 0, dp(6), 0);
            mb.setVisibility(View.GONE);
        }
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22));
        mp.setMargins(dp(4), dp(4), dp(4), dp(4));
        msgRow.addView(waBtn, mp);
        msgRow.addView(smsBtn, mp);
        root.addView(msgRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusTv = Ui.text(this, "", 18, t.primary);
        statusTv.setGravity(Gravity.CENTER);
        Ui.glowText(statusTv, t.primary, t);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(10);
        root.addView(statusTv, sp);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        CallManager.addListener(listener);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusTv != null) {
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        CallManager.removeListener(listener);
        handler.removeCallbacksAndMessages(null);
        setPulse(false);
        super.onDestroy();
    }

    private void setPulse(boolean on) {
        if (ring == null) return;
        if (on) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofPropertyValuesHolder(ring,
                        PropertyValuesHolder.ofFloat("scaleX", 1f, 1.3f),
                        PropertyValuesHolder.ofFloat("scaleY", 1f, 1.3f),
                        PropertyValuesHolder.ofFloat("alpha", 0.9f, 0.05f));
                pulse.setDuration(1300);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.setInterpolator(new DecelerateInterpolator());
                pulse.start();
            }
        } else if (pulse != null) {
            pulse.cancel();
            pulse = null;
            ring.setScaleX(1f);
            ring.setScaleY(1f);
            ring.setAlpha(0.6f);
        }
    }

    private void syncAudio() {
        CallService svc = CallManager.service;
        if (svc == null) return;
        CallAudioState as = svc.getCallAudioState();
        if (as != null) {
            CallManager.muted = as.isMuted();
            CallManager.route = as.getRoute();
        }
    }

    private void openWhatsAppChat(String number) {
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        else if (digits.startsWith("0") && digits.length() == 11) digits = "98" + digits.substring(1);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits)));
        } catch (Exception e) {
            Toast.makeText(this, "واتساپ پیدا نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSmsChat(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))));
        } catch (Exception e) {
            Toast.makeText(this, "برنامه‌ی پیامک پیدا نشد", Toast.LENGTH_SHORT).show();
        }
    }

    /** آیا متن فقط شماره (یا خالی) است؟ */
    private static boolean looksLikeNumber(String s) {
        return s == null || s.trim().isEmpty() || s.replaceAll("[0-9+\\-()\\s]", "").isEmpty();
    }

    /** نام ذخیره‌شده‌ی یک شماره‌ی شماره‌گیری سریع (یا null) */
    static String speedNameFor(Context ctx, String number) {
        String want = digitsTail(number);
        if (want == null) return null;
        android.content.SharedPreferences p = ctx.getSharedPreferences("speed", Context.MODE_PRIVATE);
        for (java.util.Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith("d") || k.endsWith("n")) continue;
            Object v = e.getValue();
            if (!(v instanceof String)) continue;
            if (want.equals(digitsTail((String) v))) {
                String name = p.getString(k + "n", null);
                return (name == null || name.isEmpty()) ? null : name;
            }
        }
        return null;
    }

    private static String digitsTail(String n) {
        if (n == null) return null;
        String d = n.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return null;
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    private void refresh() {
        if (statusTv == null) return;
        final Call c = CallManager.primary();
        if (c == null) {
            setPulse(false);
            statusTv.setText("تماس پایان یافت");
            controls.removeAllViews();
            if (!finishScheduled) {
                finishScheduled = true;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                }, 800);
            }
            return;
        }
        syncAudio();
        String title = CallManager.title(this, c);
        // اگر مخاطب در دفترچه نیست، نامِ ذخیره‌شده‌ی خانه‌ی شماره‌گیری سریع را نشان بده
        String speedName = speedNameFor(this, CallManager.number(c));
        if (speedName != null && looksLikeNumber(title)) title = speedName;
        String number = CallManager.number(c);
        nameTv.setText(title);
        numTv.setText((number != null && !number.equals(title)) ? number : "");
        final String msgNumber = number;
        boolean hasNumber = msgNumber != null && !msgNumber.isEmpty();
        waBtn.setVisibility(hasNumber ? View.VISIBLE : View.GONE);
        smsBtn.setVisibility(hasNumber ? View.VISIBLE : View.GONE);
        waBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWhatsAppChat(msgNumber);
            }
        });
        smsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSmsChat(msgNumber);
            }
        });
        avatar.setText(initial(title));
        statusTv.setText(statusText(c));
        int s = c.getState();
        setPulse(s == Call.STATE_RINGING || s == Call.STATE_DIALING || s == Call.STATE_CONNECTING);
        buildControls(c);
    }

    private String initial(String title) {
        if (TextUtils.isEmpty(title)) return "#";
        char ch = title.charAt(0);
        return Character.isLetter(ch) ? String.valueOf(ch).toUpperCase() : "#";
    }

    private String statusText(Call c) {
        switch (c.getState()) {
            case Call.STATE_RINGING:
                return "تماس ورودی";
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING:
                return "در حال برقراری تماس…";
            case Call.STATE_HOLDING:
                return "در انتظار";
            case Call.STATE_ACTIVE:
                long ct = c.getDetails().getConnectTimeMillis();
                long sec = ct > 0 ? (System.currentTimeMillis() - ct) / 1000 : 0;
                if (sec < 0) sec = 0;
                return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
            case Call.STATE_DISCONNECTED:
                return "پایان تماس";
            default:
                return "…";
        }
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(24);
        r.setLayoutParams(p);
        return r;
    }

    private View circleButton(int icon, String label, Drawable bg, int iconColor,
                              boolean glowIcon, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER);
        c.setBackground(bg);
        c.setOnClickListener(l);
        c.addView(new IconView(this, icon, iconColor, glowIcon),
                new LinearLayout.LayoutParams(dp(32), dp(32)));
        col.addView(c, new LinearLayout.LayoutParams(dp(74), dp(74)));

        TextView lb = Ui.text(this, label, 13, t.text2);
        lb.setGravity(Gravity.CENTER);
        lb.setPadding(0, dp(6), 0, 0);
        col.addView(lb);
        return col;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View toggle(int icon, String label, boolean active, View.OnClickListener l) {
        Drawable bg = active ? Ui.glowButton(this, t.primary) : Ui.keyBg(this, t);
        int ic = active ? t.onPrimary : t.text;
        return circleButton(icon, label, bg, ic, !active && t.neon, l);
    }

    private void blockAndReject(Call c) {
        String number = CallManager.number(c);
        if (TextUtils.isEmpty(number)) {
            Toast.makeText(this, "شماره ناشناس قابل مسدودسازی نیست", Toast.LENGTH_SHORT).show();
        } else if (TimedBlock.isBlocked(this, number)) {
            Toast.makeText(this, "این شماره از قبل مسدود است", Toast.LENGTH_SHORT).show();
        } else {
            TimedBlock.block(this, number);
            Toast.makeText(this, "شماره مسدود شد", Toast.LENGTH_SHORT).show();
        }
        c.reject(false, null);
    }

    private View dtmfPanel(final Call c) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);

        TextView sent = Ui.text(this, dtmfSent, 20, t.primary);
        sent.setGravity(Gravity.CENTER);
        sent.setMinHeight(dp(36));
        panel.addView(sent, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        for (String[] rowKeys : keys) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER);
            for (final String k : rowKeys) {
                TextView b = Ui.text(this, k, 22, t.text);
                b.setGravity(Gravity.CENTER);
                b.setBackground(Ui.circle(Ui.alpha(t.surface, 0xE0)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(50));
                lp.setMargins(dp(5), dp(2), dp(5), dp(2));
                b.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent e) {
                        switch (e.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                c.playDtmfTone(k.charAt(0));
                                dtmfSent = dtmfSent + k;
                                sent.setText(dtmfSent);
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                c.stopDtmfTone();
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                r.addView(b, lp);
            }
            panel.addView(r);
        }
        return panel;
    }

    private void buildControls(final Call c) {
        controls.removeAllViews();
        int s = c.getState();
        boolean keypadOpen = showKeypad && (s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING);
        if (avatarBox != null) {
            avatarBox.setVisibility(keypadOpen ? View.GONE : View.VISIBLE);
        }

        if (s == Call.STATE_RINGING) {
            LinearLayout r = row();
            r.addView(circleButton(IconView.END, "رد", Ui.glowButton(this, AppTheme.RED),
                    Color.WHITE, false, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            c.reject(false, null);
                        }
                    }), weight());
            r.addView(circleButton(IconView.PHONE, "پاسخ", Ui.glowButton(this, AppTheme.GREEN),
                    Color.WHITE, false, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            c.answer(VideoProfile.STATE_AUDIO_ONLY);
                        }
                    }), weight());
            r.addView(circleButton(IconView.END, "مسدود", Ui.keyBg(this, t),
                    t.text, false, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            blockAndReject(c);
                        }
                    }), weight());
            controls.addView(r);
            return;
        }

        boolean muted = CallManager.muted;
        boolean spk = CallManager.route == CallAudioState.ROUTE_SPEAKER;
        boolean hold = s == Call.STATE_HOLDING;

        LinearLayout r = row();
        r.addView(toggle(muted ? IconView.MIC_OFF : IconView.MIC, "بی‌صدا", muted,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CallService svc = CallManager.service;
                        if (svc != null) svc.setMuted(!CallManager.muted);
                    }
                }), weight());
        r.addView(toggle(IconView.SPEAKER, "بلندگو", spk,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CallService svc = CallManager.service;
                        if (svc != null) {
                            boolean on = CallManager.route == CallAudioState.ROUTE_SPEAKER;
                            svc.setAudioRoute(on ? CallAudioState.ROUTE_WIRED_OR_EARPIECE
                                    : CallAudioState.ROUTE_SPEAKER);
                        }
                    }
                }), weight());
        if (s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING) {
            r.addView(toggle(IconView.PAUSE, "نگه‌داشتن", hold,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (c.getState() == Call.STATE_HOLDING) c.unhold();
                            else c.hold();
                        }
                    }), weight());
            r.addView(toggle(IconView.KEYPAD, "صفحه‌کلید", showKeypad,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showKeypad = !showKeypad;
                            refresh();
                        }
                    }), weight());
        }
        controls.addView(r);

        // صفحه‌کلید DTMF: برای وارد کردن شماره‌ی داخلی بعد از برقراری تماس
        if (showKeypad && (s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING)) {
            controls.addView(dtmfPanel(c));
        }

        LinearLayout end = row();
        end.addView(circleButton(IconView.END, "پایان تماس", Ui.glowButton(this, AppTheme.RED),
                Color.WHITE, false, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        c.disconnect();
                    }
                }), weight());
        controls.addView(end);
    }

    /**
     * مسدودسازی زمان‌دار در سطح برنامه (بدون BlockedNumberContract، سازگار با اندروید ۶ به بالا).
     * شماره و زمان انقضا در SharedPreferences ذخیره می‌شود؛ بنابراین بعد از ری‌استارت هم باقی می‌ماند.
     */
    public static final class TimedBlock {

        public static final int DEFAULT_MINUTES = 5;
        private static final String PREFS = "timed_blocks";
        private static final String SETTINGS = "block_settings";

        private TimedBlock() {
        }

        private static SharedPreferences prefs(Context ctx) {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        /** آیا این شماره در حال حاضر مسدود است؟ شماره‌های منقضی‌شده را پاک می‌کند. */
        public static boolean isBlocked(Context ctx, String number) {
            if (TextUtils.isEmpty(number)) return false;
            SharedPreferences p = prefs(ctx);
            long expiry = p.getLong(number, 0L);
            if (expiry == 0L) return false;
            if (System.currentTimeMillis() >= expiry) {
                p.edit().remove(number).apply();
                return false;
            }
            return true;
        }

        /** شماره‌های مسدودِ هنوز فعال (منقضی‌ها حذف می‌شوند). */
        public static java.util.List<String> activeNumbers(Context ctx) {
            java.util.List<String> result = new java.util.ArrayList<>();
            long now = System.currentTimeMillis();
            SharedPreferences p = prefs(ctx);
            for (java.util.Map.Entry<String, ?> e : p.getAll().entrySet()) {
                long expiry = p.getLong(e.getKey(), 0L);
                if (expiry > now) {
                    result.add(e.getKey());
                } else {
                    p.edit().remove(e.getKey()).apply();
                }
            }
            return result;
        }

        /** زمان انقضای یک شماره (میلی‌ثانیه از epoch). */
        public static long expiryOf(Context ctx, String number) {
            return prefs(ctx).getLong(number, 0L);
        }

        /** رفع مسدودی یک شماره. */
        public static void unblock(Context ctx, String number) {
            prefs(ctx).edit().remove(number).apply();
        }

        /** رفع مسدودی همه‌ی شماره‌ها. */
        public static void unblockAll(Context ctx) {
            prefs(ctx).edit().clear().apply();
        }

        /** مدت مسدودی انتخاب‌شده در تنظیمات (دقیقه). */
        public static int durationMinutes(Context ctx) {
            return ctx.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
                    .getInt("minutes", DEFAULT_MINUTES);
        }

        public static void setDurationMinutes(Context ctx, int minutes) {
            ctx.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
                    .edit().putInt("minutes", minutes).apply();
        }

        /** شماره را برای مدت انتخاب‌شده در تنظیمات مسدود می‌کند. */
        public static void block(Context ctx, String number) {
            long duration = durationMinutes(ctx) * 60000L;
            prefs(ctx).edit()
                    .putLong(number, System.currentTimeMillis() + duration)
                    .apply();
        }
    }
}
