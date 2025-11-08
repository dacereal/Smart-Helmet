plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.botsquad.smarthelmet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.botsquad.smarthelmet"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Add 16KB alignment support for Android 15+ compatibility
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }
    
    // Enable 16KB alignment support
    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Configure test task to show output
tasks.withType<Test> {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        showExceptions = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-auth-ktx:23.2.1")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    // TensorFlow Lite for drowsiness detection (updated for 16KB alignment support)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    // implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1") // Removed to fix crash
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.6.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("androidx.test:core:1.5.0")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}