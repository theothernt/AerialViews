buildscript {
    val kotlinVersion by extra("2.0.20")
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.android.tools.build:gradle:8.6.1")
        classpath("com.google.gms:google-services:4.4.2")
        classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.2")
        classpath("com.google.firebase:perf-plugin:1.4.2")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.8.2.0")
        classpath("com.osacky.doctor:doctor-plugin:0.9.1")
    }
}

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("kapt") version "2.0.20"
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
    id("org.jmailen.kotlinter") version "4.3.0"
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://jitpack.io") }
    }
}

apply(plugin = "com.osacky.doctor")