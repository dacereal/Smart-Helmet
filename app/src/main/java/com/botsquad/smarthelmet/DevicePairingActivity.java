package com.botsquad.smarthelmet;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DevicePairingActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final int REQUEST_WIFI_PERMISSIONS = 3;
    private static final int DEFAULT_PROTOTYPE_PORT = 8080;
    private static final String PROTOTYPE_SSID_PREFIX = "SmartHelmet_";  // Make this configurable
    private static final String PROTOTYPE_DEFAULT_IP = "192.168.4.1";    // Common default IP for ESP32/ESP8266
    private static final int PROTOTYPE_DEFAULT_PORT = 8080;              // Make this configurable
    private static final String PROTOTYPE_BT_NAME_PREFIX = "SmartHelmet_";  // Your prototype's Bluetooth name prefix

    private BluetoothAdapter bluetoothAdapter;
    private WifiManager wifiManager;
    private ArrayAdapter<String> deviceListAdapter;
    private ArrayList<String> deviceList;
    private ArrayList<ScanResult> wifiScanResults;
    private ArrayList<BluetoothDevice> discoveredDevices;
    private Button btnScanBluetooth;
    private Button btnScanWifi;
    private ListView deviceListView;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(DevicePairingActivity.this, 
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    String deviceName = device.getName();
                    String deviceAddress = device.getAddress();
                    if (deviceName != null && !deviceList.contains(deviceName)) {
                        deviceList.add(deviceName + "\n" + deviceAddress);
                        discoveredDevices.add(device);
                        deviceListAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                if (ActivityCompat.checkSelfPermission(DevicePairingActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    List<ScanResult> results = wifiManager.getScanResults();
                    wifiScanResults.clear();
                    deviceList.clear();
                    
                    for (ScanResult result : results) {
                        wifiScanResults.add(result);
                        String ssid = result.SSID;
                        String capabilities = result.capabilities;
                        int signalStrength = result.level;
                        String security = capabilities.contains("WPA") ? "WPA" : 
                                       capabilities.contains("WEP") ? "WEP" : "Open";
                        
                        deviceList.add(String.format("%s\nSignal: %d dBm\nSecurity: %s", 
                            ssid, signalStrength, security));
                    }
                    deviceListAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_pairing);

        // Enable back button in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Device Pairing");
        }

        // Initialize UI elements
        btnScanBluetooth = findViewById(R.id.btnScanBluetooth);
        btnScanWifi = findViewById(R.id.btnScanWifi);
        deviceListView = findViewById(R.id.deviceListView);
        
        deviceList = new ArrayList<>();
        wifiScanResults = new ArrayList<>();
        discoveredDevices = new ArrayList<>();
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        deviceListView.setAdapter(deviceListAdapter);

        // Initialize Bluetooth
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        // Initialize WiFi
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // Set up click listeners
        btnScanBluetooth.setOnClickListener(v -> checkBluetoothPermissionsAndScan());
        btnScanWifi.setOnClickListener(v -> checkWifiPermissionsAndScan());

        // Set up list item click listener
        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < discoveredDevices.size()) {
                BluetoothDevice selectedDevice = discoveredDevices.get(position);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    == PackageManager.PERMISSION_GRANTED) {
                    // Check if this is our prototype device
                    if (selectedDevice.getName() != null && 
                        selectedDevice.getName().startsWith(PROTOTYPE_BT_NAME_PREFIX)) {
                        connectToPrototype(selectedDevice);
                    } else {
                        Toast.makeText(this, "Please select a Smart Helmet device", 
                            Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // Register for broadcasts
        IntentFilter bluetoothFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothReceiver, bluetoothFilter);

        IntentFilter wifiFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiReceiver, wifiFilter);
    }

    private void connectToPrototype(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Show connecting dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connecting to Prototype");
        builder.setMessage("Connecting to " + device.getName() + "...");
        AlertDialog dialog = builder.create();
        dialog.show();

        // Start connection in background
        new Thread(() -> {
            try {
                // Pass connection details to Dashboard
                Intent resultIntent = new Intent();
                resultIntent.putExtra("PROTOTYPE_BT_ADDRESS", device.getAddress());
                setResult(RESULT_OK, resultIntent);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Failed to connect: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void checkBluetoothPermissionsAndScan() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            return;
        }

        String[] permissions = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            startBluetoothScan();
        }
    }

    private void checkWifiPermissionsAndScan() {
        String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_WIFI_PERMISSIONS);
        } else {
            startWifiScan();
        }
    }

    private void startBluetoothScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        deviceList.clear();
        discoveredDevices.clear();
        deviceListAdapter.notifyDataSetChanged();

        // Get paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    == PackageManager.PERMISSION_GRANTED) {
                    String deviceName = device.getName();
                    String deviceAddress = device.getAddress();
                    if (deviceName != null) {
                        deviceList.add(deviceName + "\n" + deviceAddress + " (Paired)");
                        discoveredDevices.add(device);
                    }
                }
            }
            deviceListAdapter.notifyDataSetChanged();
        }

        // Start discovery
        bluetoothAdapter.startDiscovery();
        Toast.makeText(this, "Scanning for Bluetooth devices...", Toast.LENGTH_SHORT).show();
    }

    private void startWifiScan() {
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "Please enable WiFi", Toast.LENGTH_SHORT).show();
            return;
        }

        deviceList.clear();
        wifiScanResults.clear();
        deviceListAdapter.notifyDataSetChanged();
        
        wifiManager.startScan();
        Toast.makeText(this, "Scanning for WiFi networks...", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startBluetoothScan();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for scanning", 
                    Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_WIFI_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startWifiScan();
            } else {
                Toast.makeText(this, "WiFi permissions are required for scanning", 
                    Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                checkBluetoothPermissionsAndScan();
            } else {
                Toast.makeText(this, "Bluetooth must be enabled to scan for devices", 
                    Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // First unregister receivers to prevent any callbacks
            unregisterReceiver(bluetoothReceiver);
            unregisterReceiver(wifiReceiver);

            // Then handle Bluetooth cleanup if we have the necessary permissions
            if (bluetoothAdapter != null && 
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                == PackageManager.PERMISSION_GRANTED) {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            }
        } catch (Exception e) {
            // Log the error but don't crash
            android.util.Log.e("DevicePairing", "Error in onDestroy: " + e.getMessage());
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle back button click
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 