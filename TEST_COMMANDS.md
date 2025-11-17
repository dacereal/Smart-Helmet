# Running Tests Individually

You can run each test case individually using the commands below. This allows you to see the result for each test separately.

## Quick Scripts

### Windows (PowerShell/CMD)
Run the batch file:
```bash
run_tests_individually.bat
```

### Linux/Mac
Make the script executable and run:
```bash
chmod +x run_tests_individually.sh
./run_tests_individually.sh
```

## Individual Test Commands

### Case-001: Register with valid input
```bash
./gradlew test --tests "com.botsquad.smarthelmet.RegisterTest.testCase001_RegisterWithValidInput"
```

### Case-002: Login with valid credentials
```bash
./gradlew test --tests "com.botsquad.smarthelmet.LoginTest.testCase002_LoginWithValidCredentials"
```

### Case-003: Login validation with empty fields
```bash
./gradlew test --tests "com.botsquad.smarthelmet.LoginTest.testCase003_LoginValidationWithEmptyFields"
```

### Case-004: Open Dashboard after login
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest.testCase004_DashboardVisibilityAfterLogin"
```

### Case-005: Automatic Pi Scanning
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DevicePairingTest.testCase005_AutomaticPiScanning"
```

### Case-006: Select Prototype
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DevicePairingTest.testCase006_SelectDeviceAndPair"
```

### Case-007: Connected to SmartHelmet device
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest.testCase007_DeviceConnectionSuccess"
```

### Case-008: Live Camera Feed Start
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest.testCase008_LiveCameraFeedStart"
```

### Case-009: Drowsiness True
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest.testCase009_DrowsinessTrue"
```

### Case-010: Drowsiness False
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest.testCase010_DrowsinessFalse"
```

### Case-011: Events Count
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest.testCase011_EventsCountIncrements"
```

### Case-012: Last Event Time
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest.testCase012_LastEventTimeUpdate"
```

### Case-013: Alarm + Vibrate
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest.testCase013_AlarmAndVibrateOnDrowsy"
```

### Case-014: Alert Tones
```bash
./gradlew test --tests "com.botsquad.smarthelmet.AlertTonesTest.testCase014_OpenAlertTones"
```

### Case-015: Change Password
```bash
./gradlew test --tests "com.botsquad.smarthelmet.ChangePasswordTest.testCase015_ChangePassword"
```

### Case-016: View Profile
```bash
./gradlew test --tests "com.botsquad.smarthelmet.ProfileTest.testCase016_ViewProfile"
```

### Case-017: Update Profile
```bash
./gradlew test --tests "com.botsquad.smarthelmet.ProfileTest.testCase017_UpdateProfile"
```

### Case-018: Auto-redirect if authenticated
```bash
./gradlew test --tests "com.botsquad.smarthelmet.LoginTest.testCase018_AutoRedirectIfAuthenticated"
```

### Case-019: Logout
```bash
./gradlew test --tests "com.botsquad.smarthelmet.SessionTest.testCase019_Logout"
```

### Case-020: View List (Drowsiness History)
```bash
./gradlew test --tests "com.botsquad.smarthelmet.DrowsinessLogsTest.testCase020_OpenDrowsinessHistory"
```

## Running All Tests in a Specific Class

You can also run all tests in a specific test class:

```bash
# Run all Register tests
./gradlew test --tests "com.botsquad.smarthelmet.RegisterTest"

# Run all Login tests
./gradlew test --tests "com.botsquad.smarthelmet.LoginTest"

# Run all Dashboard tests
./gradlew test --tests "com.botsquad.smarthelmet.DashboardTest"
```

## Viewing Test Results

After running a test, you can view detailed results in:
- **HTML Report**: `app/build/reports/tests/testDebugUnitTest/index.html`
- **Console Output**: Check the terminal output for pass/fail status












