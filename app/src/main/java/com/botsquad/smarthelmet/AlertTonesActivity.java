package com.botsquad.smarthelmet;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
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
    
    private String[] alertTones = {
        "Default Beep",
        "High Pitch Alert",
        "Low Pitch Warning",
        "Continuous Beep",
        "Pulse Alert",
        "Emergency Siren"
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_tones);
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("SmartHelmetPrefs", MODE_PRIVATE);
        
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
        
        // Create and play the selected tone
        try {
            mediaPlayer = new MediaPlayer();
            
            // For demo purposes, we'll use system notification sounds
            // In a real implementation, you would have custom audio files
            switch (toneName) {
                case "Default Beep":
                    mediaPlayer.setDataSource(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                    break;
                case "High Pitch Alert":
                    // Use a higher frequency tone simulation
                    mediaPlayer.setDataSource(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
                    break;
                case "Low Pitch Warning":
                    // Use a lower frequency tone simulation
                    mediaPlayer.setDataSource(this, android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                    break;
                case "Continuous Beep":
                    // Use notification sound for continuous beep
                    mediaPlayer.setDataSource(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                    break;
                case "Pulse Alert":
                    // Use alarm sound for pulse alert
                    mediaPlayer.setDataSource(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
                    break;
                case "Emergency Siren":
                    // Use ringtone for emergency siren
                    mediaPlayer.setDataSource(this, android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                    break;
                default:
                    mediaPlayer.setDataSource(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                    break;
            }
            
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            Toast.makeText(this, "Playing: " + toneName, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "Error playing tone: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
    }
} 