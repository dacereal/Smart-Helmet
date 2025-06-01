package com.botsquad.smarthelmet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Dashboard extends AppCompatActivity {
    private ImageView drowsinessStatusIcon;
    private TextView drowsinessStatusText;
    private ImageView alarmStatusIcon;
    private TextView alarmStatusText;
    private TextView drowsinessEventsCount;
    private TextView lastEventTime;
    private ImageButton btnLogout;

    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        initializeViews();
        setupFirebaseListeners();
        setupLogoutButton();
    }

    private void initializeViews() {
        drowsinessStatusIcon = findViewById(R.id.drowsinessStatusIcon);
        drowsinessStatusText = findViewById(R.id.drowsinessStatusText);
        alarmStatusIcon = findViewById(R.id.alarmStatusIcon);
        alarmStatusText = findViewById(R.id.alarmStatusText);
        drowsinessEventsCount = findViewById(R.id.drowsinessEventsCount);
        lastEventTime = findViewById(R.id.lastEventTime);
        btnLogout = findViewById(R.id.btn_logout);
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

    private void setupLogoutButton() {
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(Dashboard.this, Login.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
} 