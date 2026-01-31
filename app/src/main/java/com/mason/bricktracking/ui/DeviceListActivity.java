package com.mason.bricktracking.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.mason.bricktracking.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceListActivity extends AppCompatActivity {
    
    private ListView lvDevices;
    private ProgressBar progressBar;
    private ArrayAdapter<String> deviceAdapter;
    private List<BluetoothDevice> deviceList;
    private BluetoothAdapter bluetoothAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        
        setTitle("Select RFID Reader");
        
        lvDevices = findViewById(R.id.lv_devices);
        progressBar = findViewById(R.id.progress_bar);
        
        deviceList = new ArrayList<>();
        deviceAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setTextColor(getResources().getColor(android.R.color.black));
                textView.setTextSize(16);
                textView.setPadding(32, 32, 32, 32);
                return view;
            }
        };
        lvDevices.setAdapter(deviceAdapter);
        
        lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = deviceList.get(position);
                returnSelectedDevice(device);
            }
        });
        
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        loadPairedDevices();
    }
    
    private void loadPairedDevices() {
        if (bluetoothAdapter == null) {
            finish();
            return;
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }
        
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();
                
                // Filter for RFID readers (optional: add specific name filters)
                if (deviceName != null && (deviceName.contains("UHF") || deviceName.contains("RFID") || deviceName.contains("RC"))) {
                    deviceList.add(device);
                    deviceAdapter.add(deviceName + "\n" + deviceAddress);
                }
            }
            
            if (deviceList.isEmpty()) {
                // If no RFID-specific devices, show all paired devices
                for (BluetoothDevice device : pairedDevices) {
                    deviceList.add(device);
                    String deviceName = device.getName();
                    String deviceAddress = device.getAddress();
                    deviceAdapter.add((deviceName != null ? deviceName : "Unknown") + "\n" + deviceAddress);
                }
            }
        }
        
        progressBar.setVisibility(View.GONE);
    }
    
    private void returnSelectedDevice(BluetoothDevice device) {
        Intent returnIntent = new Intent();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            returnIntent.putExtra("device_address", device.getAddress());
            returnIntent.putExtra("device_name", device.getName());
        }
        setResult(RESULT_OK, returnIntent);
        finish();
    }
}
