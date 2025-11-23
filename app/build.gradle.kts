plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.nextgen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.nextgen"
        minSdk = 26
        targetSdk = 36
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
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.zxing:core:3.5.2")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")


    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // ✅ Firebase BOM - ensures consistent versions
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-database")

    // ✅ Room (for local database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation(libs.tensorflow.lite.metadata)
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // ✅ PDF and file utilities
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ✅ Image libraries
    implementation("com.squareup.picasso:picasso:2.71828")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")

    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")


    implementation ("com.google.android.material:material:1.11.0")

        // RecyclerView
    implementation ("androidx.recyclerview:recyclerview:1.3.2")

        // CoordinatorLayout
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")


    // ✅ AndroidX UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // ✅ Add TensorFlow Lite dependencies
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.0")
    implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.0")
}
