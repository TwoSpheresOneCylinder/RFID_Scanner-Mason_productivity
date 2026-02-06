package com.mason.bricktracking.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Color;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.mason.bricktracking.MasonApp;
import com.mason.bricktracking.R;
import com.mason.bricktracking.data.model.BrickPlacement;
import com.mason.bricktracking.sync.SyncManager;
import com.mason.bricktracking.service.BatteryTestService;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {
    
    private TextView tvPlacementCounter, tvLastBrick, tvSyncStatus, tvUnsyncedCount, tvMasonId, tvLastTimestamp;
    private ImageView ivBatteryStatus;
    private Button btnStart, btnStop, btnBack;
    
    private RFIDWithUHFBLE uhf;
    private SyncManager syncManager;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    
    private boolean isScanning = false;
    private int placementCounter = 0;
    private String masonId;
    private Handler mainHandler;
    private Handler scanTimeoutHandler;
    private Runnable scanTimeoutRunnable;
    
    // Build session tracking
    private String currentBuildSessionId;
    private int currentEventSeq = 0;
    
    // Track tags scanned in this session - each tag only counted once
    private Set<String> scannedTagsInSession = new HashSet<>();
    private boolean isAdmin = false;
    private int currentPowerLevel = 28; // Default 5 feet range (28 dBm), loaded from MasonApp in onCreate

    // Windowed capture for best-candidate selection
    private Map<String, List<TagRead>> captureWindow = new HashMap<>();
    private Handler captureWindowHandler;
    private Runnable captureWindowTimeout;
    private boolean isCapturing = false;
    private long captureStartTime = 0;
    
    // Field-tunable parameters (adjustable via admin menu)
    private long captureWindowMs = 350; // Default 350ms, range 250-500ms
    private int rssiAmbiguityThresholdDb = 5; // Default 5dB (relaxed for field), range 3-7dB
    private int countAmbiguityThreshold = 1; // Count within 1
    
    // Track power level per placement
    private int currentScanPowerLevel = 33; // Full power by default
    
    // Battery logging
    private boolean isBatteryLoggingEnabled = false;
    
    // Helper class to store individual tag reads
    private static class TagRead {
        String epc;
        int rssi;
        long timestamp;
        
        TagRead(String epc, int rssi, long timestamp) {
            this.epc = epc;
            this.rssi = rssi;
            this.timestamp = timestamp;
        }
    }
    
    // Per-tag cooldown to prevent rapid-fire re-scanning of the same tag
    private Map<String, Long> tagCooldowns = new HashMap<>();
    private static final long SCAN_COOLDOWN_MS = 500; // 0.5 seconds cooldown per tag
    
    // Simple scan mode - stops automatically after each successful scan
    private boolean isPulsing = false;
    
    // GPS location tracking with Fused Location Provider
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastKnownLocation;
    private float lastLocationAccuracy = 999f; // meters
    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final long DUPLICATE_TIME_THRESHOLD = 5 * 60 * 1000; // 5 minutes
    private static final double DUPLICATE_DISTANCE_THRESHOLD = 10.0; // 10 meters
    private static final float MAX_ACCEPTABLE_ACCURACY = 20.0f; // Don't scan if GPS accuracy worse than 20m
    private static final float GOOD_ACCURACY = 10.0f; // Consider GPS "good" if better than 10m
    
    // Store recent placements for duplicate detection
    private Map<String, PlacementRecord> recentPlacements = new HashMap<>();
    
    // Helper class to store placement info for duplicate detection
    private static class PlacementRecord {
        long timestamp;
        double latitude;
        double longitude;
        double altitude;
        float accuracy;
        
        PlacementRecord(long timestamp, double latitude, double longitude, double altitude, float accuracy) {
            this.timestamp = timestamp;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.accuracy = accuracy;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_brick);
        
        // Keep CPU alive when screen is off so scanning continues
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MasonBrickTracking::Scanning");
        wakeLock.acquire();
        
        isAdmin = MasonApp.getInstance().isAdmin();
        
        initViews();
        
        // Load current power level from saved settings BEFORE initializing RFID
        currentPowerLevel = MasonApp.getInstance().getRfidPowerLevel();
        android.util.Log.d("MAIN", "Loaded power level: " + currentPowerLevel + " dBm");
        
        initRFID();
        initSyncManager();
        initLocation();
        loadMasonData();
        setupListeners();
        
        // Set custom action bar with user icon
        if (getSupportActionBar() != null && masonId != null) {
            getSupportActionBar().setDisplayShowCustomEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            View customBar = getLayoutInflater().inflate(R.layout.custom_action_bar, null);
            ((TextView) customBar.findViewById(R.id.action_bar_title))
                .setText(masonId + (isAdmin ? " (Admin)" : ""));
            getSupportActionBar().setCustomView(customBar);
        }
        
        // Clear old test data in admin mode
        if (isAdmin) {
            syncManager.clearUnsyncedPlacements();
            android.util.Log.d("ADMIN", "Cleared old unsynced placements on startup");
        }
        
        fetchInitialCounter();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Sync battery state
        isBatteryLoggingEnabled = BatteryTestService.isRunning();
        updateBatteryStatus();
    }
    
    private void initViews() {
        tvPlacementCounter = findViewById(R.id.tv_placement_counter);
        tvLastBrick = findViewById(R.id.tv_last_brick);
        tvSyncStatus = findViewById(R.id.tv_sync_status);
        tvUnsyncedCount = findViewById(R.id.tv_unsynced_count);
        tvMasonId = findViewById(R.id.tv_mason_id);
        tvLastTimestamp = findViewById(R.id.tv_last_timestamp);
        ivBatteryStatus = findViewById(R.id.iv_battery_status);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnBack = findViewById(R.id.btn_back);
        
        btnStop.setEnabled(false);
        btnStop.setBackgroundResource(R.drawable.button_bg_disabled);
        
        mainHandler = new Handler(Looper.getMainLooper());
        scanTimeoutHandler = new Handler(Looper.getMainLooper());
        captureWindowHandler = new Handler(Looper.getMainLooper());
    }
    
    private void initRFID() {
        uhf = RFIDWithUHFBLE.getInstance();
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        
        // Initialize vibrator for haptic feedback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        
        // Check connection status
        if (uhf.getConnectStatus() != ConnectionStatus.CONNECTED) {
            showConnectionAlert();
        } else {
            // Update battery status on connection
            updateBatteryStatus();
            // Start periodic battery updates
            startBatteryMonitoring();
        }
        
        // Set power level for scanning
        uhf.setPower(currentPowerLevel);
        android.util.Log.d("RFID_INIT", "RFID reader initialized - Power: " + currentPowerLevel + " dBm");
        
        // Set inventory callback - receives continuous tag reads after startInventoryTag()
        android.util.Log.d("RFID_INIT", "Setting up inventory callback for continuous scanning");
        uhf.setInventoryCallback(new IUHFInventoryCallback() {
            @Override
            public void callback(UHFTAGInfo tag) {
                // Log every callback invocation
                android.util.Log.d("SCAN_CALLBACK", "✓ Tag detected via continuous scan | isScanning=" + isScanning);
                
                // Only process tags if scanning session is active
                if (!isScanning) {
                    android.util.Log.w("SCAN_CALLBACK", "⚠ Tag detected but scanning session not active");
                    mainHandler.post(() -> {
                        tvSyncStatus.setText("Press SCAN to Begin");
                        tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                    });
                    return;
                }
                
                if (tag != null && tag.getEPC() != null) {
                    // Normalize EPC - trim whitespace and convert to uppercase
                    String epc = tag.getEPC().trim().toUpperCase();
                    
                    // Get RSSI value - SDK returns String with decimal (e.g. "-75.80"), parse as Float then convert to int
                    int rssi = 0;
                    try {
                        String rssiStr = tag.getRssi();
                        if (rssiStr != null && !rssiStr.isEmpty()) {
                            float rssiFloat = Float.parseFloat(rssiStr.trim());
                            rssi = Math.round(rssiFloat); // Round to nearest integer
                        }
                    } catch (NumberFormatException e) {
                        android.util.Log.w("SCAN_CALLBACK", "Failed to parse RSSI: " + tag.getRssi());
                        rssi = -999; // Sentinel value for invalid RSSI
                    }
                    
                    // Skip if empty
                    if (epc.isEmpty()) {
                        return;
                    }
                    
                    // GPS check: warn but do NOT block scan
                    if (lastKnownLocation == null) {
                        android.util.Log.w("SCAN_CALLBACK", "No GPS location - accepting scan anyway (GPS optional)");
                    }
                    
                    // Start capture window ONLY if not already capturing
                    // Accumulates all reads in one 350ms window for best-candidate selection
                    if (!isCapturing) {
                        startCaptureWindow();
                    }
                    
                    // Add read to capture window
                    synchronized (captureWindow) {
                        if (!captureWindow.containsKey(epc)) {
                            captureWindow.put(epc, new ArrayList<>());
                        }
                        captureWindow.get(epc).add(new TagRead(epc, rssi, System.currentTimeMillis()));
                        android.util.Log.d("SCAN_CALLBACK", String.format("Read: EPC=%s RSSI=%d dBm", epc, rssi));
                    }
                }
            }
        });
    }
    
    private void initLocation() {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            
            // Check location permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 
                    LOCATION_PERMISSION_REQUEST);
                return;
            }
            
            // Create location callback
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        lastKnownLocation = location;
                        lastLocationAccuracy = location.getAccuracy();
                        android.util.Log.d("GPS", String.format("Location: %.6f, %.6f | Accuracy: ±%.1fm", 
                            location.getLatitude(), location.getLongitude(), lastLocationAccuracy));
                    }
                }
            };
            
            // Configure high-accuracy location request
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdateDelayMillis(5000)
                .build();
            
            // Start requesting location updates
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            
            // Try to get last known location immediately
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    lastKnownLocation = location;
                    lastLocationAccuracy = location.getAccuracy();
                    android.util.Log.d("GPS", "Initial location acquired: ±" + lastLocationAccuracy + "m");
                }
            }).addOnFailureListener(this, e -> {
                android.util.Log.e("GPS", "Failed to get location: " + e.getMessage());
            });
        } catch (SecurityException e) {
            android.util.Log.e("GPS", "Location permission error: " + e.getMessage());
        } catch (Exception e) {
            android.util.Log.e("GPS", "GPS initialization failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initLocation();
            }
        }
    }
    
    private void initSyncManager() {
        syncManager = new SyncManager(this);
        syncManager.setSyncListener(new SyncManager.SyncListener() {
            @Override
            public void onSyncStarted() {
                tvSyncStatus.setText("Syncing...");
                tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            }
            
            @Override
            public void onSyncSuccess(int lastPlacementNumber) {
                tvSyncStatus.setText("Synced");
                tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                
                // Update counter from server - server is authoritative
                // This handles cases where server rejects scans (duplicates, validation failures)
                placementCounter = lastPlacementNumber;
                updateCounterDisplay();
            }
            
            @Override
            public void onSyncFailed(String error) {
                tvSyncStatus.setText("Sync Failed");
                tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
            
            @Override
            public void onCounterUpdated(int unsyncedCount) {
                tvUnsyncedCount.setText("Unsynced: " + unsyncedCount);
                
                // Change color based on count
                if (unsyncedCount == 0) {
                    tvUnsyncedCount.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                } else if (unsyncedCount < 10) {
                    tvUnsyncedCount.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                } else {
                    tvUnsyncedCount.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                }
            }
        });
    }
    
    private void loadMasonData() {
        MasonApp app = MasonApp.getInstance();
        if (app == null) {
            finish();
            return;
        }
        
        masonId = app.getMasonId();
        if (masonId == null || masonId.isEmpty()) {
            finish();
            return;
        }
        
        // Display mason ID in UI
        if (tvMasonId != null && masonId != null) {
            tvMasonId.setText("Mason: " + masonId);
        }
    }
    
    private void fetchInitialCounter() {
        btnStart.setEnabled(false);
        btnStart.setBackgroundResource(R.drawable.button_bg_disabled);
        tvSyncStatus.setText("Loading...");
        
        syncManager.fetchLastPlacementNumber(masonId, new SyncManager.FetchListener() {
            @Override
            public void onFetchSuccess(int lastPlacementNumber) {
                placementCounter = lastPlacementNumber;
                updateCounterDisplay();
                tvSyncStatus.setText("Ready");
                tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                btnStart.setEnabled(true);
                btnStart.setBackgroundResource(R.drawable.button_bg_green);
            }
            
            @Override
            public void onFetchFailed(String error) {
                // Allow offline mode, start from 0
                placementCounter = 0;
                updateCounterDisplay();
                tvSyncStatus.setText("Offline");
                tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                btnStart.setEnabled(true);
                btnStart.setBackgroundResource(R.drawable.button_bg_green);
            }
        });
    }
    
    private void setupListeners() {
        btnStart.setOnClickListener(v -> startScanning());
        btnStop.setOnClickListener(v -> stopScanning());
        btnBack.setOnClickListener(v -> goBack());
    }
    
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_account) {
            openAccountSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void startScanning() {
        if (uhf.getConnectStatus() != ConnectionStatus.CONNECTED) {
            showConnectionAlert();
            return;
        }
        
        // Check GPS accuracy before allowing scan
        if (lastKnownLocation == null) {
            return;
        }
        
        if (lastLocationAccuracy > MAX_ACCEPTABLE_ACCURACY) {
            new AlertDialog.Builder(this)
                .setTitle("GPS Accuracy Poor")
                .setMessage(String.format("GPS accuracy is ±%.1fm. For reliable duplicate detection, accuracy should be better than ±%.0fm.\n\nMove to an open area and wait for better signal, or scan anyway?", 
                    lastLocationAccuracy, MAX_ACCEPTABLE_ACCURACY))
                .setPositiveButton("Scan Anyway", (dialog, which) -> proceedWithScanning())
                .setNegativeButton("Wait", null)
                .show();
            return;
        }
        
        proceedWithScanning();
    }
    
    private void proceedWithScanning() {
        isScanning = true;
        isPulsing = false;
        btnStart.setEnabled(false);
        btnStart.setBackgroundResource(R.drawable.button_bg_disabled);
        btnStop.setEnabled(true);
        btnStop.setBackgroundResource(R.drawable.button_bg_red);
        tvSyncStatus.setText("Scanning...");
        tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        
        // Initialize new build session
        currentBuildSessionId = UUID.randomUUID().toString();
        currentEventSeq = 0;
        android.util.Log.d("BUILD_SESSION", "Started new session: " + currentBuildSessionId);
        
        // Apply power level and track it
        currentScanPowerLevel = currentPowerLevel;
        boolean powerSet = uhf.setPower(currentPowerLevel);
        if (!powerSet) {
            android.util.Log.w("SCAN", "Failed to set power to " + currentPowerLevel + " dBm");
        } else {
            android.util.Log.d("SCAN", "Power level set to: " + currentScanPowerLevel + " dBm for this session");
        }
        
        // Start continuous inventory mode - reader will scan tags automatically
        android.util.Log.d("SCAN", "Starting continuous inventory scanning...");
        boolean started = uhf.startInventoryTag();
        android.util.Log.d("SCAN", "startInventoryTag() returned: " + started);
        if (started) {
            android.util.Log.d("SCAN", "✓ Continuous scanning active - tags will be read automatically");
        } else {
            android.util.Log.e("SCAN", "✗ Failed to start scanning - Check reader connection");
        }
        
        android.util.Log.d("SCAN", "Scanning session started - continuous inventory active");
    }
    
    private void stopScanning() {
        stopScanning(true); // Manual stop, clear admin data
    }
    
    private void stopScanning(boolean clearAdminData) {
        // Stop continuous inventory scanning
        uhf.stopInventory();
        android.util.Log.d("SCAN", "Continuous scanning stopped");
        
        isScanning = false;
        isPulsing = false;
        btnStart.setEnabled(true);
        btnStart.setBackgroundResource(R.drawable.button_bg_green);
        btnStop.setEnabled(false);
        btnStop.setBackgroundResource(R.drawable.button_bg_disabled);
        tvSyncStatus.setText("Stopped");
        tvSyncStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
        
        // Clear scanned tags AND placements for admin every session
        // Regular users: session persists to prevent duplicates
        if (isAdmin) {
            synchronized (scannedTagsInSession) {
                int tagCount = scannedTagsInSession.size();
                scannedTagsInSession.clear();
                android.util.Log.d("SCAN_DEBUG", "Admin: Cleared " + tagCount + " scanned tags");
            }
        } else {
            int tagCount = scannedTagsInSession.size();
            android.util.Log.d("SCAN_DEBUG", "User: Session preserved - " + tagCount + " bricks cannot be rescanned");
        }
        
        // In admin mode, clear unsynced placements when manually stopped
        if (isAdmin && clearAdminData) {
            syncManager.clearUnsyncedPlacements();
            placementCounter = 0;
            updateCounterDisplay();
            tvUnsyncedCount.setText("Unsynced: 0");
            tvUnsyncedCount.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }
    }
    
    // Start capture window for best-candidate selection
    private void startCaptureWindow() {
        isCapturing = true;
        captureStartTime = System.currentTimeMillis();
        
        mainHandler.post(() -> {
            tvSyncStatus.setText("Capturing...");
            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        });
        
        // Schedule window timeout using field-tunable parameter
        captureWindowTimeout = () -> processCaptureWindow();
        captureWindowHandler.postDelayed(captureWindowTimeout, captureWindowMs);
        
        android.util.Log.d("CAPTURE_WINDOW", "Started capture window (" + captureWindowMs + "ms)");
    }
    
    // Process capture window and select best candidate
    private void processCaptureWindow() {
        isCapturing = false;
        
        synchronized (captureWindow) {
            if (captureWindow.isEmpty()) {
                android.util.Log.d("CAPTURE_WINDOW", "Window empty - no tags in range");
                mainHandler.post(() -> {
                    tvSyncStatus.setText("Scanning...");
                    tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                });
                return;
            }
            
            // Calculate statistics for each EPC
            Map<String, CandidateStats> candidates = new HashMap<>();
            for (Map.Entry<String, List<TagRead>> entry : captureWindow.entrySet()) {
                String epc = entry.getKey();
                List<TagRead> reads = entry.getValue();
                
                int readCount = reads.size();
                int rssiSum = 0;
                int rssiMax = Integer.MIN_VALUE;
                
                for (TagRead read : reads) {
                    rssiSum += read.rssi;
                    if (read.rssi > rssiMax) {
                        rssiMax = read.rssi;
                    }
                }
                
                int rssiAvg = rssiSum / readCount;
                candidates.put(epc, new CandidateStats(epc, readCount, rssiAvg, rssiMax));
                
                android.util.Log.d("CAPTURE_WINDOW", String.format("Candidate: %s | Count=%d | AvgRSSI=%d | MaxRSSI=%d", 
                    epc, readCount, rssiAvg, rssiMax));
            }
            
            // Select best candidate
            CandidateStats winner = null;
            CandidateStats runnerUp = null;
            
            for (CandidateStats candidate : candidates.values()) {
                if (winner == null || candidate.isBetterThan(winner)) {
                    runnerUp = winner;
                    winner = candidate;
                } else if (runnerUp == null || candidate.isBetterThan(runnerUp)) {
                    runnerUp = candidate;
                }
            }
            
            // Check for ambiguity using field-tunable thresholds
            if (winner != null && runnerUp != null) {
                boolean isAmbiguous = winner.isAmbiguousWith(runnerUp, rssiAmbiguityThresholdDb, countAmbiguityThreshold);
                
                if (isAmbiguous) {
                    android.util.Log.w("CAPTURE_WINDOW", String.format("✗ AMBIGUOUS - Winner: %s (count=%d, rssi=%d) vs Runner-up: %s (count=%d, rssi=%d) | Thresholds: %ddB/%dcount", 
                        winner.epc, winner.count, winner.avgRssi, runnerUp.epc, runnerUp.count, runnerUp.avgRssi, rssiAmbiguityThresholdDb, countAmbiguityThreshold));
                    
                    mainHandler.post(() -> {
                        tvSyncStatus.setText("Ambiguous - Rescan");
                        tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                    });
                    
                    captureWindow.clear();
                    return;
                }
            }
            
            // Winner is clear
            if (winner != null) {
                android.util.Log.d("CAPTURE_WINDOW", String.format("✓ WINNER - EPC: %s | Count=%d | AvgRSSI=%d | MaxRSSI=%d", 
                    winner.epc, winner.count, winner.avgRssi, winner.peakRssi));
                
                // Check cooldown
                long currentTime = System.currentTimeMillis();
                synchronized (tagCooldowns) {
                    Long lastScanTime = tagCooldowns.get(winner.epc);
                    if (lastScanTime != null && (currentTime - lastScanTime < SCAN_COOLDOWN_MS)) {
                        android.util.Log.d("CAPTURE_WINDOW", "⏸ COOLDOWN - EPC: " + winner.epc);
                        mainHandler.post(() -> {
                            tvSyncStatus.setText("Cooldown - Ready");
                            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        });
                        captureWindow.clear();
                        return;
                    }
                    tagCooldowns.put(winner.epc, currentTime);
                }
                
                // Session duplicate check removed - GPS-based duplicate detection in onBrickScanned handles this
                // This allows scanning the same physical brick multiple times (different placements in wall)
                
                // Process winner
                final CandidateStats finalWinner = winner;
                onBrickScanned(finalWinner.epc, finalWinner.avgRssi, finalWinner.peakRssi, finalWinner.count);
            }
            
            captureWindow.clear();
        }
    }
    
    // Helper class for candidate statistics
    private static class CandidateStats {
        String epc;
        int count;
        int avgRssi;
        int peakRssi;
        
        CandidateStats(String epc, int count, int avgRssi, int peakRssi) {
            this.epc = epc;
            this.count = count;
            this.avgRssi = avgRssi;
            this.peakRssi = peakRssi;
        }
        
        boolean isBetterThan(CandidateStats other) {
            // Primary: higher count
            if (this.count != other.count) {
                return this.count > other.count;
            }
            // Tie-break: higher avg RSSI
            return this.avgRssi > other.avgRssi;
        }
        
        boolean isAmbiguousWith(CandidateStats other, int rssiThresholdDb, int countThreshold) {
            // Check if counts are within threshold
            int countDiff = Math.abs(this.count - other.count);
            if (countDiff <= countThreshold) {
                // Counts similar, check RSSI
                int rssiDiff = Math.abs(this.avgRssi - other.avgRssi);
                return rssiDiff <= rssiThresholdDb;
            }
            return false;
        }
    }
    
    
    private void onBrickScanned(String epc, int avgRssi, int peakRssi, int readCount) {
        // Capture the scan timestamp immediately
        final long scanTimestamp = System.currentTimeMillis();
        
        // Increment event sequence for this session
        currentEventSeq++;
        final int eventSeq = currentEventSeq;
        
        // Get current GPS location and accuracy (or sentinel values if missing)
        double latitude = 0.0;
        double longitude = 0.0;
        double altitude = 0.0;
        float accuracy = 999.0f; // Sentinel for missing GPS
        boolean gpsAvailable = false;
        String decisionStatus = "ACCEPTED";
        
        if (lastKnownLocation != null) {
            latitude = lastKnownLocation.getLatitude();
            longitude = lastKnownLocation.getLongitude();
            altitude = lastKnownLocation.getAltitude();
            accuracy = lastLocationAccuracy;
            gpsAvailable = true;
        } else {
            decisionStatus = "ACCEPTED_NO_GPS";
            android.util.Log.w("BRICK_SCANNED", "GPS missing - using sentinel values (0.0, 0.0, 999.0)");
        }
        
        final double finalLatitude = latitude;
        final double finalLongitude = longitude;
        final double finalAltitude = altitude;
        final float finalAccuracy = accuracy;
        final boolean finalGpsAvailable = gpsAvailable;
        final String finalDecisionStatus = decisionStatus;
        
        android.util.Log.d("BRICK_SCANNED", String.format("Session: %s | Seq: %d | EPC: %s | RSSI: %d/%d | Reads: %d | GPS: %s | Power: %d dBm", 
            currentBuildSessionId, eventSeq, epc, avgRssi, peakRssi, readCount, gpsAvailable ? "Yes" : "NO", currentScanPowerLevel));
        
        // Adjust duplicate distance threshold based on GPS accuracy
        // Use 2x the GPS accuracy or minimum 10 meters, whichever is larger
        double adjustedThreshold = Math.max(DUPLICATE_DISTANCE_THRESHOLD, accuracy * 2.0);
        
        // Check for duplicate placement (same RFID + within 5 minutes + same location)
        // Skip GPS-based duplicate check if GPS not available
        synchronized (recentPlacements) {
            PlacementRecord recent = recentPlacements.get(epc);
            if (recent != null && gpsAvailable) {
                long timeDiff = scanTimestamp - recent.timestamp;
                double distance = calculateDistance(recent.latitude, recent.longitude, latitude, longitude);
                
                if (timeDiff < DUPLICATE_TIME_THRESHOLD && distance < adjustedThreshold) {
                    // Duplicate detected - discard
                    android.util.Log.d("DUPLICATE_CHECK", String.format("✗ DUPLICATE REJECTED - EPC: %s | Time: %ds | Distance: %.1fm | Threshold: %.1fm", 
                        epc, timeDiff/1000, distance, adjustedThreshold));
                    return;
                }
            }
            
            // Not a duplicate - store this placement
            recentPlacements.put(epc, new PlacementRecord(scanTimestamp, latitude, longitude, altitude, accuracy));
            android.util.Log.d("DUPLICATE_CHECK", String.format("✓ NEW PLACEMENT - EPC: %s | GPS: %.6f, %.6f ±%.1fm", 
                epc, latitude, longitude, accuracy));
        }
        
        mainHandler.post(() -> {
            // Play sound
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            
            // Vibrate for haptic feedback (200ms)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(200);
                }
            }
            
            // Counter will be updated by server response after sync
            // Don't increment locally - server is authoritative
            
            // Format timestamp for display
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            String formattedTime = sdf.format(new java.util.Date(scanTimestamp));
            
            // Display last brick with full info in admin mode
            if (isAdmin) {
                // Show full EPC in admin mode with GPS accuracy and RSSI
                tvLastBrick.setText("RFID: " + epc + "\n[#" + eventSeq + " | RSSI: " + avgRssi + " dBm | Power: " + currentScanPowerLevel + "]");
                String gpsText = finalGpsAvailable ? 
                    String.format("%.6f, %.6f ±%.1fm", finalLatitude, finalLongitude, finalAccuracy) : 
                    "NO GPS";
                tvLastTimestamp.setText("Time: " + formattedTime + "\nGPS: " + gpsText + " | Reads: " + readCount);
                // Log to console for debugging
                android.util.Log.d("ADMIN_RFID", String.format("Scanned: %s | Seq: %d | Count: %d | Time: %d | RSSI: %d/%d | Reads: %d | GPS: %s | Power: %d dBm | Status: %s", 
                    epc, eventSeq, placementCounter, scanTimestamp, avgRssi, peakRssi, readCount, 
                    finalGpsAvailable ? String.format("%.6f, %.6f ±%.1fm", finalLatitude, finalLongitude, finalAccuracy) : "MISSING",
                    currentScanPowerLevel, finalDecisionStatus));
            } else {
                // Show last 4 chars for regular users
                String displayEpc = epc.substring(Math.max(0, epc.length() - 4));
                tvLastBrick.setText("Last Brick: ID..." + displayEpc + " (#" + eventSeq + ")");
                tvLastTimestamp.setText("Time: " + formattedTime);
            }
            
            // Save to local cache with session, sequence, RSSI data, and decision status
            // Ensure masonId is loaded - critical for backend sync
            if (masonId == null || masonId.isEmpty()) {
                masonId = MasonApp.getInstance().getMasonId();
                android.util.Log.e("SCAN_ERROR", "masonId was null during scan - reloaded: " + masonId);
            }
            
            BrickPlacement placement = new BrickPlacement(masonId, epc, scanTimestamp, finalLatitude, finalLongitude, finalAltitude, finalAccuracy,
                currentBuildSessionId, eventSeq, avgRssi, peakRssi, readCount, finalDecisionStatus);
            placement.setPowerLevel(currentScanPowerLevel); // Track power level used
            
            android.util.Log.d("PLACEMENT_DEBUG", String.format("Saving placement: masonId=%s, EPC=%s, session=%s, seq=%d", 
                masonId, epc, currentBuildSessionId, eventSeq));
            
            syncManager.addPlacement(placement);
            
            // Auto-sync happens automatically in addPlacement() when threshold is reached
            
            // Update status - continue scanning
            if (isScanning) {
                tvSyncStatus.setText("Scanning...");
                tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            }
        });
    }
    
    private void updateCounterDisplay() {
        tvPlacementCounter.setText(String.valueOf(placementCounter));
    }
    
    // Calculate distance between two GPS coordinates in meters
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371000; // meters
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }
    
    private void goBack() {
        // Go back to connection activity
        if (isScanning) {
            return;
        }
        finish();
    }
    
    private void showConnectionAlert() {
        new AlertDialog.Builder(this)
            .setTitle("RFID Reader Not Connected")
            .setMessage("Please ensure your RFID reader is connected before starting.")
            .setPositiveButton("OK", null)
            .show();
    }
    
    private void openAccountSettings() {
        Intent intent = new Intent(this, AccountActivity.class);
        startActivity(intent);
    }
    
    private void toggleBatteryTest() {
        if (!BatteryTestService.isRunning()) {
            // Start foreground service
            isBatteryLoggingEnabled = true;
            Intent serviceIntent = new Intent(this, BatteryTestService.class);
            serviceIntent.putExtra("masonId", masonId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Battery test running — works with screen off", Toast.LENGTH_LONG).show();
            updateBatteryStatus();
        } else {
            // Stop foreground service
            isBatteryLoggingEnabled = false;
            Intent serviceIntent = new Intent(this, BatteryTestService.class);
            stopService(serviceIntent);
            updateBatteryStatus();
            Toast.makeText(this, "Battery test stopped. Check BatteryLogs folder.", Toast.LENGTH_LONG).show();
        }
    }
    
    private Handler batteryHandler = new Handler(Looper.getMainLooper());
    private Runnable batteryRunnable = new Runnable() {
        @Override
        public void run() {
            updateBatteryStatus();
            batteryHandler.postDelayed(this, 30000); // Update every 30 seconds
        }
    };
    
    private void startBatteryMonitoring() {
        batteryHandler.postDelayed(batteryRunnable, 30000);
    }
    
    private void stopBatteryMonitoring() {
        batteryHandler.removeCallbacks(batteryRunnable);
    }
    
    private void updateBatteryStatus() {
        if (uhf != null && uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
            try {
                int battery = uhf.getBattery();
                String iconFile;
                
                // Select icon based on battery percentage
                if (battery > 75) {
                    iconFile = "battery_100.png";
                } else if (battery > 50) {
                    iconFile = "battery_75.png";
                } else if (battery > 25) {
                    iconFile = "battery_50.png";
                } else {
                    iconFile = "battery_25.png";
                }
                
                // Load icon from assets and draw percentage on it
                try {
                    AssetManager assetManager = getAssets();
                    Bitmap originalBitmap = BitmapFactory.decodeStream(assetManager.open("BatteryPercentages/" + iconFile));
                    
                    // Create mutable copy to draw on
                    Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(mutableBitmap);
                    
                    // Setup paint for text
                    Paint paint = new Paint();
                    paint.setColor(Color.parseColor("#2D3436"));
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    paint.setAntiAlias(true);
                    paint.setTextAlign(Paint.Align.CENTER);
                    
                    String percentText = battery + "%";
                    float maxWidth = mutableBitmap.getWidth() * 0.85f;
                    float maxHeight;
                    
                    if (isBatteryLoggingEnabled) {
                        // Reserve top 70% for percentage, bottom 30% for [LOG]
                        maxHeight = mutableBitmap.getHeight() * 0.55f;
                    } else {
                        maxHeight = mutableBitmap.getHeight() * 0.75f;
                    }
                    
                    // Auto-fit: find the largest text size that fits
                    float textSize = maxHeight; // start large
                    Rect bounds = new Rect();
                    while (textSize > 1) {
                        paint.setTextSize(textSize);
                        paint.getTextBounds(percentText, 0, percentText.length(), bounds);
                        if (bounds.width() <= maxWidth && bounds.height() <= maxHeight) {
                            break;
                        }
                        textSize -= 1f;
                    }
                    
                    // Draw percentage text centered
                    float x = mutableBitmap.getWidth() / 2f;
                    float y;
                    if (isBatteryLoggingEnabled) {
                        // Shift percentage text up to make room for [LOG]
                        y = (mutableBitmap.getHeight() * 0.45f) - ((paint.descent() + paint.ascent()) / 2f);
                    } else {
                        y = (mutableBitmap.getHeight() / 2f) - ((paint.descent() + paint.ascent()) / 2f);
                    }
                    canvas.drawText(percentText, x, y, paint);
                    
                    // Draw [LOG] indicator if logging
                    if (isBatteryLoggingEnabled) {
                        paint.setTextSize(mutableBitmap.getHeight() * 0.18f);
                        canvas.drawText("[LOG]", x, mutableBitmap.getHeight() * 0.88f, paint);
                    }
                    
                    ivBatteryStatus.setImageBitmap(mutableBitmap);
                } catch (IOException ioException) {
                    android.util.Log.e("BATTERY", "Failed to load battery icon: " + iconFile, ioException);
                }
                
            } catch (Exception e) {
                ivBatteryStatus.setImageDrawable(null);
            }
        } else {
            ivBatteryStatus.setImageDrawable(null);
        }
    }
    
    private void shareBatteryLog() {
        // Find the most recent log file
        File logDir = new File(getExternalFilesDir(null), "BatteryLogs");
        if (!logDir.exists() || logDir.listFiles() == null || logDir.listFiles().length == 0) {
            Toast.makeText(this, "No log files found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get the most recent file
        File[] files = logDir.listFiles();
        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) {
                latest = f;
            }
        }
        
        try {
            Uri fileUri = FileProvider.getUriForFile(this, 
                getApplicationContext().getPackageName() + ".provider", 
                latest);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "RFID Scanner Battery Test Log");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Share Battery Log"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to share log: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isScanning) {
            uhf.stopInventory();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        if (syncManager != null) {
            syncManager.shutdown();
        }
        // Stop battery monitoring
        stopBatteryMonitoring();
        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        // Stop location updates
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
