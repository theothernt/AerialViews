import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    kotlin("kapt")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinter.gradle)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
}

fun loadProperties(fileName: String): Properties {
    val properties = Properties()
    val propertiesFile = rootProject.file("signing/$fileName")
    if (propertiesFile.exists()) {
        properties.load(FileInputStream(propertiesFile))
    }
    return properties
}

android {
    namespace = "com.neilturner.aerialviews"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neilturner.aerialviews"
        minSdk = 22 // to support Fire OS 5, Android v5.1, Lvl 22
        targetSdk = 35
        versionCode = 25
        versionName = "1.7.4"

        manifestPlaceholders["analyticsCollectionEnabled"] = false
        manifestPlaceholders["crashlyticsCollectionEnabled"] = false
        manifestPlaceholders["performanceCollectionEnabled"] = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }

    // App bundle (not APK) should contain all languages so 'locale switch'
    // feature works on Play Store and Amazon Appstore builds
    // https://stackoverflow.com/a/54862243/247257
    bundle {
        language {
            enableSplit = false
        }
    }

    kotlin {
        sourceSets.configureEach {
            languageSettings.languageVersion = "2.0"
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "BUILD_TIME", "\"${System.currentTimeMillis()}\"")

            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
            // isPseudoLocalesEnabled = true
        }
        getByName("release") {
            buildConfigField("String", "BUILD_TIME", "\"${System.currentTimeMillis()}\"")

            isMinifyEnabled = true
            isShrinkResources = true
            // isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            manifestPlaceholders["analyticsCollectionEnabled"] = true
            manifestPlaceholders["crashlyticsCollectionEnabled"] = true
            manifestPlaceholders["performanceCollectionEnabled"] = true
        }
    }

    signingConfigs {
        create("release") {
            val releaseProps = loadProperties("release.properties")
            storeFile = releaseProps["storeFile"]?.let { file(it) }
            storePassword = releaseProps["storePassword"] as String?
            keyAlias = releaseProps["keyAlias"] as String?
            keyPassword = releaseProps["keyPassword"] as String?
        }
        create("legacy") {
            val releaseProps = loadProperties("legacy.properties")
            storeFile = releaseProps["storeFile"]?.let { file(it) }
            storePassword = releaseProps["storePassword"] as String?
            keyAlias = releaseProps["keyAlias"] as String?
            keyPassword = releaseProps["keyPassword"] as String?
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("github") {
            signingConfig = signingConfigs.getByName("legacy")
            dimension = "version"
        }
        create("beta") {
            signingConfig = signingConfigs.getByName("legacy")
            dimension = "version"
            isDefault = true
            versionNameSuffix = "-beta1"
        }
        create("googleplay") {
            signingConfig = signingConfigs.getByName("release")
            dimension = "version"
        }
        create("amazon") {
            signingConfig = signingConfigs.getByName("release")
            dimension = "version"
        }
        create("fdroid") {
            signingConfig = signingConfigs.getByName("release")
            dimension = "version"
        }
    }

    // Using this method https://stackoverflow.com/a/30548238/247257
    sourceSets {
        getByName("github").java.srcDir("src/common/java")
        getByName("beta").java.srcDir("src/common/java")
        getByName("googleplay").java.srcDir("src/common/java")
        getByName("amazon").java.srcDir("src/common/java")
        getByName("fdroid").java.srcDir("src/froid/java")
    }
}

dependencies {
    // Support all favors except F-Droid
    "githubImplementation"(libs.bundles.firebase)
    "betaImplementation"(libs.bundles.firebase)
    "googleplayImplementation"(libs.bundles.firebase)
    "amazonImplementation"(libs.bundles.firebase)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.retrofit)
    implementation(libs.bundles.flowbus)
    implementation(libs.bundles.kotpref)
    implementation(libs.bundles.coil)

    ksp(libs.moshi.codegen)
    implementation(libs.media3.exoplayer)
    implementation(libs.sardine.android)
    implementation(libs.gson)
    implementation(libs.smbj)
    implementation(libs.timber)

    debugImplementation(libs.leakcanary)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events("started", "skipped", "passed", "failed")
        showStandardStreams = true
    }
}