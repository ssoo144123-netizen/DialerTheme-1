package com.example.dialertheme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UnblockReceiver extends BroadcastReceiver {

    public static final String EXTRA_NUMBER = "number";

    @Override
    public void onReceive(Context context, Intent intent) {
        String number = intent.getStringExtra(EXTRA_NUMBER);
        if (number != null) {
            TimedBlock.unblock(context, number);
        }
    }
}
