buildscript {
    val kotlinVersion by extra("2.0.20")
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }

    dependencies {
        //classpath(libs.plugins.kotlin.gradle.plugin)
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.android.tools.build:gradle:8.6.1")
        classpath("com.google.gms:google-services:4.4.2")
        classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.2")
        classpath("com.google.firebase:perf-plugin:1.4.2")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.8.2.0")
        classpath("com.osacky.doctor:doctor-plugin:0.9.1")
//
//        classpath(libs.plugins.android.gradle.plugin)
//        classpath(libs.plugins.google.services)
//        classpath(libs.plugins.firebase.crashlytics.gradle)
//        classpath(libs.plugins.firebase.perf.plugin)
//        classpath(libs.plugins.android.junit5)
//        classpath(libs.plugins.doctor.plugin)
    }
}

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("kapt") version "2.0.20"
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
    id("org.jmailen.kotlinter") version "4.3.0"
//    kotlin("jvm") version libs.versions.kotlin.get()
//    kotlin("kapt") version libs.versions.kotlin.get()
//    alias(libs.plugins.ksp)
//    alias(libs.plugins.kotlinter)
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://jitpack.io") }
    }
}

//apply(libs.plugins.gradle.doctor)
//apply(plugin = "com.osacky.doctor")