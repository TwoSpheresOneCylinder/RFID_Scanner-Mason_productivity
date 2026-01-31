package com.mason.bricktracking.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.mason.bricktracking.MasonApp;
import com.mason.bricktracking.R;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.ConnectionStatusCallback;

public class ConnectionActivity extends AppCompatActivity {
    
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final int REQUEST_SELECT_DEVICE = 3;
    
    private TextView tvConnectionStatus, tvDeviceName, tvPowerLevel;
    private Button btnSearchDevices, btnConnect, btnContinue;
    private ProgressBar progressBar;
    private SeekBar seekBarPower;
    
    private RFIDWithUHFBLE uhf;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice selectedDevice;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);
        
        // Set username in action bar title
        String masonId = MasonApp.getInstance().getMasonId();
        boolean isAdmin = MasonApp.getInstance().isAdmin();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(masonId + (isAdmin ? " (Admin)" : ""));
        }
        
        initViews();
        initRFID();
        checkPermissions();
        setupListeners();
        
        // Load last device after RFID is initialized
        loadLastDevice();
    }
    
    private void initViews() {
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvDeviceName = findViewById(R.id.tv_device_name);
        tvPowerLevel = findViewById(R.id.tv_power_level);
        btnSearchDevices = findViewById(R.id.btn_search_devices);
        btnConnect = findViewById(R.id.btn_connect);
        btnContinue = findViewById(R.id.btn_continue);
        progressBar = findViewById(R.id.progress_bar);
        seekBarPower = findViewById(R.id.seekbar_power);
        
        btnConnect.setEnabled(false);
        btnContinue.setEnabled(false);
        
        // Load and display saved power level (convert dBm to feet: 5-33 dBm -> 1-6 ft)
        int powerLevel = MasonApp.getInstance().getRfidPowerLevel();
        int rangeFeet = convertPowerToFeet(powerLevel);
        seekBarPower.setProgress(rangeFeet - 1); // SeekBar 0-5 maps to range 1-6 feet
        updateRangeDisplay(rangeFeet);
    }
    
    private void initRFID() {
        uhf = RFIDWithUHFBLE.getInstance();
        uhf.init(getApplicationContext());
        
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        // Set connection status callback
        uhf.setConnectionStatusCallback(new ConnectionStatusCallback<Object>() {
            @Override
            public void getStatus(ConnectionStatus connectionStatus, Object device) {
                runOnUiThread(() -> {
                    switch (connectionStatus) {
                        case CONNECTED:
                            onDeviceConnected();
                            break;
                        case CONNECTING:
                            updateConnectionStatus("Connecting...", false);
                            break;
                        case DISCONNECTED:
                            onDeviceDisconnected();
                            break;
                    }
                });
            }
        });
    }
    
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            boolean allGranted = true;
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
            }
        } else {
            // Android 11 and below
            String[] permissions = {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            boolean allGranted = true;
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }
        
        // Check if Bluetooth is enabled
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }
    
    private void setupListeners() {
        btnSearchDevices.setOnClickListener(v -> searchForDevices());
        btnConnect.setOnClickListener(v -> {
            if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                disconnectDevice();
            } else {
                connectToDevice();
            }
        });
        btnContinue.setOnClickListener(v -> navigateToMain());
        
        // SeekBar listener for range adjustment (works in feet, stores as dBm)
        seekBarPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // SeekBar progress 0-5 maps to range 1-6 feet
                    int rangeFeet = progress + 1;
                    updateRangeDisplay(rangeFeet);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Save the new power level when user finishes adjusting
                int rangeFeet = seekBar.getProgress() + 1;
                int powerDbm = convertFeetToPower(rangeFeet);
                MasonApp.getInstance().setRfidPowerLevel(powerDbm);
                
                // Apply power level if connected
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    uhf.setPower(powerDbm);
                }
            }
        });
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
    
    private void loadLastDevice() {
        // Safety check - ensure uhf is initialized
        if (uhf == null) {
            return;
        }
        
        String lastAddress = MasonApp.getInstance().getLastDeviceAddress();
        String lastName = MasonApp.getInstance().getLastDeviceName();
        
        // Check if already connected
        if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
            onDeviceConnected();
            // Auto-navigate to main screen after 1 second
            new android.os.Handler().postDelayed(() -> {
                if (uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    navigateToMain();
                }
            }, 1000);
            return;
        }
        
        if (lastAddress != null && bluetoothAdapter != null) {
            try {
                selectedDevice = bluetoothAdapter.getRemoteDevice(lastAddress);
                tvDeviceName.setText("Device: " + (lastName != null ? lastName : lastAddress));
                btnConnect.setEnabled(true);
                
                // Auto-connect to last device
                connectToDevice();
            } catch (Exception e) {
                // Last device not available
            }
        }
    }
    
    private void disconnectDevice() {
        uhf.disconnect();
        onDeviceDisconnected();
    }
    
    private void searchForDevices() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_DEVICE);
    }
    
    private void connectToDevice() {
        if (selectedDevice == null) {
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        btnConnect.setEnabled(false);
        updateConnectionStatus("Connecting...", false);
        
        uhf.connect(selectedDevice.getAddress(), new ConnectionStatusCallback<Object>() {
            @Override
            public void getStatus(ConnectionStatus connectionStatus, Object device) {
                runOnUiThread(() -> {
                    switch (connectionStatus) {
                        case CONNECTED:
                            onDeviceConnected();
                            break;
                        case DISCONNECTED:
                            onDeviceDisconnected();
                            break;
                    }
                });
            }
        });
    }
    
    private void onDeviceConnected() {
        progressBar.setVisibility(View.GONE);
        updateConnectionStatus("Connected", true);
        btnConnect.setText("Disconnect");
        btnConnect.setEnabled(true);
        btnContinue.setEnabled(true);
        
        // Apply saved power level
        int powerLevel = MasonApp.getInstance().getRfidPowerLevel();
        int rangeFeet = convertPowerToFeet(powerLevel);
        boolean powerSet = uhf.setPower(powerLevel);
        if (powerSet) {
            android.util.Log.d("CONNECTION", "Power level set to " + powerLevel + " dBm (~" + rangeFeet + " feet)");
        } else {
            android.util.Log.e("CONNECTION", "Failed to set power level");
        }
        
        // Save device for auto-connect next time (if preference enabled)
        if (selectedDevice != null && MasonApp.getInstance().isSaveDeviceEnabled()) {
            MasonApp.getInstance().saveLastDevice(
                selectedDevice.getAddress(),
                selectedDevice.getName() != null ? selectedDevice.getName() : selectedDevice.getAddress()
            );
        }
    }
    
    private void onDeviceDisconnected() {
        progressBar.setVisibility(View.GONE);
        updateConnectionStatus("Disconnected", false);
        btnConnect.setText("Connect");
        btnConnect.setEnabled(selectedDevice != null);
        btnContinue.setEnabled(false);
    }
    
    private void updateConnectionStatus(String status, boolean connected) {
        tvConnectionStatus.setText(status);
        tvConnectionStatus.setTextColor(getResources().getColor(
            connected ? android.R.color.holo_green_dark : android.R.color.holo_red_dark
        ));
    }
    
    private void updateRangeDisplay(int rangeFeet) {
        tvPowerLevel.setText(String.format("Current: ~%d feet", rangeFeet));
    }
    
    // Convert power level (5-33 dBm) to range in feet (1-6 ft)
    // Linear mapping: 5dBm=1ft, 33dBm=6ft
    private int convertPowerToFeet(int powerDbm) {
        // Clamp to valid range
        powerDbm = Math.max(5, Math.min(33, powerDbm));
        // Linear interpolation: (powerDbm - 5) / 28 * 5 + 1
        return Math.round((powerDbm - 5) / 28.0f * 5.0f) + 1;
    }
    
    // Convert range in feet (1-6 ft) to power level (5-33 dBm)
    private int convertFeetToPower(int rangeFeet) {
        // Clamp to valid range
        rangeFeet = Math.max(1, Math.min(6, rangeFeet));
        // Linear interpolation: (rangeFeet - 1) / 5 * 28 + 5
        return Math.round((rangeFeet - 1) / 5.0f * 28.0f) + 5;
    }
    
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        // Don't finish() - keep ConnectionActivity in back stack so back button works
    }
    
    private void openAccountSettings() {
        Intent intent = new Intent(this, AccountActivity.class);
        startActivity(intent);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SELECT_DEVICE && resultCode == RESULT_OK && data != null) {
            String deviceAddress = data.getStringExtra("device_address");
            String deviceName = data.getStringExtra("device_name");
            
            if (deviceAddress != null) {
                selectedDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
                tvDeviceName.setText("Device: " + (deviceName != null ? deviceName : deviceAddress));
                btnConnect.setEnabled(true);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                // Permissions not granted
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't disconnect here, keep connection for MainActivity
    }
}
