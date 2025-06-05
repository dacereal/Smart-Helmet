package com.botsquad.smarthelmet;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class UserManagementActivity extends AppCompatActivity implements UserAdapter.OnItemClickListener {

    private static final String TAG = "UserManagementActivity";

    private RecyclerView recyclerViewUsers;
    private UserAdapter userAdapter;
    private List<User> userList;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        // Enable back button in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("User Accounts"); // Set title
        }

        // Initialize Firebase Database
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize RecyclerView
        recyclerViewUsers = findViewById(R.id.recyclerViewUsers);
        recyclerViewUsers.setLayoutManager(new LinearLayoutManager(this));

        // Initialize user list and adapter
        userList = new ArrayList<>();
        userAdapter = new UserAdapter(userList);
        recyclerViewUsers.setAdapter(userAdapter);

        // Set item click listener
        userAdapter.setOnItemClickListener(this);

        // Fetch users from Firebase
        fetchUsers();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Close this activity and return to the previous one
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void fetchUsers() {
        mDatabase.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "DataSnapshot received: " + dataSnapshot.toString());
                userList.clear(); // Clear previous list
                if (dataSnapshot.exists()) {
                    Log.d(TAG, "DataSnapshot exists. Children count: " + dataSnapshot.getChildrenCount());
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            user.setUserId(snapshot.getKey()); // Set the user ID from the snapshot key
                            userList.add(user);
                            Log.d(TAG, "Fetched user: " + user.getFullName() + " (ID: " + user.getUserId() + ")");
                        } else {
                            Log.w(TAG, "Snapshot value is null for key: " + snapshot.getKey());
                        }
                    }
                    userAdapter.notifyDataSetChanged(); // Notify adapter of data change
                    Log.d(TAG, "User list updated. Total users: " + userList.size());
                } else {
                    Log.d(TAG, "DataSnapshot does not exist.");
                    userAdapter.notifyDataSetChanged(); // Notify adapter even if list is empty
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w(TAG, "loadUsers:onCancelled", databaseError.toException());
                Toast.makeText(UserManagementActivity.this, "Failed to load users.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onItemClick(User user) {
        // Handle user item click - navigate to EditUserActivity
        if (user != null) {
            Intent intent = new Intent(UserManagementActivity.this, EditUserActivity.class);
            intent.putExtra("USER_ID", user.getUserId());
            startActivity(intent);
        }
    }
} 