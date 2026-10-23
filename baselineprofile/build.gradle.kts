@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.neilturner.aerialviews.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Note: androidx.benchmark.enabledRules cannot be set here. AGP hard
        // codes "-e androidx.benchmark.enabledRules macrobenchmark" when it
        // launches the instrumentation, which overrides anything set in the
        // build script. To run the baseline profile generator, pass the rule
        // on the command line instead:
        //   ./gradlew :baselineprofile:connectedBetaBenchmarkReleaseAndroidTest \
        //     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile
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
    useConnectedDevices = true
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