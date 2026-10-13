plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "tv.projectivy.plugin.wallpaperprovider.api"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        aidl = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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