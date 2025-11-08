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
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Initialize Firebase in background thread to prevent ANR
        new Thread(() -> {
            try {
                mAuth = FirebaseAuth.getInstance();
                Log.d("Login", "Firebase Auth initialized");
            } catch (Exception e) {
                Log.e("Login", "Firebase initialization failed: " + e.getMessage(), e);
            }
        }).start();

        editTextEmail = findViewById(R.id.email);
        editTextPassword = findViewById(R.id.password);
        buttonLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progressBar);
        textView = findViewById(R.id.registerNow);
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
                    // Wait for Firebase to initialize if not ready
                    if (mAuth == null) {
                        new Thread(() -> {
                            while (mAuth == null) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    break;
                                }
                            }
                            runOnUiThread(() -> {
                                if (mAuth != null) {
                                    performLogin(email, password);
                                } else {
                                    Toast.makeText(Login.this, "Authentication service not ready", Toast.LENGTH_SHORT).show();
                                    progressBar.setVisibility(View.GONE);
                                    buttonLogin.setEnabled(true);
                                }
                            });
                        }).start();
                    } else {
                        performLogin(email, password);
                    }
                }
            }
        });
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