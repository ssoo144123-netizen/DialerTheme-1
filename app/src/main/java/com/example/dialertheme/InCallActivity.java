package com.example.dialertheme;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.BlockedNumberContract;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
        FrameLayout avatarBox = new FrameLayout(this);
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
        Ui.glowText(nameTv,
