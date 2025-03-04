import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.neilturner.aerialviews.baselineprofile"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("pixel9Api34") {
            device = "Pixel 9"
            apiLevel = 34
            systemImageSource = "google_apis_playstore"
        }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices += "pixel9Api34"
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