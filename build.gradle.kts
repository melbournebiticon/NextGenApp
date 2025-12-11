// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.google.gms.google-services") version "4.4.4" apply false

    // Pin AGP to the supported version
    id("com.android.application") version "8.12.2" apply false
    id("com.android.library") version "8.12.2" apply false

    // If you still need other alias/plugins from the version catalog keep them or add them explicitly here.
    // Remove alias(libs.plugins.android.application) so we don't pick up 8.13.0 from the version catalog.
}