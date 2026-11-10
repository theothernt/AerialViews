plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.junit5)
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    implementation(libs.bundles.kotlin)
    implementation(libs.core.ktx)
    implementation(libs.exifinterface)
    implementation(libs.preference.ktx)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.rtsp)
    implementation(libs.media3.container)

    // Data layer: HTTP clients, SMB, JSON, Room and the Ktor message API.
    implementation(libs.bundles.retrofit)
    implementation(libs.smbj)
    api(libs.room3.runtime)
    ksp(libs.room3.compiler)
    implementation(libs.sqlite.framework)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.flowbus)
    implementation(libs.timber)
    implementation(libs.slf4j.simple)

    // MigrationHelper clears the Coil disk cache on upgrade.
    implementation(libs.coil)

    // Prefs moved in Phase 3. The kotpref-initializer is deliberately NOT added here: :app already
    // ships its ContentProvider, which calls Kotpref.init before any prefs are read.
    api(libs.kotpref)
    api(libs.kotpref.enum.support)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.sqlite.bundled.jvm)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
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
