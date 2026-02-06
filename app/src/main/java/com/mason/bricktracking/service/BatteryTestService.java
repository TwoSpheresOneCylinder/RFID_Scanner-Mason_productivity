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

public class BatteryTestService extends Service {

    private static final String CHANNEL_ID = "battery_test_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long LOG_INTERVAL_MS = 30000; // 30 seconds

    private RFIDWithUHFBLE uhf;
    private Handler handler;
    private Runnable batteryLogRunnable;
    private PowerManager.WakeLock wakeLock;

    private FileWriter logWriter;
    private File logFile;
    private long testStartTime;

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
        // Acquire wake lock to keep CPU running with screen off
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MasonBrickTracking::BatteryLog");
        wakeLock.acquire();

        // Start as foreground service
        startForeground(NOTIFICATION_ID, buildNotification("Battery logging started..."));

        // Start CSV logging
        startLogging();

        // Log every 30 seconds
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
            logWriter.append("Timestamp,Battery %,Elapsed Minutes\n");
            logWriter.flush();

            testStartTime = System.currentTimeMillis();
            android.util.Log.d("BATTERY_LOG", "Logging started: " + filename);
        } catch (IOException e) {
            android.util.Log.e("BATTERY_LOG", "Failed to start logging", e);
        }
    }

    private void startBatteryLogging() {
        // Log immediately on start
        logBattery();

        batteryLogRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                logBattery();
                handler.postDelayed(this, LOG_INTERVAL_MS);
            }
        };
        handler.postDelayed(batteryLogRunnable, LOG_INTERVAL_MS);
    }

    private void logBattery() {
        if (logWriter == null) return;

        if (uhf != null && uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
            try {
                int battery = uhf.getBattery();
                if (battery < 0) return;

                long elapsedMinutes = (System.currentTimeMillis() - testStartTime) / 60000;

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                String timestamp = sdf.format(new Date());

                logWriter.append(String.format(Locale.US, "%s,%d,%d\n",
                        timestamp, battery, elapsedMinutes));
                logWriter.flush();

                // Update notification with current reading
                updateNotification(battery, elapsedMinutes);

                android.util.Log.d("BATTERY_LOG", "Logged: " + battery + "% @ " + elapsedMinutes + "min");
            } catch (Exception e) {
                android.util.Log.e("BATTERY_LOG", "Log error", e);
            }
        }
    }

    private void updateNotification(int battery, long minutes) {
        String text = String.format(Locale.US, "Battery: %d%% | Running: %d min", battery, minutes);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Battery Logger")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Battery Logger",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows battery logging progress");

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;

        if (handler != null && batteryLogRunnable != null) {
            handler.removeCallbacks(batteryLogRunnable);
        }

        try {
            if (logWriter != null) logWriter.close();
        } catch (IOException e) { /* ignore */ }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        android.util.Log.d("BATTERY_LOG", "Service stopped");
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

    public long getTestStartTime() {
        return testStartTime;
    }
}
