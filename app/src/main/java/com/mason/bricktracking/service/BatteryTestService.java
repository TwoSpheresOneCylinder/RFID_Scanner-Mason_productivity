package com.mason.bricktracking.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.mason.bricktracking.R;
import com.mason.bricktracking.ui.MainActivity;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class BatteryTestService extends Service {

    private static final String CHANNEL_ID = "battery_test_channel";
    private static final int NOTIFICATION_ID = 1001;

    private RFIDWithUHFBLE uhf;
    private Handler handler;
    private Runnable scanRunnable;
    private Runnable batteryLogRunnable;
    private Random random = new Random();
    private PowerManager.WakeLock wakeLock;

    private FileWriter logWriter;
    private File logFile;
    private long testStartTime;
    private int totalScans = 0;
    private int lastLoggedBattery = -1;
    private String masonId = "";

    private static boolean isRunning = false;

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        uhf = RFIDWithUHFBLE.getInstance();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            masonId = intent.getStringExtra("masonId");
            if (masonId == null) masonId = "UNKNOWN";
        }

        // Acquire wake lock to keep CPU running with screen off
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MasonBrickTracking::BatteryTest");
        wakeLock.acquire();

        // Start as foreground service with persistent notification
        startForeground(NOTIFICATION_ID, buildNotification("Battery test running..."));

        // Start logging
        startLogging();

        // Start simulated scans
        startSimScans();

        // Start battery logging every 30 seconds
        startBatteryLogging();

        isRunning = true;
        return START_STICKY;
    }

    private void startLogging() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String filename = "battery_log_" + sdf.format(new Date()) + ".csv";
            File logDir = new File(getExternalFilesDir(null), "BatteryLogs");
            if (!logDir.exists()) logDir.mkdirs();

            logFile = new File(logDir, filename);
            logWriter = new FileWriter(logFile, true);
            logWriter.append("Timestamp,Battery %,Elapsed Minutes,Activity,Total Scans,Mason ID\n");
            logWriter.flush();

            testStartTime = System.currentTimeMillis();
            totalScans = 0;
            lastLoggedBattery = -1;

            android.util.Log.d("BATTERY_SERVICE", "Logging started: " + filename);
        } catch (IOException e) {
            android.util.Log.e("BATTERY_SERVICE", "Failed to start logging", e);
        }
    }

    private void startSimScans() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                if (uhf != null && uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    try {
                        uhf.startInventoryTag();
                        totalScans++;
                        android.util.Log.d("BATTERY_SERVICE", "Sim scan #" + totalScans);

                        // Stop inventory after 500ms read window
                        handler.postDelayed(() -> {
                            try {
                                uhf.stopInventory();
                            } catch (Exception e) { /* ignore */ }
                        }, 500);

                        // Update notification
                        updateNotification();
                    } catch (Exception e) {
                        android.util.Log.e("BATTERY_SERVICE", "Scan error", e);
                    }
                }

                // Next scan: random 30-60 seconds
                long nextDelay = 30000 + random.nextInt(30001);
                handler.postDelayed(this, nextDelay);
            }
        };

        long firstDelay = 30000 + random.nextInt(30001);
        handler.postDelayed(scanRunnable, firstDelay);
    }

    private void startBatteryLogging() {
        batteryLogRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                logBattery();
                handler.postDelayed(this, 30000);
            }
        };
        handler.postDelayed(batteryLogRunnable, 30000);
    }

    private void logBattery() {
        if (logWriter == null) return;

        if (uhf != null && uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
            try {
                int battery = uhf.getBattery();

                if (battery != lastLoggedBattery) {
                    long elapsedMs = System.currentTimeMillis() - testStartTime;
                    long minutes = elapsedMs / 60000;

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    String timestamp = sdf.format(new Date());

                    logWriter.append(String.format(Locale.US, "%s,%d,%d,Scanning,%d,%s\n",
                            timestamp, battery, minutes, totalScans, masonId));
                    logWriter.flush();

                    lastLoggedBattery = battery;
                    android.util.Log.d("BATTERY_SERVICE", "Logged: " + battery + "% @ " + minutes + "min");
                }
            } catch (Exception e) {
                android.util.Log.e("BATTERY_SERVICE", "Log error", e);
            }
        }
    }

    private void updateNotification() {
        int battery = -1;
        try {
            if (uhf != null && uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                battery = uhf.getBattery();
            }
        } catch (Exception e) { /* ignore */ }

        long minutes = (System.currentTimeMillis() - testStartTime) / 60000;
        String text = String.format(Locale.US, "Battery: %s | Scans: %d | %d min",
                battery >= 0 ? battery + "%" : "--", totalScans, minutes);

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RFID Battery Test")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Battery Test",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows battery test progress");

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;

        // Stop scheduled tasks
        if (handler != null) {
            if (scanRunnable != null) handler.removeCallbacks(scanRunnable);
            if (batteryLogRunnable != null) handler.removeCallbacks(batteryLogRunnable);
        }

        // Close log file
        try {
            if (logWriter != null) logWriter.close();
        } catch (IOException e) { /* ignore */ }

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        android.util.Log.d("BATTERY_SERVICE", "Service destroyed. Total scans: " + totalScans);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public File getLogFile() {
        return logFile;
    }

    public int getTotalScans() {
        return totalScans;
    }

    public long getTestStartTime() {
        return testStartTime;
    }
}
