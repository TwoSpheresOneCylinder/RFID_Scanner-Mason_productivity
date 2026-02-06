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
    
    private TextView tvConnectionStatus, tvDeviceName;
    private Button btnSearchDevices, btnConnect, btnContinue;
    private ProgressBar progressBar;
    
    private RFIDWithUHFBLE uhf;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice selectedDevice;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);
        
        // Set custom action bar with user icon
        String masonId = MasonApp.getInstance().getMasonId();
        boolean isAdmin = MasonApp.getInstance().isAdmin();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowCustomEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            View customBar = getLayoutInflater().inflate(R.layout.custom_action_bar, null);
            ((TextView) customBar.findViewById(R.id.action_bar_title))
                .setText(masonId + (isAdmin ? " (Admin)" : ""));
            getSupportActionBar().setCustomView(customBar);
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
        btnSearchDevices = findViewById(R.id.btn_search_devices);
        btnConnect = findViewById(R.id.btn_connect);
        btnContinue = findViewById(R.id.btn_continue);
        progressBar = findViewById(R.id.progress_bar);
        
        btnConnect.setEnabled(false);
        btnContinue.setEnabled(false);
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
        btnConnect.setBackgroundResource(R.drawable.button_bg_red);
        btnConnect.setEnabled(true);
        btnContinue.setEnabled(true);
        btnSearchDevices.setEnabled(false);
        btnSearchDevices.setBackgroundResource(R.drawable.button_bg_disabled);
        
        // Apply saved power level (always full power by default)
        int powerLevel = MasonApp.getInstance().getRfidPowerLevel();
        boolean powerSet = uhf.setPower(powerLevel);
        if (powerSet) {
            android.util.Log.d("CONNECTION", "Power level set to " + powerLevel + " dBm");
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
        btnConnect.setBackgroundResource(R.drawable.button_bg_gray);
        btnConnect.setEnabled(selectedDevice != null);
        btnContinue.setEnabled(false);
        btnSearchDevices.setEnabled(true);
        btnSearchDevices.setBackgroundResource(R.drawable.button_bg_green);
    }
    
    private void updateConnectionStatus(String status, boolean connected) {
        tvConnectionStatus.setText(status);
        tvConnectionStatus.setTextColor(getResources().getColor(
            connected ? android.R.color.holo_green_dark : android.R.color.holo_red_dark
        ));
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
