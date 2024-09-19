buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
        classpath("com.android.tools.build:gradle:8.6.1")
        //classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.2")
        //classpath("com.google.firebase:perf-plugin:1.4.2")
    }
}

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    kotlin("kapt") version libs.versions.kotlin.get()
    //alias(libs.plugins.google.services) apply false
    //id("com.google.gms.google-services") version "4.4.2"
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.junit5) apply false
    alias(libs.plugins.kotlinter.gradle) apply false
    alias(libs.plugins.gradle.doctor)
}