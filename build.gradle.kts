plugins {
    //kotlin("jvm") version libs.versions.kotlin.get()
    kotlin("kapt") version libs.versions.kotlin.get()
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.junit5) apply false
    alias(libs.plugins.kotlinter.gradle) apply false
    alias(libs.plugins.gradle.doctor)
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
}