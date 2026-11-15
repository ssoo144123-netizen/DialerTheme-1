package com.example.dialertheme;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.BlockedNumberContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * مسدودسازی زمان‌دار: شماره را به مدت DURATION_MS در لیست مسدودهای اندروید نگه می‌دارد
 * و پس از آن حذف می‌کند. زمان انقضا در SharedPreferences ذخیره می‌شود تا بعد از
 * ری‌استارت گوشی هم زمان‌بندی بازسازی شود.
 */
public final class TimedBlock {

    public static final long DURATION_MS = 5 * 60 * 1000L;
    private static final String PREFS = "timed_blocks";

    private TimedBlock() {
    }

    public static boolean isSupported(Context ctx) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && BlockedNumberContract.canCurrentUserBlockNumbers(ctx);
    }

    public static boolean isBlocked(Context ctx, String number) {
        return BlockedNumberContract.isBlocked(ctx, number);
    }

    /** شماره را برای مدت DURATION_MS مسدود می‌کند. */
    public static void block(Context ctx, String number) {
        ContentValues values = new ContentValues();
        values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
        ctx.getContentResolver().insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values);

        long expiry = System.currentTimeMillis() + DURATION_MS;
        prefs(ctx).edit().putLong(number, expiry).apply();
        schedule(ctx, number, expiry);
    }

    /** شماره را از لیست مسدودها حذف و زمان‌بندی آن را لغو می‌کند. */
    public static void unblock(Context ctx, String number) {
        try {
            ctx.getContentResolver().delete(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "=?",
                    new String[]{number});
        } catch (Exception ignored) {
        }
        prefs(ctx).edit().remove(number).apply();

        PendingIntent pi = pending(ctx, number, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi);
            pi.cancel();
        }
    }

    /** بعد از ری‌استارت: شماره‌های منقضی‌شده را آزاد و بقیه را دوباره زمان‌بندی می‌کند. */
    public static void rescheduleAll(Context ctx) {
        long now = System.currentTimeMillis();
        List<String> numbers = new ArrayList<>();
        for (Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            numbers.add(e.getKey());
        }
        for (String number : numbers) {
            long expiry = prefs(ctx).getLong(number, 0L);
            if (expiry <= now) {
                unblock(ctx, number);
            } else {
                schedule(ctx, number, expiry);
            }
        }
    }

    private static void schedule(Context ctx, String number, long expiry) {
        PendingIntent pi = pending(ctx, number, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null && pi != null) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiry, pi);
        }
    }

    private static PendingIntent pending(Context ctx, String number, int flags) {
        Intent i = new Intent(ctx, UnblockReceiver.class);
        i.putExtra(UnblockReceiver.EXTRA_NUMBER, number);
        return PendingIntent.getBroadcast(ctx, number.hashCode(), i,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
