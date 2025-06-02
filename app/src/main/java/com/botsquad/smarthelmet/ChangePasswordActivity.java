package com.botsquad.smarthelmet;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private static final String TAG = "ChangePasswordActivity";

    private TextInputEditText editTextCurrentPassword, editTextNewPassword, editTextConfirmNewPassword;
    private Button btnChangePassword, btnCancel;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        mAuth = FirebaseAuth.getInstance();

        editTextCurrentPassword = findViewById(R.id.editTextCurrentPassword);
        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmNewPassword = findViewById(R.id.editTextConfirmNewPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnCancel = findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Close the activity and return to the previous screen
            }
        });

        btnChangePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changePassword();
            }
        });
    }

    private void changePassword() {
        String currentPassword = editTextCurrentPassword.getText().toString().trim();
        String newPassword = editTextNewPassword.getText().toString().trim();
        String confirmNewPassword = editTextConfirmNewPassword.getText().toString().trim();

        if (TextUtils.isEmpty(currentPassword)) {
            editTextCurrentPassword.setError("Required.");
            return;
        }
        if (TextUtils.isEmpty(newPassword)) {
            editTextNewPassword.setError("Required.");
            return;
        }
        if (TextUtils.isEmpty(confirmNewPassword)) {
            editTextConfirmNewPassword.setError("Required.");
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            editTextConfirmNewPassword.setError("Passwords do not match.");
            Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

            user.reauthenticate(credential)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "User re-authenticated.");
                                user.updatePassword(newPassword)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (task.isSuccessful()) {
                                                    Log.d(TAG, "User password updated.");
                                                    Toast.makeText(ChangePasswordActivity.this, "Password updated successfully.", Toast.LENGTH_SHORT).show();
                                                    finish(); // Close the activity after successful update
                                                } else {
                                                    Toast.makeText(ChangePasswordActivity.this, "Failed to update password.", Toast.LENGTH_SHORT).show();
                                                    Log.e(TAG, "Error updating password", task.getException());
                                                }
                                            }
                                        });
                            } else {
                                Toast.makeText(ChangePasswordActivity.this, "Authentication failed. Check current password.", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Error re-authenticating user", task.getException());
                            }
                        }
                    });
        } else {
            Toast.makeText(this, "User not logged in or email not available.", Toast.LENGTH_SHORT).show();
        }
    }
} 