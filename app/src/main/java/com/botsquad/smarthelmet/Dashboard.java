package com.botsquad.smarthelmet;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

public class Dashboard extends AppCompatActivity implements SurfaceHolder.Callback {
    private ImageView drowsinessStatusIcon;
    private TextView drowsinessStatusText;
    private ImageView alarmStatusIcon;
    private TextView alarmStatusText;
    private TextView drowsinessEventsCount;
    private TextView lastEventTime;
    private SurfaceView cameraPreview;
    private SurfaceHolder surfaceHolder;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private static final UUID SMART_HELMET_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Standard SerialPortService ID
    private boolean isConnected = false;
    private Thread bluetoothThread;

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private static final int REQUEST_DEVICE_PAIRING = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_dashboard);

            mAuth = FirebaseAuth.getInstance();
            mDatabase = FirebaseDatabase.getInstance().getReference();
            
            // Initialize views first
            initializeViews();
            
            // Then setup Firebase listeners
            setupFirebaseListeners();
            
            // Finally setup camera preview
            setupCameraPreview();
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing dashboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupCameraPreview() {
        try {
            cameraPreview = new SurfaceView(this);
            surfaceHolder = cameraPreview.getHolder();
            surfaceHolder.addCallback(this);
            
            // Find the CardView and its container
            androidx.cardview.widget.CardView cardView = findViewById(R.id.cameraPreviewCard);
            if (cardView == null) {
                android.util.Log.e("Dashboard", "Camera preview card not found in layout");
                return;
            }

            // Get the first child of the CardView (which should be the FrameLayout)
            View container = cardView.getChildAt(0);
            if (container == null) {
                android.util.Log.e("Dashboard", "No container found in camera preview card");
                return;
            }

            if (container instanceof FrameLayout) {
                ((FrameLayout) container).addView(cameraPreview);
                android.util.Log.d("Dashboard", "Successfully added camera preview to FrameLayout");
            } else {
                android.util.Log.w("Dashboard", "Container is not a FrameLayout, creating new one");
                // If the container is not a FrameLayout, create one
                FrameLayout frameLayout = new FrameLayout(this);
                frameLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ));
                frameLayout.addView(cameraPreview);
                cardView.removeAllViews();
                cardView.addView(frameLayout);
                android.util.Log.d("Dashboard", "Created new FrameLayout and added camera preview");
            }
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error setting up camera preview: " + e.getMessage(), e);
            // Don't crash the app, just show an error message
            Toast.makeText(this, "Error setting up camera preview", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // Surface is created, ready to receive camera feed
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // Surface size or format has changed
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        // Surface is being destroyed
        if (isConnected) {
            isConnected = false;
            if (bluetoothThread != null) {
                bluetoothThread.interrupt();
            }
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
                if (bluetoothSocket != null) bluetoothSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dashboard_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_edit_profile) {
            // Navigate to Edit Profile Activity
            Intent intent = new Intent(Dashboard.this, ProfileActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_manage_pairing) {
            // Navigate to Device Pairing Activity
            Intent intent = new Intent(Dashboard.this, DevicePairingActivity.class);
            startActivityForResult(intent, REQUEST_DEVICE_PAIRING);
            return true;
        } else if (id == R.id.menu_change_password) {
            // Navigate to Change Password Activity
            Intent intent = new Intent(Dashboard.this, ChangePasswordActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_logout) {
            // Handle Logout
            mAuth.signOut();
            Intent intent = new Intent(Dashboard.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        drowsinessStatusIcon = findViewById(R.id.drowsinessStatusIcon);
        drowsinessStatusText = findViewById(R.id.drowsinessStatusText);
        alarmStatusIcon = findViewById(R.id.alarmStatusIcon);
        alarmStatusText = findViewById(R.id.alarmStatusText);
        drowsinessEventsCount = findViewById(R.id.drowsinessEventsCount);
        lastEventTime = findViewById(R.id.lastEventTime);
    }

    private void setupFirebaseListeners() {
        mDatabase.child("helmet_status").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    boolean isDrowsy = snapshot.child("is_drowsy").getValue(Boolean.class);
                    boolean isAlarmActive = snapshot.child("alarm_active").getValue(Boolean.class);
                    int eventsCount = snapshot.child("events_count").getValue(Integer.class);
                    String lastEvent = snapshot.child("last_event").getValue(String.class);

                    updateUI(isDrowsy, isAlarmActive, eventsCount, lastEvent);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Dashboard.this, "Failed to get status updates", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI(boolean isDrowsy, boolean isAlarmActive, int eventsCount, String lastEvent) {
        // Update drowsiness status
        if (isDrowsy) {
            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_busy);
            drowsinessStatusIcon.setColorFilter(getResources().getColor(android.R.color.holo_red_light));
            drowsinessStatusText.setText("Driver Status: Drowsy");
        } else {
            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_online);
            drowsinessStatusIcon.setColorFilter(getResources().getColor(android.R.color.holo_green_light));
            drowsinessStatusText.setText("Driver Status: Alert");
        }

        // Update alarm status
        if (isAlarmActive) {
            alarmStatusIcon.setImageResource(android.R.drawable.ic_lock_silent_mode);
            alarmStatusIcon.setColorFilter(getResources().getColor(android.R.color.holo_red_light));
            alarmStatusText.setText("Alarm Status: Active");
        } else {
            alarmStatusIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            alarmStatusIcon.setColorFilter(getResources().getColor(android.R.color.holo_green_light));
            alarmStatusText.setText("Alarm Status: Inactive");
        }

        // Update statistics
        drowsinessEventsCount.setText(String.valueOf(eventsCount));
        lastEventTime.setText(lastEvent != null ? lastEvent : "Never");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_PAIRING && resultCode == RESULT_OK && data != null) {
            String deviceAddress = data.getStringExtra("PROTOTYPE_BT_ADDRESS");
            if (deviceAddress != null) {
                connectToPrototype(deviceAddress);
            }
        }
    }

    private void connectToPrototype(String deviceAddress) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        
        // Show connecting dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connecting to Prototype");
        builder.setMessage("Please wait...");
        AlertDialog dialog = builder.create();
        dialog.show();

        // Connect in background thread
        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SMART_HELMET_UUID);
                bluetoothSocket.connect();
                
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
                
                isConnected = true;
                
                // Start listening for messages
                startBluetoothListener();
                
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(Dashboard.this, "Connected to prototype", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(Dashboard.this, "Connection failed: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
                try {
                    if (bluetoothSocket != null) {
                        bluetoothSocket.close();
                    }
                } catch (Exception closeException) {
                    closeException.printStackTrace();
                }
            }
        }).start();
    }

    private void startBluetoothListener() {
        bluetoothThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (isConnected) {
                try {
                    int bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String message = new String(buffer, 0, bytes);
                        try {
                            // Parse the JSON message from prototype
                            JSONObject data = new JSONObject(message);
                            boolean isEyesClosed = data.getBoolean("is_eyes_closed");
                            long timestamp = data.optLong("timestamp", System.currentTimeMillis());
                            
                            // Update UI on main thread
                            runOnUiThread(() -> {
                                // Update drowsiness status
                                if (isEyesClosed) {
                                    drowsinessStatusIcon.setImageResource(android.R.drawable.presence_busy);
                                    drowsinessStatusIcon.setColorFilter(getResources().getColor(android.R.color.holo_red_light));
                                    drowsinessStatusText.setText("Driver Status: Drowsy");
                                    
                                    // Update Firebase with drowsiness event
                                    updateDrowsinessEvent(true, timestamp);
                                } else {
                                    drowsinessStatusIcon.setImageResource(android.R.drawable.presence_online);
                                    drowsinessStatusIcon.setColorFilter(getResources().getColor(android.R.color.holo_green_light));
                                    drowsinessStatusText.setText("Driver Status: Alert");
                                    
                                    // Update Firebase with alert status
                                    updateDrowsinessEvent(false, timestamp);
                                }
                            });
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    if (isConnected) {
                        runOnUiThread(() -> {
                            Toast.makeText(Dashboard.this, "Connection lost: " + e.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                        });
                        isConnected = false;
                    }
                    break;
                }
            }
        });
        bluetoothThread.start();
    }

    private void updateDrowsinessEvent(boolean isDrowsy, long timestamp) {
        DatabaseReference statusRef = mDatabase.child("helmet_status");
        statusRef.child("is_drowsy").setValue(isDrowsy);
        statusRef.child("last_event").setValue(timestamp);
        
        // Get current count and increment
        statusRef.child("events_count").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                int currentCount = 0;
                if (snapshot.exists()) {
                    currentCount = snapshot.getValue(Integer.class);
                }
                // Only increment if drowsy
                if (isDrowsy) {
                    statusRef.child("events_count").setValue(currentCount + 1);
                }
            }
        });
    }
} 