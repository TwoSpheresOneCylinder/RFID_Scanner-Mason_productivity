package com.mason.bricktracking.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;

/**
 * Pre-scan validation to ensure scanner readiness before starting work.
 * Checks: Battery, BLE connection, GPS accuracy, Network status.
 */
public class PreScanValidator {
    private static final String TAG = "PreScanValidator";
    
    // Validation thresholds
    private static final int MIN_BATTERY_PERCENT = 20;
    private static final int LOW_BATTERY_WARNING = 30;
    private static final float MAX_GPS_ACCURACY_METERS = 15.0f;
    private static final float GOOD_GPS_ACCURACY_METERS = 8.0f;
    private static final int GPS_TIMEOUT_MS = 10000; // 10 seconds to get a good fix
    private static final int GPS_UPDATE_INTERVAL_MS = 1000; // Update every second
    
    private final Context context;
    private final RFIDWithUHFBLE uhf;
    private final NetworkMonitor networkMonitor;
    private final Handler mainHandler;
    private final FusedLocationProviderClient fusedLocationClient;
    
    private ValidationListener listener;
    private ValidationResult currentResult;
    private boolean isValidating = false;
    
    // GPS state
    private LocationCallback locationCallback;
    private Runnable gpsTimeoutRunnable;
    private Location bestLocation = null;
    private boolean gpsCheckComplete = false;
    
    public PreScanValidator(Context context, RFIDWithUHFBLE uhf, NetworkMonitor networkMonitor) {
        this.context = context;
        this.uhf = uhf;
        this.networkMonitor = networkMonitor;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }
    
    public void setValidationListener(ValidationListener listener) {
        this.listener = listener;
    }
    
    /**
     * Start the validation sequence
     */
    public void startValidation() {
        if (isValidating) {
            Log.w(TAG, "Validation already in progress");
            return;
        }
        
        isValidating = true;
        currentResult = new ValidationResult();
        bestLocation = null;
        gpsCheckComplete = false;
        
        Log.d(TAG, "Starting pre-scan validation sequence");
        
        // Run checks in sequence, updating UI after each
        runValidationSequence();
    }
    
    private void runValidationSequence() {
        // Step 1: Check battery
        notifyProgress("Checking battery...", 1, 4);
        checkBattery();
        
        // Step 2: Check BLE connection (slight delay for UI)
        mainHandler.postDelayed(() -> {
            notifyProgress("Checking scanner connection...", 2, 4);
            checkBleConnection();
            
            // Step 3: Check GPS (requests fresh location)
            mainHandler.postDelayed(() -> {
                notifyProgress("Getting GPS fix...", 3, 4);
                startGpsCheck();
            }, 300);
            
        }, 300);
    }
    
    private void checkBattery() {
        try {
            int battery = uhf.getBattery();
            currentResult.batteryLevel = battery;
            
            if (battery < MIN_BATTERY_PERCENT) {
                currentResult.batteryStatus = CheckStatus.FAILED;
                currentResult.batteryMessage = "Battery too low (" + battery + "%)";
            } else if (battery < LOW_BATTERY_WARNING) {
                currentResult.batteryStatus = CheckStatus.WARNING;
                currentResult.batteryMessage = "Low battery (" + battery + "%)";
            } else {
                currentResult.batteryStatus = CheckStatus.PASSED;
                currentResult.batteryMessage = battery + "%";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking battery", e);
            currentResult.batteryStatus = CheckStatus.WARNING;
            currentResult.batteryMessage = "Unable to read";
        }
        
        notifyCheckComplete("battery", currentResult.batteryStatus, currentResult.batteryMessage);
    }
    
    private void checkBleConnection() {
        try {
            ConnectionStatus status = uhf.getConnectStatus();
            
            if (status == ConnectionStatus.CONNECTED) {
                currentResult.bleStatus = CheckStatus.PASSED;
                currentResult.bleMessage = "Connected";
            } else {
                currentResult.bleStatus = CheckStatus.FAILED;
                currentResult.bleMessage = "Not connected";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking BLE", e);
            currentResult.bleStatus = CheckStatus.FAILED;
            currentResult.bleMessage = "Connection error";
        }
        
        notifyCheckComplete("ble", currentResult.bleStatus, currentResult.bleMessage);
    }
    
    private void startGpsCheck() {
        // Check permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            currentResult.gpsStatus = CheckStatus.FAILED;
            currentResult.gpsMessage = "No GPS permission";
            notifyCheckComplete("gps", currentResult.gpsStatus, currentResult.gpsMessage);
            finishValidation();
            return;
        }
        
        // Show initial "acquiring" state
        currentResult.gpsStatus = CheckStatus.PENDING;
        currentResult.gpsMessage = "Acquiring...";
        notifyCheckComplete("gps", currentResult.gpsStatus, currentResult.gpsMessage);
        
        // Set up timeout - finish with best available after timeout
        gpsTimeoutRunnable = () -> {
            if (!gpsCheckComplete) {
                gpsCheckComplete = true;
                stopGpsUpdates();
                evaluateFinalGpsResult();
                finishValidation();
            }
        };
        mainHandler.postDelayed(gpsTimeoutRunnable, GPS_TIMEOUT_MS);
        
        // Create location request for high accuracy
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdates(20) // Get up to 20 updates in 10 seconds
                .build();
        
        // Set up location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (gpsCheckComplete) return;
                
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    // Keep track of best (most accurate) location
                    if (bestLocation == null || location.getAccuracy() < bestLocation.getAccuracy()) {
                        bestLocation = location;
                        
                        // Update UI with current accuracy
                        updateGpsStatus(location);
                        
                        // If we got a good fix, finish early
                        if (location.getAccuracy() <= GOOD_GPS_ACCURACY_METERS) {
                            gpsCheckComplete = true;
                            mainHandler.removeCallbacks(gpsTimeoutRunnable);
                            stopGpsUpdates();
                            finishValidation();
                        }
                    }
                }
            }
        };
        
        // Start requesting location updates
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Started GPS location updates");
        } catch (Exception e) {
            Log.e(TAG, "Error starting GPS updates", e);
            currentResult.gpsStatus = CheckStatus.FAILED;
            currentResult.gpsMessage = "GPS error";
            notifyCheckComplete("gps", currentResult.gpsStatus, currentResult.gpsMessage);
            mainHandler.removeCallbacks(gpsTimeoutRunnable);
            finishValidation();
        }
    }
    
    private void updateGpsStatus(Location location) {
        float accuracy = location.getAccuracy();
        currentResult.gpsAccuracy = accuracy;
        
        if (accuracy <= GOOD_GPS_ACCURACY_METERS) {
            currentResult.gpsStatus = CheckStatus.PASSED;
            currentResult.gpsMessage = String.format("%.1fm", accuracy);
        } else if (accuracy <= MAX_GPS_ACCURACY_METERS) {
            currentResult.gpsStatus = CheckStatus.WARNING;
            currentResult.gpsMessage = String.format("%.1fm (improving...)", accuracy);
        } else {
            currentResult.gpsStatus = CheckStatus.WARNING;
            currentResult.gpsMessage = String.format("%.0fm (acquiring...)", accuracy);
        }
        
        notifyCheckComplete("gps", currentResult.gpsStatus, currentResult.gpsMessage);
    }
    
    private void evaluateFinalGpsResult() {
        if (bestLocation == null) {
            currentResult.gpsStatus = CheckStatus.FAILED;
            currentResult.gpsAccuracy = 0;
            currentResult.gpsMessage = "No GPS signal";
        } else {
            float accuracy = bestLocation.getAccuracy();
            currentResult.gpsAccuracy = accuracy;
            
            if (accuracy <= GOOD_GPS_ACCURACY_METERS) {
                currentResult.gpsStatus = CheckStatus.PASSED;
                currentResult.gpsMessage = String.format("%.1fm", accuracy);
            } else if (accuracy <= MAX_GPS_ACCURACY_METERS) {
                currentResult.gpsStatus = CheckStatus.WARNING;
                currentResult.gpsMessage = String.format("%.1fm (acceptable)", accuracy);
            } else {
                currentResult.gpsStatus = CheckStatus.FAILED;
                currentResult.gpsMessage = String.format("%.0fm (poor signal)", accuracy);
            }
        }
        
        notifyCheckComplete("gps", currentResult.gpsStatus, currentResult.gpsMessage);
    }
    
    private void stopGpsUpdates() {
        if (locationCallback != null) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                Log.d(TAG, "Stopped GPS location updates");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping GPS updates", e);
            }
        }
    }
    
    private void finishValidation() {
        // Step 4: Check network (non-blocking)
        notifyProgress("Checking network...", 4, 4);
        checkNetwork();
        
        // Calculate overall result
        currentResult.calculateOverall();
        
        isValidating = false;
        
        mainHandler.postDelayed(() -> {
            if (listener != null) {
                listener.onValidationComplete(currentResult);
            }
        }, 300);
    }
    
    private void checkNetwork() {
        boolean connected = networkMonitor.checkCurrentConnectivity();
        
        if (connected) {
            currentResult.networkStatus = CheckStatus.PASSED;
            currentResult.networkMessage = "Connected";
        } else {
            currentResult.networkStatus = CheckStatus.WARNING;
            currentResult.networkMessage = "Offline (will sync later)";
        }
        
        notifyCheckComplete("network", currentResult.networkStatus, currentResult.networkMessage);
    }
    
    private void notifyProgress(String message, int step, int totalSteps) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onValidationProgress(message, step, totalSteps);
            }
        });
    }
    
    private void notifyCheckComplete(String checkName, CheckStatus status, String message) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onCheckComplete(checkName, status, message);
            }
        });
    }
    
    public void cancelValidation() {
        if (isValidating) {
            isValidating = false;
            gpsCheckComplete = true;
            
            // Stop GPS updates
            stopGpsUpdates();
            
            // Remove timeout callback
            if (gpsTimeoutRunnable != null) {
                mainHandler.removeCallbacks(gpsTimeoutRunnable);
            }
        }
    }
    
    // ========== Data Classes ==========
    
    public enum CheckStatus {
        PENDING,
        PASSED,
        WARNING,
        FAILED
    }
    
    public static class ValidationResult {
        // Battery
        public CheckStatus batteryStatus = CheckStatus.PENDING;
        public int batteryLevel = 0;
        public String batteryMessage = "";
        
        // BLE Connection
        public CheckStatus bleStatus = CheckStatus.PENDING;
        public String bleMessage = "";
        
        // GPS
        public CheckStatus gpsStatus = CheckStatus.PENDING;
        public float gpsAccuracy = 0;
        public String gpsMessage = "";
        
        // Network
        public CheckStatus networkStatus = CheckStatus.PENDING;
        public String networkMessage = "";
        
        // Overall
        public CheckStatus overallStatus = CheckStatus.PENDING;
        public boolean canProceed = false;
        public String overallMessage = "";
        
        public void calculateOverall() {
            // Can proceed if no FAILED checks (except network)
            boolean hasFailed = batteryStatus == CheckStatus.FAILED ||
                               bleStatus == CheckStatus.FAILED ||
                               gpsStatus == CheckStatus.FAILED;
            
            boolean hasWarning = batteryStatus == CheckStatus.WARNING ||
                                gpsStatus == CheckStatus.WARNING ||
                                networkStatus == CheckStatus.WARNING;
            
            if (hasFailed) {
                overallStatus = CheckStatus.FAILED;
                canProceed = false;
                overallMessage = "Please resolve issues before scanning";
            } else if (hasWarning) {
                overallStatus = CheckStatus.WARNING;
                canProceed = true;
                overallMessage = "Ready with warnings";
            } else {
                overallStatus = CheckStatus.PASSED;
                canProceed = true;
                overallMessage = "All checks passed";
            }
        }
    }
    
    public interface ValidationListener {
        void onValidationProgress(String message, int step, int totalSteps);
        void onCheckComplete(String checkName, CheckStatus status, String message);
        void onValidationComplete(ValidationResult result);
    }
}
