package com.alzahra.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import com.alzahra.service.CoreService;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlZahraAlarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm received");
        try {
            Intent serviceIntent = new Intent(context, CoreService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Alarm error", e);
        }
    }
}
