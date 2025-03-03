plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kapt) apply false

    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false

    alias(libs.plugins.kotlinter.gradle) apply false
    alias(libs.plugins.gradle.doctor)

    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

