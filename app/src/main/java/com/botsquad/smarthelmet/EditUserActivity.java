package com.botsquad.smarthelmet;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class EditUserActivity extends AppCompatActivity {

    private static final String TAG = "EditUserActivity";

    private TextInputEditText editTextEditFirstName, editTextEditLastName, editTextEditEmail, editTextEditContactNumber;
    private Button btnSaveChanges, btnDeleteUser;

    private DatabaseReference mDatabase;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_user);

        // Get user ID from intent
        userId = getIntent().getStringExtra("USER_ID");

        if (userId == null) {
            Toast.makeText(this, "User ID not provided.", Toast.LENGTH_SHORT).show();
            finish(); // Close the activity if no user ID is provided
            return;
        }

        // Initialize Firebase Database
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize views
        editTextEditFirstName = findViewById(R.id.editTextEditFirstName);
        editTextEditLastName = findViewById(R.id.editTextEditLastName);
        editTextEditEmail = findViewById(R.id.editTextEditEmail);
        editTextEditContactNumber = findViewById(R.id.editTextEditContactNumber);
        btnSaveChanges = findViewById(R.id.btnSaveChanges);
        btnDeleteUser = findViewById(R.id.btnDeleteUser);

        // Load user data
        loadUserData();

        // Set click listeners
        btnSaveChanges.setOnClickListener(v -> saveChanges());
        btnDeleteUser.setOnClickListener(v -> deleteUser());
    }

    private void loadUserData() {
        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    User user = dataSnapshot.getValue(User.class);
                    if (user != null) {
                        editTextEditFirstName.setText(user.getFirstName());
                        editTextEditLastName.setText(user.getLastName());
                        editTextEditEmail.setText(user.getEmail()); // Email is read-only
                        editTextEditContactNumber.setText(user.getContactNumber());
                    }
                } else {
                    Log.w(TAG, "No user data found for user ID: " + userId);
                    Toast.makeText(EditUserActivity.this, "User data not found.", Toast.LENGTH_SHORT).show();
                    finish(); // Close activity if user not found
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w(TAG, "loadUserData:onCancelled", databaseError.toException());
                Toast.makeText(EditUserActivity.this, "Failed to load user data.", Toast.LENGTH_SHORT).show();
                finish(); // Close activity on error
            }
        });
    }

    private void saveChanges() {
        String firstName = editTextEditFirstName.getText().toString().trim();
        String lastName = editTextEditLastName.getText().toString().trim();
        String contactNumber = editTextEditContactNumber.getText().toString().trim();

        if (firstName.isEmpty() || lastName.isEmpty() || contactNumber.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);
        updates.put("contactNumber", contactNumber);

        mDatabase.child("users").child(userId).updateChildren(updates)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(EditUserActivity.this, "User updated successfully", Toast.LENGTH_SHORT).show();
                            finish(); // Go back to the user list after saving
                        } else {
                            Toast.makeText(EditUserActivity.this, "Failed to update user", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Failed to update user", task.getException());
                        }
                    }
                });
    }

    private void deleteUser() {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete this user account?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Delete user from Firebase Realtime Database
                        mDatabase.child("users").child(userId).removeValue()
                                .addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        if (task.isSuccessful()) {
                                            Toast.makeText(EditUserActivity.this, "User deleted successfully", Toast.LENGTH_SHORT).show();
                                            finish(); // Go back to the user list after deletion
                                        } else {
                                            Toast.makeText(EditUserActivity.this, "Failed to delete user", Toast.LENGTH_SHORT).show();
                                            Log.e(TAG, "Failed to delete user", task.getException());
                                        }
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
} 