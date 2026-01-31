package com.mason.bricktracking.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.mason.bricktracking.MasonApp;
import com.mason.bricktracking.R;
import com.mason.bricktracking.data.local.AppDatabase;
import com.mason.bricktracking.data.remote.ApiClient;
import com.mason.bricktracking.data.remote.ApiService;
import com.mason.bricktracking.data.remote.ResetResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AccountActivity extends AppCompatActivity {
    
    private TextView tvMasonId, tvUsername, tvDeviceName, tvDeviceAddress;
    private CheckBox cbSaveLogin, cbSaveDevice;
    private Button btnResetProfile, btnLogout, btnBack;
    private ApiService apiService;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        
        initViews();
        loadAccountData();
        setupListeners();
    }
    
    private void initViews() {
        tvMasonId = findViewById(R.id.tv_account_mason_id);
        tvUsername = findViewById(R.id.tv_account_username);
        tvDeviceName = findViewById(R.id.tv_account_device_name);
        tvDeviceAddress = findViewById(R.id.tv_account_device_address);
        cbSaveLogin = findViewById(R.id.cb_save_login);
        cbSaveDevice = findViewById(R.id.cb_save_device);
        btnResetProfile = findViewById(R.id.btn_reset_profile);
        btnLogout = findViewById(R.id.btn_account_logout);
        btnBack = findViewById(R.id.btn_account_back);
        
        apiService = ApiClient.getApiService();
    }
    
    private void loadAccountData() {
        MasonApp app = MasonApp.getInstance();
        
        // Load account info
        String masonId = app.getMasonId();
        String username = app.getSavedUsername();
        boolean isAdmin = app.isAdmin();
        
        tvMasonId.setText("Mason ID: " + (masonId != null ? masonId : "Not logged in") + 
            (isAdmin ? " (ADMIN)" : ""));
        tvUsername.setText("Username: " + (username != null ? username : "N/A"));
        
        // Load device info
        String deviceName = app.getLastDeviceName();
        String deviceAddress = app.getLastDeviceAddress();
        
        tvDeviceName.setText("Device: " + (deviceName != null ? deviceName : "Not connected"));
        tvDeviceAddress.setText("Address: " + (deviceAddress != null ? deviceAddress : "N/A"));
        
        // Load preferences
        cbSaveLogin.setChecked(app.isSaveLoginEnabled());
        cbSaveDevice.setChecked(app.isSaveDeviceEnabled());
    }
    
    private void setupListeners() {
        cbSaveLogin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MasonApp.getInstance().setSaveLoginEnabled(isChecked);
            if (!isChecked) {
                // Clear saved credentials if disabled
                MasonApp.getInstance().clearSavedCredentials();
            }
        });
        
        cbSaveDevice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MasonApp.getInstance().setSaveDeviceEnabled(isChecked);
            if (!isChecked) {
                // Clear saved device if disabled
                MasonApp.getInstance().clearLastDevice();
                loadAccountData(); // Refresh display
            }
        });
        
        btnResetProfile.setOnClickListener(v -> showResetConfirmation());
        btnLogout.setOnClickListener(v -> showLogoutConfirmation());
        btnBack.setOnClickListener(v -> finish());
    }
    
    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?\n\nMake sure all data is synced before logging out.")
            .setPositiveButton("Logout", (dialog, which) -> performLogout())
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void performLogout() {
        MasonApp.getInstance().logout();
        
        // Go back to login screen
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showResetConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("Reset Profile Data")
            .setMessage("WARNING: This will permanently delete ALL your brick placement data from both the app and server.\n\nYour brick placement counter will reset to 0.\n\nThis action cannot be undone!")
            .setPositiveButton("Reset All Data", (dialog, which) -> performReset())
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    private void performReset() {
        String masonId = MasonApp.getInstance().getMasonId();
        if (masonId == null) {
            return;
        }
        
        btnResetProfile.setEnabled(false);
        btnResetProfile.setText("Resetting...");
        
        // Call backend to delete all placements
        Call<ResetResponse> call = apiService.resetProfileData(masonId);
        call.enqueue(new Callback<ResetResponse>() {
            @Override
            public void onResponse(Call<ResetResponse> call, Response<ResetResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ResetResponse resetResponse = response.body();
                    
                    // Clear local Room database
                    new Thread(() -> {
                        AppDatabase.getInstance(AccountActivity.this)
                            .brickPlacementDao()
                            .deleteAll();
                        
                        runOnUiThread(() -> {
                            
                            btnResetProfile.setEnabled(true);
                            btnResetProfile.setText("RESET PROFILE DATA");
                            
                            // Go back to connection screen to restart
                            Intent intent = new Intent(AccountActivity.this, ConnectionActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }).start();
                } else {
                    runOnUiThread(() -> {
                        btnResetProfile.setEnabled(true);
                        btnResetProfile.setText("RESET PROFILE DATA");
                    });
                }
            }
            
            @Override
            public void onFailure(Call<ResetResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    btnResetProfile.setEnabled(true);
                    btnResetProfile.setText("RESET PROFILE DATA");
                });
            }
        });
    }

}
