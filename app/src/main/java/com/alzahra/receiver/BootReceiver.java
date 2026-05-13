package com.alzahra.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import com.alzahra.MainActivity;
import com.alzahra.service.CoreService;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "AlZahraBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Boot received: " + action);
        
        SharedPreferences prefs = context.getSharedPreferences("alzahra_prefs", Context.MODE_PRIVATE);
        
        if (!prefs.getBoolean("configured", false)) {
            Log.d(TAG, "Not configured, starting MainActivity");
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(mainIntent);
            return;
        }
        
        Log.d(TAG, "Configured, starting CoreService");
        Intent serviceIntent = new Intent(context, CoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
