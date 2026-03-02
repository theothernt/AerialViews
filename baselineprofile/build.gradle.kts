@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.neilturner.aerialviews.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    kotlin {
        jvmToolchain(21)
    }

    targetProjectPath = ":app"

    flavorDimensions += listOf("version")
    productFlavors {
        create("googleplay") {
            dimension = "version"
        }
        create("googleplaybeta") {
            dimension = "version"
        }
        create("amazon") {
            dimension = "version"
        }
        create("beta") {
            dimension = "version"
        }
        create("fdroid") {
            dimension = "version"
        }
        create("github") {
            dimension = "version"
        }
    }

    testOptions.managedDevices.allDevices {
        @Suppress("UnstableApiUsage")
        create<ManagedVirtualDevice>("androidTvApi36") {
            device = "Television (1080p)"
            apiLevel = 36
            systemImageSource = "google_apis_playstore"
        }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices += "androidTvApi36"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.junit)
    implementation(libs.espresso.core)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId.orEmpty() }
        )
    }
}