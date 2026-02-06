package com.mason.bricktracking;

import android.app.Application;
import android.content.SharedPreferences;

public class MasonApp extends Application {
    private static MasonApp instance;
    private SharedPreferences sharedPreferences;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        sharedPreferences = getSharedPreferences("MasonBrickTracking", MODE_PRIVATE);
    }
    
    public static MasonApp getInstance() {
        return instance;
    }
    
    public SharedPreferences getSharedPrefs() {
        return sharedPreferences;
    }
    
    public void saveMasonId(String masonId, boolean isAdmin) {
        sharedPreferences.edit()
                .putString("mason_id", masonId)
                .putBoolean("is_admin", isAdmin)
                .apply();
    }
    
    public String getMasonId() {
        return sharedPreferences.getString("mason_id", null);
    }

    public boolean isAdmin() {
        return sharedPreferences.getBoolean("is_admin", false);
    }
    
    // Device connection preferences
    public void saveLastDevice(String deviceAddress, String deviceName) {
        sharedPreferences.edit()
                .putString("last_device_address", deviceAddress)
                .putString("last_device_name", deviceName)
                .apply();
    }
    
    public String getLastDeviceAddress() {
        return sharedPreferences.getString("last_device_address", null);
    }
    
    public String getLastDeviceName() {
        return sharedPreferences.getString("last_device_name", null);
    }
    
    // Login credential management
    public void saveCredentials(String username, String password) {
        if (isSaveLoginEnabled()) {
            sharedPreferences.edit()
                    .putString("saved_username", username)
                    .putString("saved_password", password)
                    .apply();
        }
    }
    
    public String getSavedUsername() {
        return sharedPreferences.getString("saved_username", null);
    }
    
    public String getSavedPassword() {
        return sharedPreferences.getString("saved_password", null);
    }
    
    public void clearSavedCredentials() {
        sharedPreferences.edit()
                .remove("saved_username")
                .remove("saved_password")
                .apply();
    }
    
    public boolean hasSavedCredentials() {
        return getSavedUsername() != null && getSavedPassword() != null;
    }
    
    // Preference management
    public void setSaveLoginEnabled(boolean enabled) {
        sharedPreferences.edit()
                .putBoolean("save_login_enabled", enabled)
                .apply();
    }
    
    public boolean isSaveLoginEnabled() {
        return sharedPreferences.getBoolean("save_login_enabled", true);
    }
    
    public void setSaveDeviceEnabled(boolean enabled) {
        sharedPreferences.edit()
                .putBoolean("save_device_enabled", enabled)
                .apply();
        // If disabled, clear saved device
        if (!enabled) {
            clearLastDevice();
        }
    }
    
    public boolean isSaveDeviceEnabled() {
        return sharedPreferences.getBoolean("save_device_enabled", true); // Default true
    }
    
    // RFID Power Level (5-33 dBm, default 20)
    public int getRfidPowerLevel() {
        return sharedPreferences.getInt("rfid_power_level", 20);
    }
    
    public void setRfidPowerLevel(int powerLevel) {
        sharedPreferences.edit()
            .putInt("rfid_power_level", powerLevel)
            .apply();
    }
    
    public void clearLastDevice() {
        sharedPreferences.edit()
                .remove("last_device_address")
                .remove("last_device_name")
                .apply();
    }
    
    public void logout() {
        // Clear login data but keep device preferences if enabled
        sharedPreferences.edit()
                .remove("mason_id")
                .remove("is_admin")
                .apply();
        
        // Clear credentials if save login is disabled
        if (!isSaveLoginEnabled()) {
            clearSavedCredentials();
        }
    }
}
