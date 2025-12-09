// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("com.android.library") version "8.11.1" apply false
}

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.11.1")
        classpath("com.google.gms:google-services:4.4.0")
    }
}

