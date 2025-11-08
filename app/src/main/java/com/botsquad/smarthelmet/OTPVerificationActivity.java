package com.botsquad.smarthelmet;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OTPVerificationActivity extends AppCompatActivity {
    
    private TextInputEditText editTextOTP;
    private Button buttonVerify;
    private Button buttonResend;
    private ProgressBar progressBar;
    private TextView textViewPhone;
    private TextView textViewTimer;
    
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String verificationId;
    private String phoneNumber;
    private String firstName, lastName, email, password, contact;
    
    // Timer for resend button
    private int resendTimer = 60;
    private boolean isTimerRunning = false;
    
    // Background executor for Firebase operations
    private ExecutorService firebaseExecutor;
    
    // Retry mechanism
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3; // Restore normal retry count
    private boolean shouldSkipFirebaseAuth = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);
        
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        // Initialize background executor for Firebase operations
        firebaseExecutor = Executors.newSingleThreadExecutor();
        
        // Get data from previous activity
        Intent intent = getIntent();
        phoneNumber = intent.getStringExtra("phoneNumber");
        firstName = intent.getStringExtra("firstName");
        lastName = intent.getStringExtra("lastName");
        email = intent.getStringExtra("email");
        password = intent.getStringExtra("password");
        contact = intent.getStringExtra("contact");
        
        // Initialize views
        editTextOTP = findViewById(R.id.editTextOTP);
        buttonVerify = findViewById(R.id.buttonVerify);
        buttonResend = findViewById(R.id.buttonResend);
        progressBar = findViewById(R.id.progressBar);
        textViewPhone = findViewById(R.id.textViewPhone);
        textViewTimer = findViewById(R.id.textViewTimer);
        
        // Display phone number
        textViewPhone.setText("Verification code sent to: " + phoneNumber);
        
        // Debug: Log received data
        android.util.Log.d("OTPVerification", "Received phone number: " + phoneNumber);
        android.util.Log.d("OTPVerification", "Received firstName: " + firstName);
        android.util.Log.d("OTPVerification", "Received email: " + email);
        
        // Validate phone number format
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Toast.makeText(this, "Error: Phone number is empty", Toast.LENGTH_LONG).show();
            android.util.Log.e("OTPVerification", "Phone number is null or empty");
            finish();
            return;
        }
        
        // Validate other required fields
        if (firstName == null || lastName == null || email == null || password == null) {
            Toast.makeText(this, "Error: Missing required information", Toast.LENGTH_LONG).show();
            android.util.Log.e("OTPVerification", "Missing required fields - firstName: " + firstName + ", lastName: " + lastName + ", email: " + email);
            finish();
            return;
        }
        
        // Send OTP with retry mechanism
        sendOTPWithRetry();
        
        // Set up click listeners
        buttonVerify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verifyOTP();
            }
        });
        
        buttonResend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resendOTP();
            }
        });
    }
    
    private void sendOTPWithRetry() {
        if (retryCount < MAX_RETRIES) {
            sendOTP();
        } else {
            // Show fallback option after max retries
            showFallbackOption();
        }
    }
    
    private void sendOTP() {
        // Update UI immediately on main thread
        runOnUiThread(() -> {
        progressBar.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Sending OTP...", Toast.LENGTH_SHORT).show();
        });
        
        // Debug: Log the phone number and Firebase configuration
        android.util.Log.d("OTPVerification", "Attempting to send OTP to: " + phoneNumber);
        android.util.Log.d("OTPVerification", "Firebase Auth instance: " + (mAuth != null ? "Initialized" : "Null"));
        android.util.Log.d("OTPVerification", "Retry attempt: " + (retryCount + 1) + "/" + MAX_RETRIES);
        
        // Remove timeout - let Firebase handle its own timing
        
        // Firebase operations need to run on main thread for reCAPTCHA to work properly
        // Add timeout to prevent ANR from Play Integrity issues
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(30L, TimeUnit.SECONDS) // Reduced timeout to fail faster
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        android.util.Log.d("OTPVerification", "Auto-verification completed");
                        // Auto-verification completed
                        signInWithPhoneAuthCredential(credential);
                    }
                    
                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        progressBar.setVisibility(View.GONE);
                        android.util.Log.e("OTPVerification", "Verification failed: " + e.getMessage(), e);
                        
                                // Handle specific Firebase errors with better user messaging
                                String errorMessage = "Verification failed. Please try again.";
                                String errorCode = "";
                                
                        if (e.getMessage() != null) {
                                    android.util.Log.e("OTPVerification", "Firebase error details: " + e.getMessage());
                                    
                            if (e.getMessage().contains("BILLING_NOT_ENABLED")) {
                                        errorMessage = "Phone verification is not enabled in Firebase project. Please contact support.";
                                        errorCode = "BILLING_NOT_ENABLED";
                            } else if (e.getMessage().contains("INVALID_CERT_HASH")) {
                                        errorMessage = "App certificate hash not registered in Firebase. Please add SHA fingerprints to Firebase Console.";
                                        errorCode = "INVALID_CERT_HASH";
                                    } else if (e.getMessage().contains("app identifier")) {
                                        errorMessage = "App verification failed. Please check Firebase configuration.";
                                        errorCode = "APP_IDENTIFIER";
                                    } else if (e.getMessage().contains("reCAPTCHA")) {
                                        errorMessage = "reCAPTCHA configuration issue. Please configure reCAPTCHA in Firebase Console.";
                                        errorCode = "RECAPTCHA";
                                    } else if (e.getMessage().contains("QUOTA_EXCEEDED")) {
                                        errorMessage = "SMS quota exceeded. Please try again later or contact support.";
                                        errorCode = "QUOTA_EXCEEDED";
                                    } else if (e.getMessage().contains("INVALID_PHONE_NUMBER")) {
                                        errorMessage = "Invalid phone number format. Please use international format (+639928507766).";
                                        errorCode = "INVALID_PHONE_NUMBER";
                                    } else if (e.getMessage().contains("TOO_MANY_REQUESTS")) {
                                        errorMessage = "Too many requests. Please wait before trying again.";
                                        errorCode = "TOO_MANY_REQUESTS";
                            } else {
                                errorMessage = "Verification failed: " + e.getMessage();
                                        errorCode = "UNKNOWN";
                                    }
                                }
                                
                                android.util.Log.e("OTPVerification", "Error code: " + errorCode + " - " + errorMessage);
                        
                                final String finalErrorMessage = errorMessage;
                                Toast.makeText(OTPVerificationActivity.this, finalErrorMessage, Toast.LENGTH_LONG).show();
                                
                                // Increment retry count and retry if possible
                                retryCount++;
                                if (retryCount < MAX_RETRIES) {
                                    android.os.Handler retryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                                    retryHandler.postDelayed(() -> {
                                        android.util.Log.d("OTPVerification", "Retrying OTP send (attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ")");
                                        sendOTP();
                                    }, 2000); // Retry after 2 seconds
                                } else {
                                    showFallbackOption();
                                }
                    }
                    
                    @Override
                    public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(OTPVerificationActivity.this, "OTP sent successfully", Toast.LENGTH_SHORT).show();
                        android.util.Log.d("OTPVerification", "OTP sent successfully, verificationId: " + verificationId);
                        OTPVerificationActivity.this.verificationId = verificationId;
                        startResendTimer();
                    }
                })
                .build();
        
        try {
            PhoneAuthProvider.verifyPhoneNumber(options);
            android.util.Log.d("OTPVerification", "PhoneAuthProvider.verifyPhoneNumber() called successfully");
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error sending OTP: " + e.getMessage(), Toast.LENGTH_LONG).show();
            android.util.Log.e("OTPVerification", "Exception in verifyPhoneNumber: " + e.getMessage(), e);
        }
    }
    
    private void verifyOTP() {
        String otp = editTextOTP.getText().toString().trim();
        
        if (TextUtils.isEmpty(otp)) {
            Toast.makeText(this, "Please enter OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (otp.length() != 6) {
            Toast.makeText(this, "Please enter valid 6-digit OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update UI immediately
        progressBar.setVisibility(View.VISIBLE);
        
        // Move verification to background thread
        firebaseExecutor.execute(() -> {
            try {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        signInWithPhoneAuthCredential(credential);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(OTPVerificationActivity.this, "Error verifying OTP: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                android.util.Log.e("OTPVerification", "Error in verifyOTP: " + e.getMessage(), e);
            }
        });
    }
    
    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);
                        
                        if (task.isSuccessful()) {
                            // Phone verification successful, now create email account
                            createEmailAccount();
                        } else {
                            Toast.makeText(OTPVerificationActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
    
    private void createEmailAccount() {
        // Move account creation to background thread
        firebaseExecutor.execute(() -> {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Get the user ID
                            String userId = mAuth.getCurrentUser().getUid();
                            
                            // Create user data map
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("firstName", firstName);
                            userData.put("lastName", lastName);
                            userData.put("email", email);
                            userData.put("contactNumber", contact);
                            userData.put("phoneNumber", phoneNumber);
                            userData.put("isPhoneVerified", true);
                            
                            // Save user data to Firebase Database
                            mDatabase.child("users").child(userId).setValue(userData)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                                runOnUiThread(() -> {
                                                    progressBar.setVisibility(View.GONE);
                                            if (task.isSuccessful()) {
                                                Toast.makeText(OTPVerificationActivity.this, "Account created successfully!", 
                                                        Toast.LENGTH_SHORT).show();
                                                // Navigate to dashboard
                                                Intent intent = new Intent(getApplicationContext(), Dashboard.class);
                                                startActivity(intent);
                                                finish();
                                            } else {
                                                Toast.makeText(OTPVerificationActivity.this, "Failed to save user data.", 
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                                });
                                        }
                                    });
                        } else {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                            Toast.makeText(OTPVerificationActivity.this, "Failed to create account: " + task.getException().getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });
                });
    }
    
    private void resendOTP() {
        if (!isTimerRunning) {
            sendOTPWithRetry();
        }
    }
    
    private void showFallbackOption() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("OTP Verification Failed")
                    .setMessage("Unable to send OTP after multiple attempts. This may be due to Firebase configuration issues. Would you like to continue without phone verification?")
                    .setPositiveButton("Continue Without Phone Verification", (dialog, which) -> {
                        // Skip phone verification and create account directly
                        createAccountWithoutPhoneVerification();
                    })
                    .setNegativeButton("Try Again", (dialog, which) -> {
                        // Reset retry count and try again
                        retryCount = 0;
                        sendOTPWithRetry();
                    })
                    .setNeutralButton("Cancel", (dialog, which) -> {
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        });
    }
    
    private void createAccountWithoutPhoneVerification() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Creating account without phone verification...", Toast.LENGTH_SHORT).show();
        });
        
        // Create account directly with email/password
        firebaseExecutor.execute(() -> {
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                // Get the user ID
                                String userId = mAuth.getCurrentUser().getUid();
                                
                                // Create user data map
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("firstName", firstName);
                                userData.put("lastName", lastName);
                                userData.put("email", email);
                                userData.put("contactNumber", contact);
                                userData.put("phoneNumber", phoneNumber);
                                userData.put("isPhoneVerified", false); // Mark as not verified
                                
                                // Save user data to Firebase Database
                                mDatabase.child("users").child(userId).setValue(userData)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                runOnUiThread(() -> {
                                                    progressBar.setVisibility(View.GONE);
                                                    if (task.isSuccessful()) {
                                                        Toast.makeText(OTPVerificationActivity.this, "Account created successfully! (Phone verification skipped)", 
                                                                Toast.LENGTH_LONG).show();
                                                        // Navigate to dashboard
                                                        Intent intent = new Intent(getApplicationContext(), Dashboard.class);
                                                        startActivity(intent);
                                                        finish();
                                                    } else {
                                                        Toast.makeText(OTPVerificationActivity.this, "Failed to save user data.", 
                                                                Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            }
                                        });
                            } else {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(OTPVerificationActivity.this, "Failed to create account: " + task.getException().getMessage(), 
                                            Toast.LENGTH_LONG).show();
                                });
                            }
                        }
                    });
        });
    }
    
    private void startResendTimer() {
        isTimerRunning = true;
        resendTimer = 60;
        buttonResend.setEnabled(false);
        
        // Use Handler for better performance than Thread + runOnUiThread
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        Runnable timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (resendTimer > 0 && isTimerRunning) {
                    textViewTimer.setText("Resend OTP in " + resendTimer + " seconds");
                    resendTimer--;
                    handler.postDelayed(this, 1000);
                } else {
                    isTimerRunning = false;
                    buttonResend.setEnabled(true);
                    textViewTimer.setText("");
                }
            }
        };
        
        handler.post(timerRunnable);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTimerRunning = false;
        
        // Clean up background executor
        if (firebaseExecutor != null) {
            firebaseExecutor.shutdown();
            try {
                if (!firebaseExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    firebaseExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                firebaseExecutor.shutdownNow();
            }
            firebaseExecutor = null;
        }
    }
}
