plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "tv.projectivy.plugin.wallpaperprovider.api"
    compileSdk = 36
    defaultConfig {
        minSdk = 22
    }

    buildFeatures {
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("nonMinifiedRelease") {
        }
        create("benchmarkRelease") {
        }
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.androidx)
}