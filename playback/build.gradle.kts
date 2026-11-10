plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.neilturner.aerialviews.playback"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.keep")
    }

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
    implementation(project(":core"))

    implementation(libs.bundles.kotlin)
    implementation(libs.core.ktx)
    implementation(libs.bundles.exoplayer)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.flowbus)
    implementation(libs.smbj)
    implementation(libs.okhttp)
    implementation(libs.appcompat)
    implementation(libs.timber)
}
