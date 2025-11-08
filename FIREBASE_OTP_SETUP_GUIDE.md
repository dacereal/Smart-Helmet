# Firebase OTP Authentication Setup Guide

## 🔧 **Your App's SHA Fingerprints**

From the signing report, here are your app's fingerprints:

```
SHA1: 00:D8:74:89:DB:F2:64:02:5E:CE:37:D7:6A:47:33:14:B8:13:FB:E9
SHA-256: 6F:31:02:6F:73:2B:B6:4E:A3:6F:61:E9:2E:40:05:39:9D:51:F5:D9:21:61:F2:FD:D1:98:5B:88:0B:67:32:A6
```

## 📋 **Step-by-Step Firebase Configuration**

### **Step 1: Add SHA Fingerprints to Firebase Project**

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your Smart Helmet project
3. Go to **Project Settings** (gear icon)
4. Scroll down to **Your apps** section
5. Find your Android app and click on it
6. In the **SHA certificate fingerprints** section, click **Add fingerprint**
7. Add both fingerprints:
   - **SHA1**: `00:D8:74:89:DB:F2:64:02:5E:CE:37:D7:6A:47:33:14:B8:13:FB:E9`
   - **SHA-256**: `6F:31:02:6F:73:2B:B6:4E:A3:6F:61:E9:2E:40:05:39:9D:51:F5:D9:21:61:F2:FD:D1:98:5B:88:0B:67:32:A6`
8. Click **Save**

### **Step 2: Enable Phone Authentication**

1. In Firebase Console, go to **Authentication**
2. Click on **Sign-in method** tab
3. Find **Phone** and click on it
4. Toggle **Enable** to ON
5. Click **Save**

### **Step 3: Configure reCAPTCHA Enterprise (Optional but Recommended)**

1. In Firebase Console, go to **Authentication** > **Settings**
2. Scroll down to **reCAPTCHA Enterprise**
3. Click **Set up reCAPTCHA Enterprise**
4. Follow the setup wizard to configure reCAPTCHA
5. This will resolve the "No Recaptcha Enterprise siteKey configured" error

### **Step 4: Update Firebase Configuration**

1. Download the latest `google-services.json` file from Firebase Console
2. Replace the existing file in your project: `app/google-services.json`
3. Make sure the package name matches: `com.botsquad.smarthelmet`

### **Step 5: Update Dependencies (if needed)**

Check your `app/build.gradle` file and ensure you have the latest Firebase dependencies:

```gradle
dependencies {
    // Firebase BOM - use latest version
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
    
    // Firebase Auth
    implementation 'com.google.firebase:firebase-auth'
    
    // Firebase Database
    implementation 'com.google.firebase:firebase-database'
    
    // Google Play Services Auth
    implementation 'com.google.android.gms:play-services-auth:20.7.0'
}
```

### **Step 6: Test the Configuration**

1. Clean and rebuild your project:
   ```bash
   ./gradlew clean
   ./gradlew build
   ```

2. Run the app and test OTP functionality

## 🚨 **Common Issues and Solutions**

### **Issue 1: INVALID_CERT_HASH 400**
**Solution**: Make sure you've added the correct SHA fingerprints to Firebase Console

### **Issue 2: Play Integrity API Error**
**Solution**: This is common in debug mode. The app should still work with phone authentication even with this warning.

### **Issue 3: reCAPTCHA Enterprise Not Configured**
**Solution**: Either configure reCAPTCHA Enterprise or the app will fall back to standard reCAPTCHA

### **Issue 4: SMS Not Received**
**Solution**: 
- Check if phone number format is correct (+639928507766)
- Verify Firebase project has SMS quota
- Check if phone authentication is enabled in Firebase Console

## 🔍 **Testing Steps**

1. **Clean Build**: `./gradlew clean build`
2. **Install App**: Install the updated APK
3. **Test Registration**: Try registering with a valid phone number
4. **Check Logs**: Monitor logcat for any remaining errors
5. **Verify OTP**: Check if SMS is received and can be verified

## 📱 **Phone Number Format**

Make sure phone numbers are in international format:
- ✅ Correct: `+639928507766`
- ❌ Wrong: `09928507766` or `639928507766`

## 🔧 **Additional Configuration (if needed)**

If you're still having issues, you can also:

1. **Enable App Check** in Firebase Console
2. **Configure OAuth consent screen** in Google Cloud Console
3. **Set up domain verification** for your project

## 📞 **Support**

If you continue to have issues after following these steps:
1. Check Firebase Console for any error messages
2. Verify your Firebase project is properly configured
3. Ensure you're using the latest Firebase SDK versions
4. Test with a different phone number to rule out carrier issues

## 🎯 **Expected Results**

After completing these steps:
- ✅ OTP SMS should be sent successfully
- ✅ No more "INVALID_CERT_HASH" errors
- ✅ No more "reCAPTCHA Enterprise" errors
- ✅ Phone authentication should work smoothly
- ✅ No more ANRs during OTP verification
