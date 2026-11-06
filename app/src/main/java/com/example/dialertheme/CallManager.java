package com.example.dialertheme;

import android.content.Context;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class CallManager {

    public interface Listener {
        void onChanged();
    }

    private static final List<Call> calls = new ArrayList<>();
    private static final List<Listener> listeners = new ArrayList<>();

    public static CallService service;
    public static boolean muted = false;
    public static int route = CallAudioState.ROUTE_EARPIECE;

    private static final Call.Callback callback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            notifyChanged();
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            notifyChanged();
        }
    };

    public static void add(Call c) {
        if (!calls.contains(c)) {
            calls.add(c);
            c.registerCallback(callback);
        }
        notifyChanged();
    }

    public static void remove(Call c) {
        calls.remove(c);
        c.unregisterCallback(callback);
        notifyChanged();
    }

    public static int count() {
        return calls.size();
    }

    public static Call primary() {
        Call hold = null, other = null;
        for (Call c : calls) {
            int s = c.getState();
            if (s == Call.STATE_RINGING) return c;
            if (s == Call.STATE_HOLDING) {
                if (hold == null) hold = c;
            } else if (s != Call.STATE_DISCONNECTED && other == null) {
                other = c;
            }
        }
        if (other != null) return other;
        if (hold != null) return hold;
        return calls.isEmpty() ? null : calls.get(0);
    }

    public static void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) l.onChanged();
    }

    public static String number(Call c) {
        Call.Details d = c.getDetails();
        if (d == null) return null;
        Uri h = d.getHandle();
        if (h == null) return null;
        return Uri.decode(h.getSchemeSpecificPart());
    }

    public static String title(Context ctx, Call c) {
        String number = number(c);
        Call.Details d = c.getDetails();
        String name = ContactUtil.lookupName(ctx, number);
        if (TextUtils.isEmpty(name) && d != null) name = d.getCallerDisplayName();
        if (!TextUtils.isEmpty(name)) return name;
        return TextUtils.isEmpty(number) ? "ناشناس" : number;
    }
}
