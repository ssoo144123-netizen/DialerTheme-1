package com.example.dialertheme;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

public class CallService extends InCallService {

    private static final String CHANNEL = "calls";
    private static final int NOTIF_ID = 77;

    private int lastKey = 0;

    /** پخش آهنگ زنگ انتخابی کاربر برای تماس ورودی */
    private static MediaPlayer ringer;

    private void startCustomRing() {
        String uri = getSharedPreferences("ringer", MODE_PRIVATE).getString("uri", null);
        if (uri == null) return; // پیش‌فرض گوشی
        stopCustomRing();
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioStreamType(AudioManager.STREAM_RING);
            mp.setDataSource(this, Uri.parse(uri));
            mp.setLooping(true);
            mp.prepare();
            mp.start();
            ringer = mp;
        } catch (Exception e) {
            mp.release();
        }
    }

    private static void stopCustomRing() {
        if (ringer != null) {
            try {
                ringer.stop();
            } catch (Exception ignored) {
            }
            ringer.release();
            ringer = null;
        }
    }

    private final CallManager.Listener listener = new CallManager.Listener() {
        @Override
        public void onChanged() {
            refreshNotification();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        CallManager.service = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "تماس‌ها",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
        CallManager.addListener(listener);
    }

    @Override
    public void onDestroy() {
        stopCustomRing();
        CallManager.removeListener(listener);
        CallManager.service = null;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIF_ID);
        super.onDestroy();
    }

    private Intent activityIntent() {
        return new Intent(this, InCallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        // تماس ورودی از شماره مسدود: بی‌صدا رد می‌شود و هیچ صفحه‌ای باز نمی‌شود
        if (call.getState() == Call.STATE_RINGING
                && InCallActivity.TimedBlock.isBlocked(this, CallManager.number(call))) {
            call.reject(false, null);
            return;
        }
        CallManager.add(call);
        if (call.getState() == Call.STATE_RINGING) {
            startCustomRing();
            call.registerCallback(new Call.Callback() {
                @Override
                public void onStateChanged(Call c, int state) {
                    if (state != Call.STATE_RINGING) stopCustomRing();
                }
            });
        }
        try {
            startActivity(activityIntent());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        CallManager.remove(call);
        stopCustomRing();
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        super.onCallAudioStateChanged(audioState);
        if (audioState != null) {
            CallManager.muted = audioState.isMuted();
            CallManager.route = audioState.getRoute();
        }
        CallManager.notifyChanged();
    }

    private void refreshNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Call c = CallManager.primary();
        if (c == null) {
            nm.cancel(NOTIF_ID);
            lastKey = 0;
            return;
        }
        boolean ringing = c.getState() == Call.STATE_RINGING;
        int key = ringing ? 1 : 2;
        if (key == lastKey) return;
        lastKey = key;

        PendingIntent pi = PendingIntent.getActivity(this, 0, activityIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.sym_action_call);
        b.setContentTitle(ringing ? "تماس ورودی" : "تماس در حال انجام");
        b.setContentText(CallManager.title(this, c));
        b.setContentIntent(pi);
        b.setOngoing(true);
        b.setCategory(Notification.CATEGORY_CALL);
        if (ringing) {
            b.setFullScreenIntent(pi, true);
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
        }
        nm.notify(NOTIF_ID, b.build());
    }
}
