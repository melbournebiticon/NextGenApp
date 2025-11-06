plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.nextgen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.nextgen"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // ✅ Ensure .tflite model is not excluded or compressed
    packaging {
        resources {
            // Remove exclude if you want to use yamnet.tflite or other models
            excludes -= "**/yamnet.tflite"
        }
    }

    // ✅ Allow .tflite to be packed properly
    aaptOptions {
        noCompress += "tflite"
    }

    // ✅ Keep model binding for ML models
    buildFeatures {
        mlModelBinding = true
    }
}

dependencies {
    // ✅ Firebase BOM - ensures consistent versions
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-database")

    // ✅ Room (for local database)
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // ✅ TensorFlow Lite dependencies - COMPLETE SET
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.0")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.0") // ✅ ADDED THIS MISSING DEPENDENCY
    implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.0")

    // ✅ PDF and file utilities
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ✅ Image libraries
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")

    // ✅ Office file processing
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // ✅ Charts library
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ✅ UI components
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    // ✅ AndroidX UI
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ✅ Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}