import java.io.FileInputStream
import java.util.Properties

val kotlinVersion: String by rootProject.extra

plugins {
    kotlin("android")
    kotlin("kapt")
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("de.mannodermaus.android-junit5")
    id("com.google.devtools.ksp")
    id("org.jmailen.kotlinter")
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
    compileSdk = 34

    defaultConfig {
        applicationId = "com.neilturner.aerialviews"
        minSdk = 22 // to support Fire OS 5, Android v5.1, Lvl 22
        targetSdk = 34
        versionCode = 24
        versionName = "1.7.3"

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
            // isMinifyEnabled = true
        }
        getByName("release") {
            buildConfigField("String", "BUILD_TIME", "\"${System.currentTimeMillis()}\"")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            manifestPlaceholders["analyticsCollectionEnabled"] = true
            manifestPlaceholders["crashlyticsCollectionEnabled"] = true
            manifestPlaceholders["performanceCollectionEnabled"] = true

            // isDebuggable = true
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
    // Firebase
    val firebaseDependencies = listOf(
        "com.google.firebase:firebase-analytics-ktx:22.1.0",
        "com.google.firebase:firebase-crashlytics-ktx:19.0.3",
        "com.google.firebase:firebase-perf-ktx:21.0.1"
    )

    // Support all favors except F-Droid
    firebaseDependencies.forEach { dependency ->
        "githubImplementation"(dependency)
        "betaImplementation"(dependency)
        "googleplayImplementation"(dependency)
        "amazonImplementation"(dependency)
    }

    // Kotlin + Coroutines
    val coroutinesVersion = "1.9.0"
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    // Android X
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.leanback:leanback-preference:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Retrofit, OkHttp, and Moshi
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.google.code.gson:gson:2.11.0")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.4.1")

    // FlowBus Pub/Sub
    implementation("com.github.Kosert.FlowBus:FlowBus:1.1")
    implementation("com.github.Kosert.FlowBus:FlowBus-android:1.1")

    // KotPref
    implementation("com.chibatching.kotpref:kotpref:2.13.2")
    implementation("com.chibatching.kotpref:initializer:2.13.2")
    implementation("com.chibatching.kotpref:enum-support:2.13.2")

    // SMBJ
    implementation("com.hierynomus:smbj:0.13.0")

    // WebDAV
    implementation("com.github.nova-video-player:sardine-android:0.0.2")

    // Coil - Images + GIFs
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Memory Leaks
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // Unit Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events("started", "skipped", "passed", "failed")
        showStandardStreams = true
    }
}