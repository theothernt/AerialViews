plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.neilturner.aerialviews.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.keep")
    }

    // Flavorless on purpose: a library without the version dimension needs no
    // missingDimensionStrategy when the flavored :app consumes it.
    kotlin {
        jvmToolchain(21)
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencies {
    // Owner types used by the host contracts (ScreensaverHost) and the Compose ViewModels.
    api(libs.lifecycle.runtime.ktx)
    api(libs.lifecycle.viewmodel)
    api(libs.savedstate.ktx)
}
