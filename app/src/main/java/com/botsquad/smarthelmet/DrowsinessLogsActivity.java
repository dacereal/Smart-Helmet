package com.botsquad.smarthelmet;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DrowsinessLogsActivity extends AppCompatActivity {
    
    private ListView logsListView;
    private TextView noLogsTextView;
    private Button sortButton;
    private ArrayAdapter<String> logsAdapter;
    private List<String> logsList;
    private List<Long> timestampsList;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private String currentUserId;
    private boolean isLatestFirst = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drowsiness_logs);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUserId = mAuth.getCurrentUser().getUid();
        
        // Initialize views
        logsListView = findViewById(R.id.logsListView);
        noLogsTextView = findViewById(R.id.noLogsTextView);
        sortButton = findViewById(R.id.sortButton);
        
        // Initialize data
        logsList = new ArrayList<>();
        timestampsList = new ArrayList<>();
        logsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, logsList);
        logsListView.setAdapter(logsAdapter);
        
        // Set up toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Drowsiness Logs");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // Set up sort button
        sortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSortOrder();
            }
        });
        
        // Load drowsiness logs
        loadDrowsinessLogs();
    }
    
    private void loadDrowsinessLogs() {
        // Show loading state
        noLogsTextView.setText("Loading logs...");
        noLogsTextView.setVisibility(View.VISIBLE);
        logsListView.setVisibility(View.GONE);
        
        // Get user's drowsiness logs
        DatabaseReference logsRef = mDatabase.child("drowsiness_logs").child(currentUserId);
        
        logsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                logsList.clear();
                timestampsList.clear();
                
                if (snapshot.exists()) {
                    // Convert snapshot to list of log entries with timestamps
                    for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                        try {
                            Long timestamp = logSnapshot.getValue(Long.class);
                            if (timestamp != null) {
                                timestampsList.add(timestamp);
                                String formattedLog = formatLogEntry(timestamp);
                                logsList.add(formattedLog);
                            }
                        } catch (Exception e) {
                            // Handle any parsing errors
                            android.util.Log.e("DrowsinessLogs", "Error parsing log entry: " + e.getMessage());
                        }
                    }
                    
                    // Sort logs based on current sort order
                    sortLogs();
                    
                    if (logsList.isEmpty()) {
                        showNoLogsMessage();
                    } else {
                        showLogsList();
                    }
                } else {
                    showNoLogsMessage();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("DrowsinessLogs", "Error loading logs: " + error.getMessage());
                Toast.makeText(DrowsinessLogsActivity.this, "Failed to load logs", Toast.LENGTH_SHORT).show();
                showNoLogsMessage();
            }
        });
    }
    
    private String formatLogEntry(long timestamp) {
        try {
            Date date = new Date(timestamp);
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            
            String dateStr = dateFormat.format(date);
            String timeStr = timeFormat.format(date);
            
            return dateStr + " at " + timeStr;
        } catch (Exception e) {
            return "Invalid timestamp: " + timestamp;
        }
    }
    
    private void showNoLogsMessage() {
        noLogsTextView.setText("No drowsiness events recorded yet.\n\nDrowsiness events will appear here when detected.");
        noLogsTextView.setVisibility(View.VISIBLE);
        logsListView.setVisibility(View.GONE);
    }
    
    private void showLogsList() {
        noLogsTextView.setVisibility(View.GONE);
        logsListView.setVisibility(View.VISIBLE);
        logsAdapter.notifyDataSetChanged();
    }
    
    private void sortLogs() {
        // Create a list of entries with timestamps for sorting
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < logsList.size() && i < timestampsList.size(); i++) {
            entries.add(new LogEntry(timestampsList.get(i), logsList.get(i)));
        }
        
        // Sort by timestamp
        Collections.sort(entries, (a, b) -> {
            if (isLatestFirst) {
                // Latest first (descending)
                return Long.compare(b.timestamp, a.timestamp);
            } else {
                // Earliest first (ascending)
                return Long.compare(a.timestamp, b.timestamp);
            }
        });
        
        // Update lists
        logsList.clear();
        timestampsList.clear();
        for (LogEntry entry : entries) {
            timestampsList.add(entry.timestamp);
            logsList.add(entry.formatted);
        }
        
        // Update button text
        sortButton.setText(isLatestFirst ? "Latest First" : "Earliest First");
    }
    
    private void toggleSortOrder() {
        isLatestFirst = !isLatestFirst;
        sortLogs();
        logsAdapter.notifyDataSetChanged();
    }
    
    // Helper class to store log entry with timestamp
    private static class LogEntry {
        long timestamp;
        String formatted;
        
        LogEntry(long timestamp, String formatted) {
            this.timestamp = timestamp;
            this.formatted = formatted;
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
