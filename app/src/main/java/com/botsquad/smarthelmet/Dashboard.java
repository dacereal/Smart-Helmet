package com.botsquad.smarthelmet;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.io.ByteArrayOutputStream;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.net.Socket;
import java.util.Map;


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
    private DatabaseReference userHelmetStatusRef;
    private String currentUserId;
    private PiDrowsinessDetector piDrowsinessDetector;
    private boolean isPiConnected = false;  // Track if Pi is connected
    private ExecutorService inferenceExecutor;
    
    // Configuration: Force Raspberry Pi only - no local fallback
    private static final boolean FORCE_PI_ONLY = true;  // Must use Raspberry Pi
    private static final String PREF_PI_SERVER_URL = "pi_server_url";
    private static final String DEFAULT_PI_SERVER_URL = "http://192.168.43.151:5000";
    private static final int PI_CONNECTION_RETRY_ATTEMPTS = 5;  // Retry 5 times
    private static final int PI_CONNECTION_RETRY_DELAY_MS = 2000;  // 2 seconds between retries
    private boolean usePiDetection = false;  // Will be set when Pi is available
    private String piServerUrl = DEFAULT_PI_SERVER_URL;
    
    // Pi Camera mode - Pi uses its own camera, Android app queries for results AND receives video stream
    private Handler piQueryHandler;
    private Runnable piQueryRunnable;
    private static final long PI_QUERY_INTERVAL_MS = 200; // Query Pi every 200ms (~5 FPS)
    private Thread piStreamThread;  // Thread for processing MJPEG stream from Pi
    private boolean isStreaming = false;  // Track if stream is active
    
    // Frame processing variables
    private int frameCount = 0; // Track frames for ML processing optimization
    
    // Alert system variables
    private SharedPreferences sharedPreferences;
    private MediaPlayer alertMediaPlayer;
    private Handler drowsinessTimerHandler;
    private Runnable drowsinessTimerRunnable;
    private boolean isDrowsinessTimerActive = false;
    private long drowsinessStartTime = 0;
    private static final long DROWSINESS_THRESHOLD_MS = 1500; // 1.5 seconds
    private long lastAlarmTime = 0;
    private static final long ALARM_COOLDOWN_MS = 200; // 200ms between alarms for maximum responsiveness
    private boolean isAlarmSounding = false;
    
    // Debouncing variables to prevent rapid UI blinking
    private boolean lastDrowsyState = false;
    private long lastStateChangeTime = 0;
    private static final long DEBOUNCE_DELAY_MS = 300; // Wait 300ms before confirming state change
    private Handler debounceHandler;
    private Runnable debounceRunnable;
    
    // Track if we've already logged this alarm event (to prevent multiple counts)
    private boolean hasLoggedCurrentAlarm = false;

    // Using Raspberry Pi 5 with Raspberry Pi Camera for drowsiness detection
    private ExecutorService networkExecutor;

    // Vibration
    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null ? vm.getDefaultVibrator() : null;
        } else {
            return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private void startVibrationForTone(String toneName) {
        try {
            Vibrator vibrator = getVibrator();
            if (vibrator == null || !vibrator.hasVibrator()) return;

            // Patterns tuned to tone names; all looped to match MediaPlayer looping
            long[] timings;
            int[] amplitudes;
            int repeatIndex = 0;

            switch (toneName) {
                case "Pulse Alert":
                    // 400ms on, 200ms off repeating
                    timings = new long[]{0, 400, 200};
                    amplitudes = new int[]{0, VibrationEffect.DEFAULT_AMPLITUDE, 0};
                    break;
                case "Continuous Beep":
                    // Stronger continuous vibration with brief refresh gaps
                    timings = new long[]{0, 800, 50};
                    amplitudes = new int[]{0, VibrationEffect.DEFAULT_AMPLITUDE, 0};
                    break;
                case "Emergency Siren":
                    // Ramp effect: short pulses
                    timings = new long[]{0, 300, 150, 300, 150};
                    amplitudes = new int[]{0, 255, 0, 200, 0};
                    break;
                default:
                    // Default: 500ms on, 500ms off
                    timings = new long[]{0, 500, 500};
                    amplitudes = new int[]{0, VibrationEffect.DEFAULT_AMPLITUDE, 0};
                    break;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(timings, amplitudes, repeatIndex);
                vibrator.vibrate(effect);
            } else {
                // Legacy: timings only; first value is delay
                vibrator.vibrate(timings, repeatIndex);
            }
        } catch (Exception ignore) {
            // Avoid crashing the alert flow if vibration fails
        }
    }

    private void stopVibration() {
        try {
            Vibrator vibrator = getVibrator();
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception ignore) {
        }
    }

    private Uri selectToneUriDistinct(Context context, String toneName) {
        try {
            switch (toneName) {
                case "Default Beep":
                    return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
                case "High Pitch Alert":
                    return pickRingtone(context, RingtoneManager.TYPE_ALARM, 0);
                case "Low Pitch Warning":
                    return pickRingtone(context, RingtoneManager.TYPE_ALARM, 1);
                case "Pulse Alert":
                    return pickRingtone(context, RingtoneManager.TYPE_NOTIFICATION, 1);
                case "Emergency Siren":
                    return pickRingtone(context, RingtoneManager.TYPE_RINGTONE, 0);
                default:
                    return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
            }
        } catch (Exception e) {
            return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
        }
    }

    private Uri pickRingtone(Context context, int type, int index) {
        RingtoneManager rm = new RingtoneManager(this);
        rm.setType(type);
        android.database.Cursor cursor = rm.getCursor();
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int count = 0;
                    do {
                        if (count == index) {
                            return rm.getRingtoneUri(cursor.getPosition());
                        }
                        count++;
                    } while (cursor.moveToNext());
                    cursor.moveToFirst();
                    return rm.getRingtoneUri(cursor.getPosition());
                }
            } finally {
                cursor.close();
            }
        }
        if (type == RingtoneManager.TYPE_ALARM) return android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
        if (type == RingtoneManager.TYPE_RINGTONE) return android.provider.Settings.System.DEFAULT_RINGTONE_URI;
        return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            android.util.Log.d("Dashboard", "onCreate started");
            
            setContentView(R.layout.activity_dashboard);
            android.util.Log.d("Dashboard", "Layout set successfully");

            // Initialize basic components on main thread (lightweight operations only)
            mAuth = FirebaseAuth.getInstance();
            mDatabase = FirebaseDatabase.getInstance().getReference();
            FirebaseUser firebaseUser = mAuth.getCurrentUser();
            if (firebaseUser != null) {
                currentUserId = firebaseUser.getUid();
            } else {
                currentUserId = null;
                android.util.Log.w("Dashboard", "No authenticated user found while initializing Dashboard");
            }
            sharedPreferences = getSharedPreferences("SmartHelmetPrefs", Context.MODE_PRIVATE);
            setPiServerUrl(sharedPreferences.getString(PREF_PI_SERVER_URL, DEFAULT_PI_SERVER_URL), false, false);
            android.util.Log.d("Dashboard", "Loaded Pi server URL: " + piServerUrl);
            drowsinessTimerHandler = new Handler(Looper.getMainLooper());
            android.util.Log.d("Dashboard", "Basic components initialized");
            
            // Initialize views immediately (must be on main thread)
            android.util.Log.d("Dashboard", "Initializing views...");
            initializeViews();
            android.util.Log.d("Dashboard", "Views initialized successfully");
            
            // Setup camera preview immediately (lightweight setup)
            android.util.Log.d("Dashboard", "Setting up camera preview...");
            setupCameraPreview();
            android.util.Log.d("Dashboard", "Camera preview setup complete");
            
            // Show initial waiting state
            showPrototypeWaitingState();
            
            // Move all heavy operations to background threads
            android.util.Log.d("Dashboard", "Starting background initialization...");
            initializeBackgroundComponents();
            
            android.util.Log.d("Dashboard", "onCreate completed successfully");

            if (savedInstanceState == null) {
                showPiServerConfigurationDialog();
            }
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing dashboard: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeBackgroundComponents() {
        // Create executors with limited threads to prevent resource exhaustion
        networkExecutor = Executors.newFixedThreadPool(2);
        inferenceExecutor = Executors.newFixedThreadPool(1);
        
        startPiConnectionTask();
        
        // Setup Firebase and prototype detection in background
            networkExecutor.execute(() -> {
            try {
                android.util.Log.d("Dashboard", "Setting up Firebase and prototype detection...");
                initializeFirebaseData();
                setupPrototypeDetection();
                android.util.Log.d("Dashboard", "Firebase and prototype detection setup complete");
            } catch (Exception e) {
                android.util.Log.e("Dashboard", "Error in background Firebase setup: " + e.getMessage(), e);
            }
        });
        
        // Raspberry Pi monitoring is handled through the Pi query loop
        // which is started when the surface is created (see startPiQueryLoop)
    }

    private void startPiConnectionTask() {
        final String targetUrl = piServerUrl;

        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            android.util.Log.w("Dashboard", "Pi server URL is empty. Skipping connection attempt.");
            return;
        }

        if (inferenceExecutor == null || inferenceExecutor.isShutdown()) {
            inferenceExecutor = Executors.newFixedThreadPool(1);
        }

        inferenceExecutor.execute(() -> {
            android.util.Log.d("Dashboard", "Attempting to connect to Raspberry Pi at " + targetUrl + " (Pi-only mode)...");

            int attempts = 0;
            boolean connected = false;

            while (attempts < PI_CONNECTION_RETRY_ATTEMPTS && !connected) {
                if (!targetUrl.equals(piServerUrl)) {
                    android.util.Log.d("Dashboard", "Pi server URL changed while connecting. Aborting attempt for " + targetUrl);
                    return;
                }

                attempts++;
                android.util.Log.d("Dashboard", "Pi connection attempt " + attempts + "/" + PI_CONNECTION_RETRY_ATTEMPTS);

                PiDrowsinessDetector candidateDetector = null;

                try {
                    candidateDetector = new PiDrowsinessDetector(targetUrl);

                    final boolean[] piAvailable = {false};
                    final Object lock = new Object();

                    PiDrowsinessDetector finalCandidateDetector = candidateDetector;
                    candidateDetector.checkHealthAsync((isHealthy, response) -> {
                        synchronized (lock) {
                            piAvailable[0] = isHealthy;
                            lock.notify();
                        }

                        if (!isHealthy && response != null) {
                            android.util.Log.w("Dashboard", "Pi health check response for " + targetUrl + ": " + response);
                        }
                    });

                    synchronized (lock) {
                        try {
                            lock.wait(3000);
                        } catch (InterruptedException e) {
                            android.util.Log.w("Dashboard", "Pi detection interrupted");
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    if (!targetUrl.equals(piServerUrl)) {
                        android.util.Log.d("Dashboard", "Pi server URL changed after health check. Aborting attempt for " + targetUrl);
                        if (candidateDetector != null) {
                            candidateDetector.close();
                        }
                        return;
                    }

                    if (piAvailable[0]) {
                        connected = true;
                        piDrowsinessDetector = candidateDetector;
                        usePiDetection = true;
                        isPiConnected = true;
                        android.util.Log.d("Dashboard", "✅ Pi server connected successfully at " + targetUrl + "!");
                        runOnUiThread(() -> {
                            Toast.makeText(Dashboard.this, "✅ Connected to Raspberry Pi\n" + targetUrl, Toast.LENGTH_SHORT).show();

                            if (surfaceHolder != null && surfaceHolder.getSurface() != null && surfaceHolder.getSurface().isValid()) {
                                startPiQueryLoop();
                            }
                        });
                    } else {
                        if (candidateDetector != null) {
                            candidateDetector.close();
                        }

                        android.util.Log.w("Dashboard", "Pi not available on attempt " + attempts + " for URL " + targetUrl);

                        if (attempts < PI_CONNECTION_RETRY_ATTEMPTS) {
                            try {
                                Thread.sleep(PI_CONNECTION_RETRY_DELAY_MS);
                            } catch (InterruptedException e) {
                                android.util.Log.w("Dashboard", "Retry wait interrupted");
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("Dashboard", "Error connecting to Pi (attempt " + attempts + ", URL " + targetUrl + "): " + e.getMessage(), e);

                    if (candidateDetector != null) {
                        candidateDetector.close();
                    }

                    if (attempts < PI_CONNECTION_RETRY_ATTEMPTS) {
                        try {
                            Thread.sleep(PI_CONNECTION_RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            android.util.Log.w("Dashboard", "Retry wait interrupted");
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }

            if (!connected && targetUrl.equals(piServerUrl)) {
                android.util.Log.e("Dashboard", "❌ Failed to connect to Raspberry Pi after " + PI_CONNECTION_RETRY_ATTEMPTS + " attempts (" + targetUrl + ")");
                runOnUiThread(() -> {
                    Toast.makeText(Dashboard.this,
                        "❌ Cannot connect to Raspberry Pi\nPlease check:\n• Pi server is running\n• Same Wi-Fi network\n• Pi URL: " + targetUrl,
                        Toast.LENGTH_LONG).show();
                    drawPiErrorOnSurface("Unable to connect to Raspberry Pi\n" + targetUrl);
                });

                usePiDetection = false;
                isPiConnected = false;
                piDrowsinessDetector = null;
            }
        });
    }

    private void restartPiConnection() {
        android.util.Log.i("Dashboard", "Restarting Pi connection using URL: " + piServerUrl);

        stopPiQueryLoop();
        stopPiStream();

        if (piDrowsinessDetector != null) {
            try {
                piDrowsinessDetector.close();
            } catch (Exception e) {
                android.util.Log.w("Dashboard", "Error while closing Pi detector: " + e.getMessage());
            }
            piDrowsinessDetector = null;
        }

        usePiDetection = false;
        isPiConnected = false;

        startPiConnectionTask();
    }

    private String normalizePiServerUrl(String rawUrl) {
        if (rawUrl == null) {
            return DEFAULT_PI_SERVER_URL;
        }

        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_PI_SERVER_URL;
        }

        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }

        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }

    private void setPiServerUrl(String newUrl, boolean persist, boolean restart) {
        String normalizedUrl = normalizePiServerUrl(newUrl);

        if (normalizedUrl == null || normalizedUrl.isEmpty()) {
            normalizedUrl = DEFAULT_PI_SERVER_URL;
        }

        if (normalizedUrl.equals(piServerUrl) && !restart) {
            return;
        }

        piServerUrl = normalizedUrl;
        android.util.Log.i("Dashboard", "Pi server URL set to: " + piServerUrl + " (persist=" + persist + ", restart=" + restart + ")");

        if (persist && sharedPreferences != null) {
            sharedPreferences.edit().putString(PREF_PI_SERVER_URL, piServerUrl).apply();
        }

        if (restart) {
            restartPiConnection();
        }
    }

    private void showPiServerConfigurationDialog() {
        if (isFinishing()) {
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pi_server_url, null);
        TextInputLayout urlLayout = dialogView.findViewById(R.id.piServerUrlLayout);
        TextInputEditText urlInput = dialogView.findViewById(R.id.piServerUrlInput);

        if (urlInput != null && piServerUrl != null) {
            urlInput.setText(piServerUrl);
            urlInput.setSelection(piServerUrl.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Raspberry Pi Server")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Use Default", (d, which) -> {
                setPiServerUrl(DEFAULT_PI_SERVER_URL, true, true);
                Toast.makeText(Dashboard.this, "Using default Pi server URL", Toast.LENGTH_SHORT).show();
            })
            .create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                if (urlInput == null) {
                    dialog.dismiss();
                    return;
                }

                String inputValue = urlInput.getText() != null ? urlInput.getText().toString() : "";

                if (urlLayout != null) {
                    urlLayout.setError(null);
                }

                if (inputValue.trim().isEmpty()) {
                    if (urlLayout != null) {
                        urlLayout.setError("Please enter a server address");
                    }
                    return;
                }

                setPiServerUrl(inputValue, true, true);
                Toast.makeText(Dashboard.this, "Saved Pi server URL", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void setupCameraPreview() {
        try {
            android.util.Log.d("Dashboard", "setupCameraPreview: Starting...");
            cameraPreview = new SurfaceView(this);
            surfaceHolder = cameraPreview.getHolder();
            android.util.Log.d("Dashboard", "setupCameraPreview: SurfaceHolder obtained");
            surfaceHolder.addCallback(this);
            android.util.Log.d("Dashboard", "setupCameraPreview: Callback added");
            
            // Pi Camera mode - Pi uses its own camera
            // Android app will query Pi for detection results periodically
            piQueryHandler = new Handler(Looper.getMainLooper());
            
            // Initialize frame bitmap and canvas
            frameBitmap = android.graphics.Bitmap.createBitmap(640, 480, android.graphics.Bitmap.Config.RGB_565);
            canvas = new android.graphics.Canvas();
            paint = new android.graphics.Paint();
            
            // Set camera preview properties
            cameraPreview.setZOrderOnTop(false);
            cameraPreview.setZOrderMediaOverlay(false);
            
            // Find the CardView and its container
            androidx.cardview.widget.CardView cardView = findViewById(R.id.cameraPreviewCard);
            if (cardView == null) {
                android.util.Log.e("Dashboard", "Camera preview card not found in layout");
                return;
            }
            android.util.Log.d("Dashboard", "setupCameraPreview: Camera preview card found");

            // Get the first child of the CardView (which should be the FrameLayout)
            View container = cardView.getChildAt(0);
            if (container == null) {
                android.util.Log.e("Dashboard", "No container found in camera preview card");
                return;
            }

            if (container instanceof FrameLayout) {
                // Clear any existing views
                ((FrameLayout) container).removeAllViews();
                // Add camera preview with proper layout params
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                cameraPreview.setLayoutParams(params);
                ((FrameLayout) container).addView(cameraPreview);
                android.util.Log.d("Dashboard", "Successfully added camera preview to FrameLayout");
                // Make sure SurfaceView is visible
                cameraPreview.setVisibility(View.VISIBLE);
                android.util.Log.d("Dashboard", "SurfaceView visibility set to VISIBLE");
                // Force layout to trigger surface creation - try multiple times
                cameraPreview.post(() -> {
                    android.util.Log.d("Dashboard", "Requesting layout for SurfaceView");
                    cameraPreview.requestLayout();
                    
                    // Wait a bit for layout to complete, then check surface
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (surfaceHolder != null) {
                            try {
                                Surface surface = surfaceHolder.getSurface();
                                if (surface != null && surface.isValid()) {
                                    android.util.Log.d("Dashboard", "Surface is valid after layout, manually triggering surfaceCreated");
                                    surfaceCreated(surfaceHolder);
                                } else {
                                    android.util.Log.w("Dashboard", "Surface not valid yet, waiting more...");
                                    // Try again after another delay
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        if (surfaceHolder != null && surfaceHolder.getSurface() != null && surfaceHolder.getSurface().isValid()) {
                                            android.util.Log.d("Dashboard", "Surface valid on retry, triggering surfaceCreated");
                                            surfaceCreated(surfaceHolder);
                                        } else {
                                            android.util.Log.e("Dashboard", "Surface still not valid after retry");
                                        }
                                    }, 500);
                                }
                            } catch (Exception e) {
                                android.util.Log.e("Dashboard", "Error checking surface: " + e.getMessage());
                            }
                        }
                    }, 200);
                });
            } else {
                android.util.Log.w("Dashboard", "Container is not a FrameLayout, creating new one");
                // If the container is not a FrameLayout, create one
                FrameLayout frameLayout = new FrameLayout(this);
                frameLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ));
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                cameraPreview.setLayoutParams(params);
                frameLayout.addView(cameraPreview);
                cardView.removeAllViews();
                cardView.addView(frameLayout);
                android.util.Log.d("Dashboard", "Created new FrameLayout and added camera preview");
                // Make sure SurfaceView is visible
                cameraPreview.setVisibility(View.VISIBLE);
                android.util.Log.d("Dashboard", "SurfaceView visibility set to VISIBLE");
                // Force layout to trigger surface creation - try multiple times
                cameraPreview.post(() -> {
                    android.util.Log.d("Dashboard", "Requesting layout for SurfaceView");
                    cameraPreview.requestLayout();
                    
                    // Wait a bit for layout to complete, then check surface
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (surfaceHolder != null) {
                            try {
                                Surface surface = surfaceHolder.getSurface();
                                if (surface != null && surface.isValid()) {
                                    android.util.Log.d("Dashboard", "Surface is valid after layout, manually triggering surfaceCreated");
                                    surfaceCreated(surfaceHolder);
                                } else {
                                    android.util.Log.w("Dashboard", "Surface not valid yet, waiting more...");
                                    // Try again after another delay
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        if (surfaceHolder != null && surfaceHolder.getSurface() != null && surfaceHolder.getSurface().isValid()) {
                                            android.util.Log.d("Dashboard", "Surface valid on retry, triggering surfaceCreated");
                                            surfaceCreated(surfaceHolder);
                                        } else {
                                            android.util.Log.e("Dashboard", "Surface still not valid after retry");
                                        }
                                    }, 500);
                                }
                            } catch (Exception e) {
                                android.util.Log.e("Dashboard", "Error checking surface: " + e.getMessage());
                            }
                        }
                    }, 200);
                });
            }
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error setting up camera preview: " + e.getMessage(), e);
            // Don't crash the app, just show an error message
            Toast.makeText(this, "Error setting up camera preview", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // Surface is created
        android.util.Log.d("Dashboard", "Surface created - holder: " + holder + ", surface: " + holder.getSurface());
        
        // Wait a bit for Pi connection to complete, then start query loop
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (usePiDetection && piDrowsinessDetector != null && isPiConnected) {
                // Start Pi query loop if using Pi
                android.util.Log.d("Dashboard", "Starting Pi query loop");
                startPiQueryLoop();
            } else {
                android.util.Log.w("Dashboard", "Pi not connected yet, waiting...");
                // Retry after delay if Pi is still connecting
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (usePiDetection && piDrowsinessDetector != null && isPiConnected) {
                        android.util.Log.d("Dashboard", "Pi connected, starting query loop");
                        startPiQueryLoop();
                    } else {
                        android.util.Log.w("Dashboard", "Pi still not connected - will retry when connection is established");
                        // Surface is ready, but Pi connection is still in progress
                        // The query loop will start automatically when Pi connects
                    }
                }, 1000);
            }
        }, 500);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // Surface size or format has changed
        android.util.Log.d("Dashboard", "Surface changed: " + width + "x" + height);
    }
    
    /**
     * Start querying Pi for detection results (Pi Camera mode)
     * The Pi uses its own camera, so we just query for results periodically
     */
    private void startPiQueryLoop() {
        if (piQueryHandler == null) {
            android.util.Log.e("Dashboard", "Pi query handler not initialized");
            return;
        }
        
        // Stop existing loop if any
        stopPiQueryLoop();
        
        // Create query runnable
        piQueryRunnable = new Runnable() {
            @Override
            public void run() {
                if (usePiDetection && piDrowsinessDetector != null) {
                    // Query Pi for current detection result (Pi Camera mode)
                    piDrowsinessDetector.queryDetectionAsync(new PiDrowsinessDetector.DetectionCallback() {
                        @Override
                        public void onDetectionComplete(List<PiDrowsinessDetector.Detection> detections, boolean isDrowsy, float confidence) {
                            // Convert Pi detections to compatible format
                            List<DrowsinessDetector.Detection> finalDetections = new ArrayList<>();
                            for (PiDrowsinessDetector.Detection piDet : detections) {
                                finalDetections.add(new DrowsinessDetector.Detection(
                                    piDet.box, piDet.score, piDet.cls, piDet.label
                                ));
                            }
                            
                            // Debounce detection results to prevent rapid UI blinking
                            handleDebouncedDetection(isDrowsy, System.currentTimeMillis());
                            
                            frameCount++;
                            
                            // Schedule next query
                            if (piQueryHandler != null && piQueryRunnable != null) {
                                piQueryHandler.postDelayed(piQueryRunnable, PI_QUERY_INTERVAL_MS);
                            }
                        }
                        
                        @Override
                        public void onError(Exception error) {
                            android.util.Log.e("Dashboard", "Pi query error: " + error.getMessage(), error);
                            
                            // Retry after delay (video stream may still be working)
                            if (piQueryHandler != null && piQueryRunnable != null) {
                                piQueryHandler.postDelayed(piQueryRunnable, PI_QUERY_INTERVAL_MS * 2); // Slower retry on error
                            }
                        }
                    });
                } else {
                    // Not using Pi detection, stop loop
                    android.util.Log.d("Dashboard", "Pi detection not enabled, stopping query loop");
                }
            }
        };
        
        // Start query loop for detection results
        piQueryHandler.post(piQueryRunnable);
        android.util.Log.d("Dashboard", "Pi query loop started");
        
        // Start MJPEG stream from Pi Camera
        startPiStream();
    }
    
    /**
     * Start receiving MJPEG video stream from Pi Camera
     */
    private void startPiStream() {
        if (piStreamThread != null && piStreamThread.isAlive()) {
            android.util.Log.d("Dashboard", "Stream thread already running");
            return;
        }
        
        final String targetUrl = piServerUrl;
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            android.util.Log.w("Dashboard", "Pi server URL is empty. Cannot start stream.");
            return;
        }

        isStreaming = true;
        piStreamThread = new Thread(() -> {
            android.util.Log.d("Dashboard", "Starting Pi MJPEG stream thread from " + targetUrl);
            HttpURLConnection connection = null;
            InputStream streamInput = null;
            
            try {
                URL streamUrl = new URL(targetUrl + "/stream");
                connection = (HttpURLConnection) streamUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.connect();
                
                int responseCode = connection.getResponseCode();
                android.util.Log.d("Dashboard", "Stream connection response: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    streamInput = connection.getInputStream();
                    android.util.Log.d("Dashboard", "Stream connected, starting to receive frames...");
                    
                    // Read MJPEG stream (binary parsing for JPEG frames)
                    byte[] buffer = new byte[8192];
                    java.io.ByteArrayOutputStream frameBuffer = new java.io.ByteArrayOutputStream();
                    boolean inJpeg = false;
                    int lastByte = -1;
                    int consecutiveErrors = 0;
                    long lastFrameTime = 0;
                    
                    while (isStreaming && !Thread.currentThread().isInterrupted()) {
                        try {
                            int bytesRead = streamInput.read(buffer);
                            if (bytesRead <= 0) {
                                android.util.Log.w("Dashboard", "Stream read returned 0 bytes, connection may have closed");
                                break;
                            }
                            
                            for (int i = 0; i < bytesRead; i++) {
                                byte b = buffer[i];
                                
                                // Look for JPEG start marker (0xFF 0xD8)
                                if (lastByte == (byte)0xFF && b == (byte)0xD8) {
                                    // Process previous complete frame if exists
                                    if (inJpeg && frameBuffer.size() > 100) { // At least 100 bytes to be valid
                                        try {
                                            byte[] frameData = frameBuffer.toByteArray();
                                            
                                            // Verify it's a complete JPEG (ends with 0xFF 0xD9)
                                            boolean isComplete = false;
                                            if (frameData.length >= 2) {
                                                int len = frameData.length;
                                                if (frameData[len-2] == (byte)0xFF && frameData[len-1] == (byte)0xD9) {
                                                    isComplete = true;
                                                }
                                            }
                                            
                                            if (isComplete) {
                                                Bitmap frameBitmap = BitmapFactory.decodeByteArray(
                                                    frameData, 0, frameData.length);
                                                if (frameBitmap != null) {
                                                    final Bitmap finalBitmap = frameBitmap;
                                                    long currentTime = System.currentTimeMillis();
                                                    
                                                    // Limit display rate to prevent UI overload (max 2 FPS)
                                                    if (currentTime - lastFrameTime > 500) {
                                                        runOnUiThread(() -> {
                                                            displayFrameOnSurface(finalBitmap);
                                                        });
                                                        lastFrameTime = currentTime;
                                                        frameCount++;
                                                        consecutiveErrors = 0;
                                                        
                                                        if (frameCount % 10 == 0) {
                                                            android.util.Log.d("Dashboard", "Received frame #" + frameCount);
                                                        }
                                                    } else {
                                                        // Skip this frame if too soon
                                                        finalBitmap.recycle();
                                                    }
                                                } else {
                                                    consecutiveErrors++;
                                                    android.util.Log.w("Dashboard", "Failed to decode JPEG frame (error " + consecutiveErrors + ")");
                                                }
                                            }
                                        } catch (Exception e) {
                                            consecutiveErrors++;
                                            android.util.Log.w("Dashboard", "Error processing frame: " + e.getMessage());
                                        }
                                    }
                                    
                                    // Start new frame
                                    frameBuffer.reset();
                                    frameBuffer.write((byte)0xFF);
                                    frameBuffer.write((byte)0xD8);
                                    inJpeg = true;
                                    lastByte = b;
                                    continue;
                                }
                                
                                if (inJpeg) {
                                    frameBuffer.write(b);
                                    
                                    // Look for JPEG end marker (0xFF 0xD9)
                                    if (lastByte == (byte)0xFF && b == (byte)0xD9) {
                                        // End of JPEG frame - process immediately
                                        try {
                                            byte[] frameData = frameBuffer.toByteArray();
                                            Bitmap frameBitmap = BitmapFactory.decodeByteArray(
                                                frameData, 0, frameData.length);
                                            if (frameBitmap != null) {
                                                final Bitmap finalBitmap = frameBitmap;
                                                long currentTime = System.currentTimeMillis();
                                                
                                                // Limit display rate
                                                if (currentTime - lastFrameTime > 500) {
                                                    runOnUiThread(() -> {
                                                        displayFrameOnSurface(finalBitmap);
                                                    });
                                                    lastFrameTime = currentTime;
                                                    frameCount++;
                                                    consecutiveErrors = 0;
                                                    
                                                    if (frameCount % 10 == 0) {
                                                        android.util.Log.d("Dashboard", "Received frame #" + frameCount);
                                                    }
                                                } else {
                                                    finalBitmap.recycle();
                                                }
                                            }
                                        } catch (Exception e) {
                                            consecutiveErrors++;
                                            android.util.Log.w("Dashboard", "Error processing end frame: " + e.getMessage());
                                        }
                                        
                                        // Reset for next frame
                                        frameBuffer.reset();
                                        inJpeg = false;
                                    }
                                }
                                
                                lastByte = b;
                            }
                            
                            // Check for too many consecutive errors
                            if (consecutiveErrors > 50) {
                                android.util.Log.e("Dashboard", "Too many consecutive errors, stopping stream");
                                break;
                            }
                            
                        } catch (Exception e) {
                            consecutiveErrors++;
                            android.util.Log.e("Dashboard", "Error reading stream: " + e.getMessage());
                            if (consecutiveErrors > 10) {
                                break;
                            }
                            // Small delay before retrying
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ie) {
                                break;
                            }
                        }
                    }
                } else {
                    android.util.Log.e("Dashboard", "Stream connection failed: " + responseCode);
                    // Show error on UI
                    runOnUiThread(() -> {
                        drawPiErrorOnSurface("Stream connection failed: HTTP " + responseCode);
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("Dashboard", "Error in Pi stream thread: " + e.getMessage(), e);
                // Show error on UI
                runOnUiThread(() -> {
                    drawPiErrorOnSurface("Stream error: " + e.getMessage());
                });
            } finally {
                try {
                    if (streamInput != null) streamInput.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    android.util.Log.e("Dashboard", "Error closing stream: " + e.getMessage());
                }
                android.util.Log.d("Dashboard", "Pi stream thread stopped");
                
                // Auto-retry stream connection if it failed (after delay)
                if (isStreaming && isPiConnected) {
                    android.util.Log.d("Dashboard", "Stream stopped, will retry in 3 seconds...");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isStreaming && isPiConnected && usePiDetection) {
                            android.util.Log.d("Dashboard", "Retrying stream connection...");
                            startPiStream();
                        }
                    }, 3000);
                }
            }
        });
        
        piStreamThread.start();
        android.util.Log.d("Dashboard", "Pi stream thread started");
    }
    
    /**
     * Stop MJPEG stream from Pi
     */
    private void stopPiStream() {
        isStreaming = false;
        if (piStreamThread != null && piStreamThread.isAlive()) {
            piStreamThread.interrupt();
            try {
                piStreamThread.join(2000);
            } catch (InterruptedException e) {
                android.util.Log.w("Dashboard", "Interrupted waiting for stream thread");
            }
        }
        android.util.Log.d("Dashboard", "Pi stream stopped");
    }
    
    /**
     * Display video frame on SurfaceView
     */
    private void displayFrameOnSurface(Bitmap frameBitmap) {
        if (surfaceHolder == null || frameBitmap == null) {
            return;
        }
        
        android.graphics.Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        
        try {
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
            
            // Draw frame
            canvas.save();
            canvas.translate(dx, dy);
            canvas.scale(scale, scale);
            canvas.drawBitmap(frameBitmap, 0, 0, null);
            canvas.restore();
            
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }
    
    /**
     * Stop querying Pi for detection results
     */
    private void stopPiQueryLoop() {
        if (piQueryHandler != null && piQueryRunnable != null) {
            piQueryHandler.removeCallbacks(piQueryRunnable);
            piQueryRunnable = null;
            android.util.Log.d("Dashboard", "Pi query loop stopped");
        }
    }
    
    /**
     * Draw Pi Camera status on SurfaceView (since we don't receive video frames)
     */
    private void drawPiStatusOnSurface(boolean isDrowsy, float confidence, int frameCount) {
        if (surfaceHolder == null) {
            return;
        }
        
        android.graphics.Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        
        try {
            // Clear with dark background
            canvas.drawColor(android.graphics.Color.rgb(20, 20, 20));
            
            // Create text paint
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            
            // Title
            textPaint.setTextSize(48f);
            textPaint.setColor(android.graphics.Color.WHITE);
            float centerX = canvas.getWidth() / 2f;
            float y = 100f;
            canvas.drawText("Raspberry Pi Camera", centerX, y, textPaint);
            
            // Status
            textPaint.setTextSize(36f);
            y += 80f;
            canvas.drawText("Status: Connected ✓", centerX, y, textPaint);
            
            // Detection status
            textPaint.setTextSize(42f);
            y += 80f;
            if (isDrowsy) {
                textPaint.setColor(android.graphics.Color.RED);
                canvas.drawText("⚠️ DROWSY DETECTED ⚠️", centerX, y, textPaint);
            } else {
                textPaint.setColor(android.graphics.Color.GREEN);
                canvas.drawText("✓ Driver Alert", centerX, y, textPaint);
            }
            
            // Confidence
            textPaint.setTextSize(32f);
            textPaint.setColor(android.graphics.Color.WHITE);
            y += 70f;
            canvas.drawText(String.format("Confidence: %.1f%%", confidence * 100), centerX, y, textPaint);
            
            // Frame count
            textPaint.setTextSize(28f);
            textPaint.setColor(android.graphics.Color.GRAY);
            y += 60f;
            canvas.drawText(String.format("Frames Processed: %d", frameCount), centerX, y, textPaint);
            
            // Instructions
            textPaint.setTextSize(24f);
            textPaint.setColor(android.graphics.Color.DKGRAY);
            y += 100f;
            canvas.drawText("Pi Camera is capturing and", centerX, y, textPaint);
            y += 40f;
            canvas.drawText("processing frames on Raspberry Pi", centerX, y, textPaint);
            
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }
    
    /**
     * Draw error status on SurfaceView when Pi connection fails
     */
    private void drawPiErrorOnSurface(String errorMessage) {
        if (surfaceHolder == null) {
            return;
        }
        
        android.graphics.Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        
        try {
            // Clear with dark background
            canvas.drawColor(android.graphics.Color.rgb(20, 20, 20));
            
            // Create text paint
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            
            // Title
            textPaint.setTextSize(48f);
            textPaint.setColor(android.graphics.Color.RED);
            float centerX = canvas.getWidth() / 2f;
            float y = 100f;
            canvas.drawText("❌ Connection Error", centerX, y, textPaint);
            
            // Error message
            textPaint.setTextSize(28f);
            textPaint.setColor(android.graphics.Color.WHITE);
            y += 80f;
            canvas.drawText(errorMessage != null ? errorMessage : "Failed to connect to Pi", centerX, y, textPaint);
            
            // Instructions
            textPaint.setTextSize(24f);
            textPaint.setColor(android.graphics.Color.GRAY);
            y += 100f;
            canvas.drawText("Check:", centerX, y, textPaint);
            y += 50f;
            canvas.drawText("• Pi server is running", centerX, y, textPaint);
            y += 40f;
            canvas.drawText("• Same Wi-Fi network", centerX, y, textPaint);
            y += 40f;
            canvas.drawText("• Pi IP: 192.168.43.151:5000", centerX, y, textPaint);
            
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        // Surface is being destroyed
        stopPiQueryLoop();
        stopPiStream();
        
        
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
        } else if (id == R.id.menu_alert_tones) {
            // Navigate to Alert Tones Activity
            Intent intent = new Intent(Dashboard.this, AlertTonesActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_drowsiness_logs) {
            // Navigate to Drowsiness Logs Activity
            Intent intent = new Intent(Dashboard.this, DrowsinessLogsActivity.class);
            startActivity(intent);
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
        } else if (id == R.id.menu_set_pi_server) {
            showPiServerConfigurationDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        try {
            android.util.Log.d("Dashboard", "Finding view: drowsinessStatusIcon");
            drowsinessStatusIcon = findViewById(R.id.drowsinessStatusIcon);
            android.util.Log.d("Dashboard", "Finding view: drowsinessStatusText");
            drowsinessStatusText = findViewById(R.id.drowsinessStatusText);
            android.util.Log.d("Dashboard", "Finding view: alarmStatusIcon");
            alarmStatusIcon = findViewById(R.id.alarmStatusIcon);
            android.util.Log.d("Dashboard", "Finding view: alarmStatusText");
            alarmStatusText = findViewById(R.id.alarmStatusText);
            android.util.Log.d("Dashboard", "Finding view: drowsinessEventsCount");
            drowsinessEventsCount = findViewById(R.id.drowsinessEventsCount);
            android.util.Log.d("Dashboard", "Finding view: lastEventTime");
            lastEventTime = findViewById(R.id.lastEventTime);
            
            // Check if any views are null
            if (drowsinessStatusIcon == null) android.util.Log.e("Dashboard", "drowsinessStatusIcon is null");
            if (drowsinessStatusText == null) android.util.Log.e("Dashboard", "drowsinessStatusText is null");
            if (alarmStatusIcon == null) android.util.Log.e("Dashboard", "alarmStatusIcon is null");
            if (alarmStatusText == null) android.util.Log.e("Dashboard", "alarmStatusText is null");
            if (drowsinessEventsCount == null) android.util.Log.e("Dashboard", "drowsinessEventsCount is null");
            if (lastEventTime == null) android.util.Log.e("Dashboard", "lastEventTime is null");
            
            android.util.Log.d("Dashboard", "All views found successfully");
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error in initializeViews: " + e.getMessage(), e);
        }
    }

    private void initializeFirebaseData() {
        try {
            final DatabaseReference helmetStatusRef = getHelmetStatusRef();
            if (helmetStatusRef == null) {
                android.util.Log.w("Dashboard", "Helmet status reference is null - skipping initialization");
                return;
            }

            helmetStatusRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DataSnapshot snapshot = task.getResult();
                    if (snapshot == null || !snapshot.exists()) {
                        migrateLegacyHelmetStatus(helmetStatusRef);
                    } else {
                        ensureHelmetStatusFields(helmetStatusRef);
                    }
                } else {
                    android.util.Log.w("Dashboard", "Failed to fetch helmet status for initialization: " +
                            (task.getException() != null ? task.getException().getMessage() : "unknown error"));
                    ensureHelmetStatusFields(helmetStatusRef);
                }
            });
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error initializing Firebase data: " + e.getMessage(), e);
        }
    }

    private DatabaseReference getHelmetStatusRef() {
        if (mDatabase == null) {
            return null;
        }

        if (userHelmetStatusRef != null) {
            return userHelmetStatusRef;
        }

        String uid = currentUserId;
        if (uid == null && mAuth != null) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                uid = user.getUid();
                currentUserId = uid;
            }
        }

        if (uid != null) {
            userHelmetStatusRef = mDatabase.child("users").child(uid).child("helmet_status");
        } else {
            android.util.Log.w("Dashboard", "No user id available - falling back to legacy helmet_status node");
            userHelmetStatusRef = mDatabase.child("helmet_status");
        }

        return userHelmetStatusRef;
    }

    private void migrateLegacyHelmetStatus(DatabaseReference helmetStatusRef) {
        if (helmetStatusRef == null || mDatabase == null) {
            return;
        }

        mDatabase.child("helmet_status").get().addOnCompleteListener(legacyTask -> {
            boolean migrated = false;
            if (legacyTask.isSuccessful() && legacyTask.getResult() != null && legacyTask.getResult().exists()) {
                Map<String, Object> legacyData = new HashMap<>();
                for (DataSnapshot child : legacyTask.getResult().getChildren()) {
                    legacyData.put(child.getKey(), child.getValue());
                }

                if (!legacyData.isEmpty()) {
                    migrated = true;
                    helmetStatusRef.updateChildren(legacyData).addOnCompleteListener(updateTask -> {
                        if (!updateTask.isSuccessful()) {
                            android.util.Log.w("Dashboard", "Failed to migrate legacy helmet_status data: " +
                                    (updateTask.getException() != null ? updateTask.getException().getMessage() : "unknown error"));
                        }
                        ensureHelmetStatusFields(helmetStatusRef);
                    });
                }
            }

            if (!migrated) {
                setDefaultHelmetStatusValues(helmetStatusRef);
            }
        });
    }

    private void ensureHelmetStatusFields(DatabaseReference helmetStatusRef) {
        if (helmetStatusRef == null) {
            return;
        }

        helmetStatusRef.child("is_drowsy").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && (task.getResult() == null || !task.getResult().exists())) {
                helmetStatusRef.child("is_drowsy").setValue(false);
                android.util.Log.d("Dashboard", "Initialized missing is_drowsy field");
            }
        });

        helmetStatusRef.child("alarm_active").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && (task.getResult() == null || !task.getResult().exists())) {
                helmetStatusRef.child("alarm_active").setValue(false);
                android.util.Log.d("Dashboard", "Initialized missing alarm_active field");
            }
        });

        helmetStatusRef.child("events_count").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && (task.getResult() == null || !task.getResult().exists())) {
                helmetStatusRef.child("events_count").setValue(0);
                android.util.Log.d("Dashboard", "Initialized missing events_count field");
            }
        });

        helmetStatusRef.child("last_event").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && (task.getResult() == null || !task.getResult().exists())) {
                helmetStatusRef.child("last_event").setValue("No events yet");
                android.util.Log.d("Dashboard", "Initialized missing last_event field");
            }
        });
    }

    private void setDefaultHelmetStatusValues(DatabaseReference helmetStatusRef) {
        if (helmetStatusRef == null) {
            return;
        }

        Map<String, Object> defaults = new HashMap<>();
        defaults.put("is_drowsy", false);
        defaults.put("alarm_active", false);
        defaults.put("events_count", 0);
        defaults.put("last_event", "No events yet");

        helmetStatusRef.updateChildren(defaults).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                android.util.Log.w("Dashboard", "Failed to set default helmet status values: " +
                        (task.getException() != null ? task.getException().getMessage() : "unknown error"));
            }
        });
    }

    private void setupPrototypeDetection() {
        // First, show waiting state
        runOnUiThread(() -> {
            showPrototypeWaitingState();
        });
        
        final DatabaseReference helmetStatusRef = getHelmetStatusRef();
        if (helmetStatusRef == null) {
            android.util.Log.w("Dashboard", "Helmet status reference is null - cannot set up prototype detection");
            return;
        }

        // Check for prototype connection periodically with improved logic
        helmetStatusRef.addValueEventListener(new ValueEventListener() {
            private boolean hasDetectedPrototype = false;
            private long lastCheckTime = System.currentTimeMillis();
            private boolean isInitializing = true;
            
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Give initialization 3 seconds to complete before checking for prototype
                    if (isInitializing && System.currentTimeMillis() - lastCheckTime > 3000) {
                        isInitializing = false;
                    }
                    
                    // Only check for prototype after initialization period
                    if (!isInitializing && !hasDetectedPrototype) {
                        Boolean drowsyValue = snapshot.child("is_drowsy").getValue(Boolean.class);
                        Long lastEventTimestamp = snapshot.child("last_event").getValue(Long.class);
                        
                        // More robust prototype detection:
                        // 1. Check for recent timestamp updates (within last 10 seconds)
                        // 2. Verify data is changing/dynamic, not just static
                        boolean hasRecentActivity = false;
                        if (lastEventTimestamp != null) {
                            long timeDiff = System.currentTimeMillis() - lastEventTimestamp;
                            hasRecentActivity = timeDiff < 10000; // Within 10 seconds
                        }
                        
                        // Prototype is "connected" only if we detect real-time activity
                        if (drowsyValue != null && hasRecentActivity) {
                            hasDetectedPrototype = true;
                            android.util.Log.d("Dashboard", "Real prototype detected! Activity found within 10 seconds");
                            
                            // Switch to active monitoring
                            runOnUiThread(() -> {
                                showPrototypeConnectedState();
                            });
                            
                            // Remove this listener and start normal Firebase listeners
                            helmetStatusRef.removeEventListener(this);
                            setupFirebaseListeners();
                        } else {
                            android.util.Log.d("Dashboard", "No active prototype detected - data too old or static");
                        }
                    }
                } else {
                    android.util.Log.d("Dashboard", "Waiting for prototype connection - no data found...");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("Dashboard", "Prototype detection failed: " + error.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(Dashboard.this, "Failed to detect prototype connection", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setupFirebaseListeners() {
        final DatabaseReference helmetStatusRef = getHelmetStatusRef();
        if (helmetStatusRef == null) {
            android.util.Log.w("Dashboard", "Helmet status reference is null - cannot set up Firebase listeners");
            return;
        }

        helmetStatusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Log all available data for debugging prototype status
                    android.util.Log.d("Dashboard", "Received Firebase data: " + snapshot.toString());
                    
                    // Safely get values with null checks and proper type handling (preserving prototype data)
                    Boolean drowsyValue = snapshot.child("is_drowsy").getValue(Boolean.class);
                    Boolean alarmValue = snapshot.child("alarm_active").getValue(Boolean.class);
                    Integer countValue = snapshot.child("events_count").getValue(Integer.class);
                    Long lastEventTimestamp = snapshot.child("last_event").getValue(Long.class);

                    // Use defaults ONLY for null values - respect existing prototype data
                    boolean isDrowsy = drowsyValue != null ? drowsyValue : false;
                    boolean isAlarmActive = alarmValue != null ? alarmValue : false;
                    int eventsCount = countValue != null ? countValue : 0;

                    // Convert timestamp to readable string
                    String lastEventDisplay = "Never";
                    if (lastEventTimestamp != null) {
                        java.util.Date date = new java.util.Date(lastEventTimestamp);
                        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MMM dd, HH:mm:ss", java.util.Locale.getDefault());
                        lastEventDisplay = format.format(date);
                    }

                    android.util.Log.d("Dashboard", String.format("Parsed status - drowsy: %s, alarm: %s, count: %d, last: %s", 
                        isDrowsy, isAlarmActive, eventsCount, lastEventDisplay));

                    updateUI(isDrowsy, isAlarmActive, eventsCount, lastEventDisplay);
                } else {
                    android.util.Log.d("Dashboard", "No helmet status data found for user - prototype may not be connected");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Dashboard.this, "Failed to get status updates", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPrototypeWaitingState() {
        // Show waiting state for prototype connection
        if (drowsinessStatusIcon != null) {
            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_offline);
            drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_light));
            drowsinessStatusText.setText("Driver Status: Waiting for Prototype...");
        }
        
        if (alarmStatusIcon != null) {
            alarmStatusIcon.setImageResource(android.R.drawable.presence_offline);
            alarmStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_light));
            alarmStatusText.setText("Alarm Status: Waiting for Prototype...");
        }
        
        if (drowsinessEventsCount != null) {
            drowsinessEventsCount.setText("--");
        }
        
        if (lastEventTime != null) {
            lastEventTime.setText("Waiting for prototype connection...");
        }
        
        android.util.Log.d("Dashboard", "Showing prototype waiting state");
    }
    
    private void showPrototypeConnectedState() {
        // Show that prototype is connected and monitoring has started
        if (drowsinessStatusText != null) {
            drowsinessStatusText.setText("Driver Status: Monitoring Active");
        }
        
        android.util.Log.d("Dashboard", "Showing prototype connected state");
        
        // Optional: Show a brief toast
        Toast.makeText(this, "Prototype connected! Monitoring started.", Toast.LENGTH_SHORT).show();
    }

    private void updateUI(boolean isDrowsy, boolean isAlarmActive, int eventsCount, String lastEvent) {
        // Update drowsiness status
        if (isDrowsy) {
            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_busy);
            drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
            drowsinessStatusText.setText("Driver Status: Drowsy");
        } else {
            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_online);
            drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light));
            drowsinessStatusText.setText("Driver Status: Alert");
        }

        // Update alarm status
        if (isAlarmActive) {
            alarmStatusIcon.setImageResource(android.R.drawable.ic_lock_silent_mode);
            alarmStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
            alarmStatusText.setText("Alarm Status: Active");
        } else {
            alarmStatusIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            alarmStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light));
            alarmStatusText.setText("Alarm Status: Inactive");
        }

        // Update statistics
        drowsinessEventsCount.setText(String.valueOf(eventsCount));
        lastEventTime.setText(lastEvent != null ? lastEvent : "Never");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Handle other activity results if needed
        if (resultCode == RESULT_OK && data != null) {
            // Handle other activity results
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
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
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
                                // Support both is_eyes_closed and is_drowsy for compatibility
                                boolean isDrowsy = false;
                                if (data.has("is_eyes_closed")) {
                                    isDrowsy = data.getBoolean("is_eyes_closed");
                                } else if (data.has("is_drowsy")) {
                                    isDrowsy = data.getBoolean("is_drowsy");
                                }
                                long timestamp = data.optLong("timestamp", System.currentTimeMillis());
                                
                                // Use debounced detection to prevent rapid UI blinking
                                handleDebouncedDetection(isDrowsy, timestamp);
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
                                
                                // Run drowsiness detection on the frame
                                if (usePiDetection && piDrowsinessDetector != null) {
                                    // Use Raspberry Pi for detection
                                    piDrowsinessDetector.isDrowsyAsync(frameBitmap, new PiDrowsinessDetector.DetectionCallback() {
                                        @Override
                                        public void onDetectionComplete(List<PiDrowsinessDetector.Detection> detections, boolean isDrowsy, float confidence) {
                                            // Convert Pi detections to compatible format
                                            List<DrowsinessDetector.Detection> finalDetections = new ArrayList<>();
                                            for (PiDrowsinessDetector.Detection piDet : detections) {
                                                finalDetections.add(new DrowsinessDetector.Detection(
                                                    piDet.box, piDet.score, piDet.cls, piDet.label
                                                ));
                                            }
                                            final boolean finalIsDrowsy = isDrowsy;
                                            final List<DrowsinessDetector.Detection> finalDetectionsList = finalDetections;
                                            
                                            // Update UI on main thread
                                            runOnUiThread(() -> {
                                                updateDetectionUI(finalDetectionsList, finalIsDrowsy, frameBitmap);
                                            });
                                        }
                                        
                                        @Override
                                        public void onError(Exception error) {
                                            android.util.Log.e("Dashboard", "Pi detection error: " + error.getMessage(), error);
                                            // Fallback: draw frame without detection
                                            runOnUiThread(() -> {
                                                if (surfaceHolder != null) {
                                                    android.graphics.Canvas canvas = surfaceHolder.lockCanvas();
                                                    if (canvas != null) {
                                                        float scale = Math.min(
                                                            (float)canvas.getWidth() / frameBitmap.getWidth(),
                                                            (float)canvas.getHeight() / frameBitmap.getHeight()
                                                        );
                                                        float dx = (canvas.getWidth() - frameBitmap.getWidth() * scale) / 2;
                                                        float dy = (canvas.getHeight() - frameBitmap.getHeight() * scale) / 2;
                                                        canvas.drawColor(android.graphics.Color.BLACK);
                                                        canvas.save();
                                                        canvas.translate(dx, dy);
                                                        canvas.scale(scale, scale);
                                                        canvas.drawBitmap(frameBitmap, 0, 0, paint);
                                                        canvas.restore();
                                                        surfaceHolder.unlockCanvasAndPost(canvas);
                                                    }
                                                }
                                            });
                                        }
                                    });
                                } else {
                                    // Pi-only mode: no local detection fallback
                                    android.util.Log.w("Dashboard", "Pi detection not available - skipping frame processing");
                                    // Draw frame without inference if detector not available
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
                                }
                                
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

    /**
     * Helper method to update UI with detection results
     */
    private void updateDetectionUI(List<DrowsinessDetector.Detection> detections, boolean isDrowsy, android.graphics.Bitmap frameBitmap) {
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
                
                // Draw detection boxes
                android.graphics.Paint boxPaint = new android.graphics.Paint();
                boxPaint.setStyle(android.graphics.Paint.Style.STROKE);
                boxPaint.setStrokeWidth(3f);
                boxPaint.setColor(android.graphics.Color.GREEN);
                
                // Text paint for labels
                android.graphics.Paint textPaint = new android.graphics.Paint();
                textPaint.setColor(android.graphics.Color.GREEN);
                textPaint.setTextSize(24f);
                textPaint.setAntiAlias(true);
                
                for (DrowsinessDetector.Detection detection : detections) {
                    // Draw bounding box
                    canvas.drawRect(detection.box, boxPaint);
                    
                    // Draw label with confidence
                    String label = detection.label + " (" + String.format("%.2f", detection.score) + ")";
                    canvas.drawText(label, detection.box.left, detection.box.top - 10, textPaint);
                }
                
                canvas.restore();
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
        
        // Update drowsiness status and UI
        updateDrowsinessEvent(isDrowsy, System.currentTimeMillis());
        
        // Update UI status to match detection
        if (isDrowsy) {
            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_busy);
            drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
            drowsinessStatusText.setText("Driver Status: Drowsy");
        } else {
            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_online);
            drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light));
            drowsinessStatusText.setText("Driver Status: Alert");
        }
    }

    private void updateDrowsinessEvent(boolean isDrowsy, long timestamp) {
        // Update Firebase in background thread to avoid blocking UI
        networkExecutor.execute(() -> {
            try {
                DatabaseReference statusRef = getHelmetStatusRef();
                if (statusRef == null) {
                    android.util.Log.w("Dashboard", "Helmet status reference unavailable while updating drowsiness event");
                    return;
                }
                statusRef.child("is_drowsy").setValue(isDrowsy);
                statusRef.child("last_event").setValue(timestamp);
                
                // Don't increment count here - only increment when alarm triggers
                // This prevents counting events that don't result in alarms
            } catch (Exception e) {
                android.util.Log.e("Dashboard", "Error updating Firebase: " + e.getMessage(), e);
            }
        });
    }
    
    private void incrementDrowsinessEventCount(long timestamp) {
        // Only increment count when alarm actually triggers (after 1.5 seconds)
        // This ensures we only count actual drowsiness events, not brief eye closures
        networkExecutor.execute(() -> {
            try {
                DatabaseReference statusRef = getHelmetStatusRef();
                if (statusRef == null) {
                    android.util.Log.w("Dashboard", "Helmet status reference unavailable while incrementing event count");
                    return;
                }
                statusRef.child("last_event").setValue(timestamp);
                
                // Get current count and increment
                statusRef.child("events_count").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DataSnapshot snapshot = task.getResult();
                        int currentCount = 0;
                        if (snapshot != null && snapshot.exists()) {
                            Integer value = snapshot.getValue(Integer.class);
                            if (value != null) {
                                currentCount = value;
                            }
                        }
                        // Increment the count for this alarm event
                        statusRef.child("events_count").setValue(currentCount + 1);
                        android.util.Log.d("Dashboard", "Drowsiness event count incremented to: " + (currentCount + 1));
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("Dashboard", "Error incrementing event count: " + e.getMessage(), e);
            }
        });
    }
    
    private void handleDebouncedDetection(boolean isDrowsy, long timestamp) {
        // Initialize debounce handler if needed
        if (debounceHandler == null) {
            debounceHandler = new Handler(Looper.getMainLooper());
        }
        
        // If state changed, reset the debounce timer
        if (isDrowsy != lastDrowsyState) {
            lastStateChangeTime = System.currentTimeMillis();
            lastDrowsyState = isDrowsy;
            
            // Cancel any pending debounce runnable
            if (debounceRunnable != null && debounceHandler != null) {
                debounceHandler.removeCallbacks(debounceRunnable);
            }
            
            // IMPORTANT: If eyes open, stop alarm immediately (no debounce delay)
            if (!isDrowsy) {
                android.util.Log.d("Dashboard", "Eyes opened - stopping alarm immediately");
                handleDrowsinessTimer(isDrowsy);  // This will stop the alarm
                updateDrowsinessEvent(isDrowsy, timestamp);
                updateUIWithDrowsinessState(isDrowsy);
            } else {
                // Eyes closed - use debounce to prevent false positives
                debounceRunnable = () -> {
                    // Only update if state is still the same after debounce delay
                    if (isDrowsy == lastDrowsyState) {
                        android.util.Log.d("Dashboard", "Debounced detection: isDrowsy=" + isDrowsy);
                        handleDrowsinessTimer(isDrowsy);
                        updateDrowsinessEvent(isDrowsy, timestamp);
                        updateUIWithDrowsinessState(isDrowsy);
                    }
                };
                
                debounceHandler.postDelayed(debounceRunnable, DEBOUNCE_DELAY_MS);
            }
        } else {
            // State hasn't changed, but check if we've been in this state long enough
            long timeSinceChange = System.currentTimeMillis() - lastStateChangeTime;
            if (timeSinceChange >= DEBOUNCE_DELAY_MS) {
                // State has been stable, update immediately
                handleDrowsinessTimer(isDrowsy);
                updateDrowsinessEvent(isDrowsy, timestamp);
                updateUIWithDrowsinessState(isDrowsy);
            }
        }
    }
    
    private void updateUIWithDrowsinessState(boolean isDrowsy) {
        runOnUiThread(() -> {
            if (isDrowsy) {
                drowsinessStatusIcon.setImageResource(android.R.drawable.presence_busy);
                drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
                drowsinessStatusText.setText("Driver Status: Drowsy");
            } else {
                drowsinessStatusIcon.setImageResource(android.R.drawable.presence_online);
                drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light));
                drowsinessStatusText.setText("Driver Status: Alert");
            }
        });
    }
    
    private void handleDrowsinessTimer(boolean isDrowsy) {
        android.util.Log.d("Dashboard", "handleDrowsinessTimer called with isDrowsy: " + isDrowsy);
        
        if (isDrowsy) {
            // Eyes are closed - start timer for 1.5 seconds
            long currentTime = System.currentTimeMillis();
            
            if (!isDrowsinessTimerActive) {
                // Start the 1.5-second timer
                hasLoggedCurrentAlarm = false; // Reset flag for new drowsiness event
                startDrowsinessTimer(currentTime);
            } else {
                // Timer is already active, check if 1.5 seconds have passed
                long elapsed = currentTime - drowsinessStartTime;
                if (elapsed >= DROWSINESS_THRESHOLD_MS) {
                    // 1.5 seconds have passed, activate alarm
                    activateDrowsinessAlarm();
                }
            }
        } else {
            // Eyes are open - immediately stop alarm and cancel timer
            hasLoggedCurrentAlarm = false; // Reset flag when eyes open
            cancelDrowsinessTimer();
            deactivateDrowsinessAlarm();
        }
    }

    private void startDrowsinessTimer(long startTime) {
        android.util.Log.d("Dashboard", "Starting drowsiness timer");
        isDrowsinessTimerActive = true;
        drowsinessStartTime = startTime;

        if (drowsinessTimerHandler == null) {
            drowsinessTimerHandler = new Handler(Looper.getMainLooper());
        }

        if (drowsinessTimerRunnable != null) {
            drowsinessTimerHandler.removeCallbacks(drowsinessTimerRunnable);
        }

        drowsinessTimerRunnable = () -> {
            android.util.Log.d("Dashboard", "Drowsiness timer threshold reached via handler");
            activateDrowsinessAlarm();
        };

        drowsinessTimerHandler.postDelayed(drowsinessTimerRunnable, DROWSINESS_THRESHOLD_MS);
    }

    private void cancelDrowsinessTimer() {
        if (drowsinessTimerHandler != null && drowsinessTimerRunnable != null) {
            drowsinessTimerHandler.removeCallbacks(drowsinessTimerRunnable);
        }
        drowsinessTimerRunnable = null;
        isDrowsinessTimerActive = false;
        drowsinessStartTime = 0;
    }

    private void activateDrowsinessAlarm() {
        // Only activate if timer is active (eyes have been closed for 2+ seconds)
        if (!isDrowsinessTimerActive) {
            return;
        }
        
        long elapsed = System.currentTimeMillis() - drowsinessStartTime;
        if (elapsed < DROWSINESS_THRESHOLD_MS) {
            return;
        }
        
        // Check if alarm should be playing but isn't
        boolean shouldPlayAlarm = !isAlarmSounding || (alertMediaPlayer == null || !alertMediaPlayer.isPlaying());
        
        if (shouldPlayAlarm) {
            android.util.Log.w("Dashboard", "Eyes closed for 1.5 seconds - activating alarm");
            playAlertTone();
            lastAlarmTime = System.currentTimeMillis();
            
            // Increment event count only once per alarm trigger (not multiple times)
            if (!hasLoggedCurrentAlarm) {
                hasLoggedCurrentAlarm = true;
                incrementDrowsinessEventCount(System.currentTimeMillis());
            }

            runOnUiThread(() ->
                Toast.makeText(Dashboard.this, "🚨 Eyes closed for 1.5 seconds - alarm activated! 🚨", Toast.LENGTH_SHORT).show()
            );
        }

        runOnUiThread(() -> {
            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_busy);
            drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
            drowsinessStatusText.setText("Driver Status: Drowsy");

            alarmStatusIcon.setImageResource(android.R.drawable.ic_lock_silent_mode);
            alarmStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
            alarmStatusText.setText("Alarm Status: Active");
        });
    }

    private void deactivateDrowsinessAlarm() {
        boolean alarmWasPlaying = isAlarmSounding;

        if (alarmWasPlaying) {
            android.util.Log.d("Dashboard", "Driver is awake - stopping alarm");
            stopAlertTone();
        }

        runOnUiThread(() -> {
            if (alarmWasPlaying) {
                Toast.makeText(Dashboard.this, "✅ Driver is awake - Alarm stopped", Toast.LENGTH_SHORT).show();
            }

            drowsinessStatusIcon.setImageResource(android.R.drawable.presence_online);
            drowsinessStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light));
            drowsinessStatusText.setText("Driver Status: Awake");

            alarmStatusIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            alarmStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light));
            alarmStatusText.setText("Alarm Status: Inactive");
        });
    }
    
    private void stopAlertTone() {
        try {
            boolean wasSounding = isAlarmSounding || (alertMediaPlayer != null && alertMediaPlayer.isPlaying());

            // Stop any currently playing alert
            if (alertMediaPlayer != null) {
                if (alertMediaPlayer.isPlaying()) {
                    alertMediaPlayer.stop();
                    android.util.Log.d("Dashboard", "Alarm stopped - driver is awake");
                }
                alertMediaPlayer.release();
                alertMediaPlayer = null;
            }

            // Stop vibration in sync
            stopVibration();

            isAlarmSounding = false;

            if (wasSounding && networkExecutor != null && !networkExecutor.isShutdown() && mDatabase != null) {
                networkExecutor.execute(() -> {
                    try {
                        DatabaseReference statusRef = getHelmetStatusRef();
                        if (statusRef != null) {
                            statusRef.child("alarm_active").setValue(false);
                        }
                    } catch (Exception dbError) {
                        android.util.Log.e("Dashboard", "Error updating alarm status: " + dbError.getMessage(), dbError);
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error stopping alert tone: " + e.getMessage(), e);
        }
    }
    
    private void playAlertTone() {
        try {
            // Stop any currently playing alert first
            stopAlertTone();
            
            // Get selected alert tone
            String selectedTone = sharedPreferences.getString("selected_alert_tone", "Default Beep");
            
            // Create and play the selected tone
            alertMediaPlayer = new MediaPlayer();
            
            // Map to distinct system tones (aligned with AlertTonesActivity)
            alertMediaPlayer.setDataSource(this, selectToneUriDistinct(this, selectedTone));
            
            alertMediaPlayer.prepare();
            alertMediaPlayer.setLooping(true);
            alertMediaPlayer.start();
            isAlarmSounding = true;

            // Start vibration in sync with the tone
            startVibrationForTone(selectedTone);
            
            // Update Firebase and log event in background thread
            if (networkExecutor != null && !networkExecutor.isShutdown() && mDatabase != null) {
                networkExecutor.execute(() -> {
                    try {
                        DatabaseReference statusRef = getHelmetStatusRef();
                        if (statusRef != null) {
                            // Update Firebase to show alarm is active
                            statusRef.child("alarm_active").setValue(true);
                        }
                        
                        // Log drowsiness event to database
                        logDrowsinessEvent();
                    } catch (Exception e) {
                        android.util.Log.e("Dashboard", "Error updating Firebase after alert: " + e.getMessage(), e);
                    }
                });
            }
            
            android.util.Log.d("Dashboard", "Alert tone played: " + selectedTone);
            
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error playing alert tone: " + e.getMessage(), e);
        }
    }
    
    private void logDrowsinessEvent() {
        // Log to Firebase in background thread to avoid blocking UI
        networkExecutor.execute(() -> {
            try {
                String userId = currentUserId;
                if (userId == null && mAuth != null && mAuth.getCurrentUser() != null) {
                    userId = mAuth.getCurrentUser().getUid();
                    currentUserId = userId;
                }

                if (userId == null) {
                    android.util.Log.w("Dashboard", "No authenticated user - skipping drowsiness event logging");
                    return;
                }

                long timestamp = System.currentTimeMillis();
                
                // Create a unique key for this log entry
                DatabaseReference userLogsRef = mDatabase.child("drowsiness_logs").child(userId);
                String logKey = userLogsRef.push().getKey();
                if (logKey == null) {
                    android.util.Log.w("Dashboard", "Unable to create log key for drowsiness event");
                    return;
                }
                
                // Save the timestamp to the database
                userLogsRef.child(logKey).setValue(timestamp)
                    .addOnSuccessListener(aVoid -> {
                        android.util.Log.d("Dashboard", "Drowsiness event logged successfully");
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("Dashboard", "Failed to log drowsiness event: " + e.getMessage());
                    });
                    
            } catch (Exception e) {
                android.util.Log.e("Dashboard", "Error logging drowsiness event: " + e.getMessage(), e);
            }
        });
    }

    // ESP32-related methods removed - using Raspberry Pi 5 with Pi Camera instead


    private void processImageData(byte[] imageData) {
        frameCount++;
        
        // Process every frame for maximum detection responsiveness (Pi-only mode)
        boolean shouldRunML = usePiDetection && piDrowsinessDetector != null && isPiConnected;
        
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            if (bitmap != null) {
                // Store bitmap dimensions for logging
                int bitmapWidth = bitmap.getWidth();
                int bitmapHeight = bitmap.getHeight();
                
                // Log every 10th frame for better debugging
                if (frameCount % 10 == 0) {
                android.util.Log.d("Dashboard", "Processing frame #" + frameCount + " with ML detection: " + 
                                 (shouldRunML ? "YES" : "SKIP") + " - " + bitmapWidth + "x" + bitmapHeight);
                }
                
                // Display the frame immediately on UI thread
                runOnUiThread(() -> {
                    try {
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
                        }
                        
                        // Use stored bitmap dimensions
                        
                        // Create bitmap copy for ML detection (only if needed)
                        Bitmap mlBitmap = null;
                        if (shouldRunML) {
                            try {
                                // Create smaller bitmap for ML processing to reduce memory usage
                                int mlWidth = Math.min(320, bitmapWidth);
                                int mlHeight = Math.min(240, bitmapHeight);
                                mlBitmap = Bitmap.createScaledBitmap(bitmap, mlWidth, mlHeight, true);
                                android.util.Log.d("Dashboard", "Created ML bitmap: " + mlWidth + "x" + mlHeight);
                            } catch (Exception e) {
                                android.util.Log.e("Dashboard", "Failed to create ML bitmap copy: " + e.getMessage());
                            }
                        }
                        
                        // Recycle the original bitmap immediately after creating ML copy
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        
                        // Run ML detection with the isolated bitmap copy
                            final Bitmap finalMlBitmap = mlBitmap;
                        if (mlBitmap != null) {
                            if (usePiDetection && piDrowsinessDetector != null) {
                                // Use Raspberry Pi for detection
                                piDrowsinessDetector.isDrowsyAsync(finalMlBitmap, new PiDrowsinessDetector.DetectionCallback() {
                                    @Override
                                    public void onDetectionComplete(List<PiDrowsinessDetector.Detection> detections, boolean isDrowsy, float confidence) {
                                        // Log every ML result for debugging
                                        android.util.Log.d("Dashboard", "Frame #" + frameCount + " Pi ML result: " + (isDrowsy ? "DROWSY" : "AWAKE"));
                                        
                                        // Always log drowsiness detection for debugging
                                        if (isDrowsy) {
                                            android.util.Log.w("Dashboard", "DROWSINESS DETECTED! Frame #" + frameCount + " - Updating UI status");
                                        }
                                        
                                        // Update drowsiness detection logic (timer will handle UI updates)
                                        updateDrowsinessEvent(isDrowsy, System.currentTimeMillis());
                                        
                                        // Recycle ML bitmap after processing
                                        if (finalMlBitmap != null && !finalMlBitmap.isRecycled()) {
                                            finalMlBitmap.recycle();
                                        }
                                    }
                                    
                                    @Override
                                    public void onError(Exception error) {
                                        android.util.Log.e("Dashboard", "Pi ML detection error: " + error.getMessage(), error);
                                        // Recycle ML bitmap on error
                                        if (finalMlBitmap != null && !finalMlBitmap.isRecycled()) {
                                            finalMlBitmap.recycle();
                                        }
                                    }
                                });
                            } else {
                                // Pi-only mode: no local detection fallback
                                android.util.Log.w("Dashboard", "Pi detection not available - skipping ML processing");
                                // Recycle ML bitmap
                                if (finalMlBitmap != null && !finalMlBitmap.isRecycled()) {
                                    finalMlBitmap.recycle();
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        android.util.Log.e("Dashboard", "Error in main UI thread: " + e.getMessage(), e);
                        // Recycle bitmap on error (if not already recycled)
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error processing image data: " + e.getMessage(), e);
        }
    }


    // ESP32 connection dialog and methods removed - using Raspberry Pi 5 instead

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        android.util.Log.d("Dashboard", "onDestroy started - cleaning up resources");
        
        // Stop Pi query loop and stream
        stopPiQueryLoop();
        stopPiStream();
        
        
        // Stop Bluetooth connection if active
        isConnected = false;
        
        // Interrupt and cleanup threads
        if (bluetoothThread != null) {
            bluetoothThread.interrupt();
            bluetoothThread = null;
        }
        
        // Close Bluetooth connections
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (Exception e) {
            android.util.Log.e("Dashboard", "Error closing Bluetooth connections: " + e.getMessage());
        }
        
        // Shutdown executors properly
        if (networkExecutor != null) {
            networkExecutor.shutdown();
            try {
                if (!networkExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    networkExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                networkExecutor.shutdownNow();
            }
            networkExecutor = null;
        }
        
        if (inferenceExecutor != null) {
            inferenceExecutor.shutdown();
            try {
                if (!inferenceExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    inferenceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                inferenceExecutor.shutdownNow();
            }
            inferenceExecutor = null;
        }
        
        
        // Clean up alert system
        cancelDrowsinessTimer();
        isAlarmSounding = false;
        
        if (alertMediaPlayer != null) {
            try {
                if (alertMediaPlayer.isPlaying()) {
                    alertMediaPlayer.stop();
                }
                alertMediaPlayer.release();
            } catch (Exception e) {
                android.util.Log.e("Dashboard", "Error releasing MediaPlayer: " + e.getMessage());
            }
            alertMediaPlayer = null;
        }
        
        // Clean up bitmap resources
        if (frameBitmap != null && !frameBitmap.isRecycled()) {
            frameBitmap.recycle();
            frameBitmap = null;
        }
        
        // Clean up canvas and paint
        canvas = null;
        paint = null;
        
        android.util.Log.d("Dashboard", "onDestroy completed - all resources cleaned up");
    }
} 