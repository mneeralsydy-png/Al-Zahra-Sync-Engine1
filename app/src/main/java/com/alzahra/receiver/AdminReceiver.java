package com.alzahra.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "AlZahraAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.d(TAG, "Admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.d(TAG, "Admin disabled");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "سيتم تعطيل حماية الجهاز";
    }
}
