package com.botsquad.smarthelmet;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AlertTonesActivity extends AppCompatActivity {
    
    private ListView tonesListView;
    private Button testButton;
    private Button saveButton;
    private MediaPlayer mediaPlayer;
    private String selectedTone;
    private SharedPreferences sharedPreferences;
    private Vibrator vibrator;
    
    private String[] alertTones = {
        "Default Beep",
        "High Pitch Alert",
        "Low Pitch Warning",
        "Pulse Alert",
        "Emergency Siren"
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_tones);
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("SmartHelmetPrefs", MODE_PRIVATE);

        // Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        
        // Initialize views
        tonesListView = findViewById(R.id.tonesListView);
        testButton = findViewById(R.id.testButton);
        saveButton = findViewById(R.id.saveButton);
        
        // Set up ListView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_list_item_single_choice, alertTones);
        tonesListView.setAdapter(adapter);
        tonesListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        // Load previously selected tone
        selectedTone = sharedPreferences.getString("selected_alert_tone", "Default Beep");
        int selectedIndex = getToneIndex(selectedTone);
        if (selectedIndex >= 0) {
            tonesListView.setItemChecked(selectedIndex, true);
        }
        
        // Set up click listeners
        tonesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedTone = alertTones[position];
                testButton.setEnabled(true);
            }
        });
        
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playTestTone(selectedTone);
            }
        });
        
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSelectedTone();
            }
        });
        
        // Enable test button if a tone is selected
        testButton.setEnabled(selectedTone != null);
    }
    
    private int getToneIndex(String toneName) {
        for (int i = 0; i < alertTones.length; i++) {
            if (alertTones[i].equals(toneName)) {
                return i;
            }
        }
        return -1;
    }
    
    private void playTestTone(String toneName) {
        // Stop any currently playing tone
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        // Stop any running vibration
        if (vibrator != null) vibrator.cancel();
        
        // Create and play the selected tone
        try {
            mediaPlayer = new MediaPlayer();
            
            mediaPlayer.setDataSource(this, selectToneUriDistinct(this, toneName));
            
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Start matching vibration pattern while testing
            try {
                if (vibrator != null && vibrator.hasVibrator()) {
                    long[] timings;
                    int[] amplitudes;
                    int repeatIndex = 0;
                    switch (toneName) {
                        case "Pulse Alert":
                            timings = new long[]{0, 400, 200};
                            amplitudes = new int[]{0, VibrationEffect.DEFAULT_AMPLITUDE, 0};
                            break;
                        case "Continuous Beep":
                            timings = new long[]{0, 800, 50};
                            amplitudes = new int[]{0, VibrationEffect.DEFAULT_AMPLITUDE, 0};
                            break;
                        case "Emergency Siren":
                            timings = new long[]{0, 300, 150, 300, 150};
                            amplitudes = new int[]{0, 255, 0, 200, 0};
                            break;
                        default:
                            timings = new long[]{0, 500, 500};
                            amplitudes = new int[]{0, VibrationEffect.DEFAULT_AMPLITUDE, 0};
                            break;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, repeatIndex));
                    } else {
                        vibrator.vibrate(timings, repeatIndex);
                    }
                }
            } catch (Exception ignore) {}
            
            Toast.makeText(this, "Playing: " + toneName, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "Error playing tone: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Uri selectToneUriDistinct(android.content.Context context, String toneName) {
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

    private Uri pickRingtone(android.content.Context context, int type, int index) {
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
                    // If requested index exceeds available list, fallback to first
                    cursor.moveToFirst();
                    return rm.getRingtoneUri(cursor.getPosition());
                }
            } finally {
                cursor.close();
            }
        }
        // Fallbacks by type
        if (type == RingtoneManager.TYPE_ALARM) return android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
        if (type == RingtoneManager.TYPE_RINGTONE) return android.provider.Settings.System.DEFAULT_RINGTONE_URI;
        return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
    }
    
    private void saveSelectedTone() {
        if (selectedTone != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("selected_alert_tone", selectedTone);
            editor.apply();
            
            Toast.makeText(this, "Alert tone saved: " + selectedTone, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Please select a tone first", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignore) {}
        }
    }
} 