package com.botsquad.smarthelmet;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Login extends AppCompatActivity {

    TextInputEditText editTextEmail, editTextPassword;
    Button buttonLogin;
    FirebaseAuth mAuth;
    ProgressBar progressBar;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("Login", "=== Login.onCreate() STARTED ===");
        
        try {
            Log.d("Login", "Step 1: Calling super.onCreate()");
            super.onCreate(savedInstanceState);
            Log.d("Login", "Step 2: super.onCreate() completed");
            
            Log.d("Login", "Step 3: Enabling EdgeToEdge");
            EdgeToEdge.enable(this);
            Log.d("Login", "Step 4: EdgeToEdge enabled");
            
            Log.d("Login", "Step 5: Setting content view");
            setContentView(R.layout.activity_login);
            Log.d("Login", "Step 6: Content view set");

            // Firebase is initialized in MyApplication, so this should be safe
            Log.d("Login", "Step 7: Getting Firebase Auth instance");
            try {
                mAuth = FirebaseAuth.getInstance();
                Log.d("Login", "Step 8: Firebase Auth instance obtained successfully");
            } catch (Exception e) {
                Log.e("Login", "Step 7-8 ERROR: Firebase Auth initialization failed", e);
                // Show error to user
                Toast.makeText(this, "Authentication service unavailable. Please restart the app.", Toast.LENGTH_LONG).show();
            }
            
            Log.d("Login", "Step 9: Initializing views");
            editTextEmail = findViewById(R.id.email);
            editTextPassword = findViewById(R.id.password);
            buttonLogin = findViewById(R.id.btn_login);
            progressBar = findViewById(R.id.progressBar);
            textView = findViewById(R.id.registerNow);
            Log.d("Login", "Step 10: Views initialized");

            Log.d("Login", "Step 11: Setting up click listeners");
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getApplicationContext(), Register.class);
                    startActivity(intent);
                    finish();
                }
            });

            buttonLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    progressBar.setVisibility(View.VISIBLE);
                    buttonLogin.setEnabled(false); // Disable button to prevent multiple clicks
                    
                    String email, password;
                    email = String.valueOf(editTextEmail.getText()).trim();
                    password = String.valueOf(editTextPassword.getText()).trim();

                    if(TextUtils.isEmpty(email)) {
                        Toast.makeText(Login.this, "Enter email or username", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        buttonLogin.setEnabled(true);
                        return;
                    }

                    if(TextUtils.isEmpty(password)) {
                        Toast.makeText(Login.this, "Enter password", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        buttonLogin.setEnabled(true);
                        return;
                    }

                    // Check for admin credentials
                    if (email.equals("admin") && password.equals("botsquad")) {
                        Toast.makeText(Login.this, "Admin Login Successful", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(getApplicationContext(), AdminDashboard.class);
                        startActivity(intent);
                        finish();
                    } else {
                        // Perform Firebase login
                        if (mAuth == null) {
                            Toast.makeText(Login.this, "Authentication service not ready. Please restart the app.", Toast.LENGTH_LONG).show();
                            progressBar.setVisibility(View.GONE);
                            buttonLogin.setEnabled(true);
                        } else {
                            performLogin(email, password);
                        }
                    }
                }
            });
            
            Log.d("Login", "=== Login.onCreate() COMPLETED SUCCESSFULLY ===");
            
        } catch (Throwable e) {
            Log.e("Login", "=== CRITICAL ERROR in Login.onCreate() ===", e);
            Log.e("Login", "Error type: " + e.getClass().getName());
            Log.e("Login", "Error message: " + e.getMessage());
            if (e.getCause() != null) {
                Log.e("Login", "Caused by: " + e.getCause(), e.getCause());
            }
            // Show error to user
            Toast.makeText(this, "App initialization failed. Check logs for details.", Toast.LENGTH_LONG).show();
            // Re-throw to see the crash in logcat
            throw new RuntimeException("Login onCreate failed", e);
        }
    }

    private void performLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);
                        buttonLogin.setEnabled(true);
                        
                        if (task.isSuccessful()) {
                            Toast.makeText(Login.this, "Login Successful",
                                    Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(getApplicationContext(), Dashboard.class);
                            startActivity(intent);
                            finish();
                        } else {
                            String errorMessage = "Authentication failed.";
                            if (task.getException() != null) {
                                errorMessage = task.getException().getMessage();
                            }
                            Toast.makeText(Login.this, errorMessage,
                                    Toast.LENGTH_LONG).show();
                            Log.e("Login", "Authentication failed: " + task.getException());
                        }
                    }
                });
    }
}