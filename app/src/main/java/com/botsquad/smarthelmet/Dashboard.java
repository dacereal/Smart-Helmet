package com.botsquad.smarthelmet;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
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

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.net.Socket;
import java.io.InputStream;


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
    private static final int CAMERA_FRAME_SIZE = 320 * 240 * 2; // Assuming 320x240 RGB565 format
    private byte[] frameBuffer = new byte[CAMERA_FRAME_SIZE];
    private android.graphics.Canvas canvas;
    private android.graphics.Paint paint;
    private android.graphics.Bitmap frameBitmap;

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private static final int REQUEST_DEVICE_PAIRING = 1001;
    private static String SERVER_URL = "http://192.168.56.1:8080"; // Change this to your laptop's IP address
    private ExecutorService networkExecutor;
    private boolean isStreaming = false;
    private Thread streamThread;
    private HttpURLConnection connection;
    private static final int BUFFER_SIZE = 8192;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_dashboard);

            mAuth = FirebaseAuth.getInstance();
            mDatabase = FirebaseDatabase.getInstance().getReference();
            networkExecutor = Executors.newSingleThreadExecutor();
            
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
            
            // Initialize frame bitmap and canvas
            frameBitmap = android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.RGB_565);
            canvas = new android.graphics.Canvas();
            paint = new android.graphics.Paint();
            
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
        canvas = holder.lockCanvas();
        if (canvas != null) {
            canvas.drawColor(android.graphics.Color.BLACK);
            holder.unlockCanvasAndPost(canvas);
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // Surface size or format has changed
        canvas = holder.lockCanvas();
        if (canvas != null) {
            canvas.drawColor(android.graphics.Color.BLACK);
            holder.unlockCanvasAndPost(canvas);
        }
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
        } else if (id == R.id.menu_alert_tones) {
            // Navigate to Alert Tones Activity
            Intent intent = new Intent(Dashboard.this, AlertTonesActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_change_password) {
            // Navigate to Change Password Activity
            Intent intent = new Intent(Dashboard.this, ChangePasswordActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_connect_server) {
            // Show dialog to get server IP address
            showServerConnectionDialog();
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
            int frameIndex = 0;
            boolean receivingFrame = false;
            
            while (isConnected) {
                try {
                    int bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        // Check if this is a JSON message or camera frame
                        if (buffer[0] == '{') {  // JSON message starts with {
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
                        } else {  // This is a camera frame
                            // Copy the received bytes to the frame buffer
                            System.arraycopy(buffer, 0, frameBuffer, frameIndex, bytes);
                            frameIndex += bytes;
                            
                            // If we've received a complete frame
                            if (frameIndex >= CAMERA_FRAME_SIZE) {
                                // Convert the frame buffer to a bitmap
                                frameBitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(frameBuffer));
                                
                                // Draw the frame on the surface
                                runOnUiThread(() -> {
                                    if (surfaceHolder != null) {
                                        android.graphics.Canvas canvas = surfaceHolder.lockCanvas();
                                        if (canvas != null) {
                                            // Calculate scaling to fit the preview area
                                            float scale = Math.min(
                                                (float)canvas.getWidth() / frameBitmap.getWidth(),
                                                (float)canvas.getHeight() / frameBitmap.getHeight()
                                            );
                                            
                                            // Calculate centering offsets
                                            float dx = (canvas.getWidth() - frameBitmap.getWidth() * scale) / 2;
                                            float dy = (canvas.getHeight() - frameBitmap.getHeight() * scale) / 2;
                                            
                                            // Clear the canvas
                                            canvas.drawColor(android.graphics.Color.BLACK);
                                            
                                            // Save canvas state, scale and translate, then draw
                                            canvas.save();
                                            canvas.translate(dx, dy);
                                            canvas.scale(scale, scale);
                                            canvas.drawBitmap(frameBitmap, 0, 0, paint);
                                            canvas.restore();
                                            
                                            surfaceHolder.unlockCanvasAndPost(canvas);
                                        }
                                    }
                                });
                                
                                // Reset frame buffer index
                                frameIndex = 0;
                            }
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

    private void connectToServer(String ipAddress, int port) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connecting to Server");
        builder.setMessage("Connecting to laptop server...");
        AlertDialog dialog = builder.create();
        dialog.show();

        networkExecutor.execute(() -> {
            try {
                Socket socket = new Socket(ipAddress, port);
                isStreaming = true;
                InputStream inputStream = socket.getInputStream();
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(Dashboard.this, "Connected to server", Toast.LENGTH_SHORT).show();
                });
                startStreaming(inputStream);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(Dashboard.this, "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }


    private void startStreaming(InputStream inputStream) {
        streamThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            StringBuilder jsonBuilder = new StringBuilder();
            boolean inJson = false;

            try {
                while (isStreaming) {
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead <= 0) break;

                    // Process the received data
                    for (int i = 0; i < bytesRead; i++) {
                        if (buffer[i] == '{') {
                            inJson = true;
                            jsonBuilder.setLength(0);
                        }

                        if (inJson) {
                            jsonBuilder.append((char) buffer[i]);
                            if (buffer[i] == '}') {
                                // Process complete JSON message
                                processJsonMessage(jsonBuilder.toString());
                                inJson = false;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (isStreaming) {
                    runOnUiThread(() -> {
                        Toast.makeText(Dashboard.this, "Stream error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                    isStreaming = false;
                }
            } finally {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        streamThread.start();
    }


    private void processJsonMessage(String jsonStr) {
        try {
            JSONObject data = new JSONObject(jsonStr);
            boolean isEyesClosed = data.getBoolean("is_eyes_closed");
            long timestamp = data.optLong("timestamp", System.currentTimeMillis());

            runOnUiThread(() -> {
                if (isEyesClosed) {
                    drowsinessStatusIcon.setImageResource(android.R.drawable.presence_busy);
                    drowsinessStatusIcon.setColorFilter(getResources().getColor(android.R.color.holo_red_light));
                    drowsinessStatusText.setText("Driver Status: Drowsy");
                    updateDrowsinessEvent(true, timestamp);
                } else {
                    drowsinessStatusIcon.setImageResource(android.R.drawable.presence_online);
                    drowsinessStatusIcon.setColorFilter(getResources().getColor(android.R.color.holo_green_light));
                    drowsinessStatusText.setText("Driver Status: Alert");
                    updateDrowsinessEvent(false, timestamp);
                }
            });

            String frameBase64 = data.optString("frame", null);
            if (frameBase64 != null) {
                byte[] imageBytes = Base64.decode(frameBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (bitmap != null) {
                    runOnUiThread(() -> {
                        if (surfaceHolder != null) {
                            Canvas canvas = surfaceHolder.lockCanvas();
                            if (canvas != null) {
                                float scale = Math.min(
                                        (float) canvas.getWidth() / bitmap.getWidth(),
                                        (float) canvas.getHeight() / bitmap.getHeight()
                                );
                                float dx = (canvas.getWidth() - bitmap.getWidth() * scale) / 2;
                                float dy = (canvas.getHeight() - bitmap.getHeight() * scale) / 2;

                                ((Canvas) canvas).drawColor(Color.BLACK);
                                canvas.save();
                                canvas.translate(dx, dy);
                                canvas.scale(scale, scale);
                                canvas.drawBitmap(bitmap, 0, 0, null);
                                canvas.restore();

                                surfaceHolder.unlockCanvasAndPost(canvas);
                            }
                            bitmap.recycle();
                        }
                    });
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void processImageData(byte[] imageData) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            if (bitmap != null) {
                runOnUiThread(() -> {
                    if (surfaceHolder != null) {
                        android.graphics.Canvas canvas = surfaceHolder.lockCanvas();
                        if (canvas != null) {
                            // Calculate scaling to fit the preview area
                            float scale = Math.min(
                                (float)canvas.getWidth() / bitmap.getWidth(),
                                (float)canvas.getHeight() / bitmap.getHeight()
                            );
                            
                            // Calculate centering offsets
                            float dx = (canvas.getWidth() - bitmap.getWidth() * scale) / 2;
                            float dy = (canvas.getHeight() - bitmap.getHeight() * scale) / 2;
                            
                            // Clear the canvas
                            canvas.drawColor(android.graphics.Color.BLACK);
                            
                            // Save canvas state, scale and translate, then draw
                            canvas.save();
                            canvas.translate(dx, dy);
                            canvas.scale(scale, scale);
                            canvas.drawBitmap(bitmap, 0, 0, null);
                            canvas.restore();
                            
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                        bitmap.recycle();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showServerConnectionDialog() {
        // Inflate the dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_prototype_connection, null);
        TextInputEditText ipAddressInput = dialogView.findViewById(R.id.ipAddressInput);
        TextInputEditText portInput = dialogView.findViewById(R.id.portInput);

        // Set default values
        ipAddressInput.setText("192.168.56.1");  // Default IP
        portInput.setText("12345");  // Default port

        // Create and show the dialog
        new AlertDialog.Builder(this)
            .setTitle("Connect to Laptop Server")
            .setView(dialogView)
                .setPositiveButton("Connect", (dialog, which) -> {
                    String ipAddress = ipAddressInput.getText().toString();
                    String portStr = portInput.getText().toString();
                    if (!ipAddress.isEmpty() && !portStr.isEmpty()) {
                        int port = Integer.parseInt(portStr);
                        connectToServer(ipAddress, port);  // Call socket version
                    } else {
                        Toast.makeText(this, "Please enter both IP address and port", Toast.LENGTH_SHORT).show();
                    }


            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isStreaming = false;
        if (streamThread != null) {
            streamThread.interrupt();
        }
        if (connection != null) {
            connection.disconnect();
        }
        if (networkExecutor != null) {
            networkExecutor.shutdown();
        }
    }
} 