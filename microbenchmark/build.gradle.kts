plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.benchmark)
}

android {
    namespace = "com.neilturner.aerialviews.microbenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // NOT-AOT-COMPILED is reported when the benchmark APK has not been
        // compiled with the "speed" profile. Gradle reinstalls the APK before
        // every run, which resets the compilation state, so the check is
        // suppressed here and the package is speed compiled by the
        // aotCompileBenchmarkPackage task instead.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "EMULATOR,LOW-BATTERY,NOT-AOT-COMPILED"
    }

    testBuildType = "release"
    buildTypes {
        debug {
            // Since isDebuggable can"t be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
        }
        release {
            isDefault = true
        }
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.junit.junit)
    androidTestImplementation(libs.benchmark.junit4)
    // Add your dependencies here. Note that you cannot benchmark code
    // in an app module this way - you will need to move any code you
    // want to benchmark to a library module:
    // https://developer.android.com/studio/projects/android-library#Convert
}

// The benchmark library wants the benchmark APK to be compiled with the "speed"
// profile. Gradle reinstalls the APK before every test run, which resets the
// compilation state, so the speed compile has to happen after the install.
// Run the aotCompileBenchmarkPackage task once the test APK is installed, or
// use the aotBenchmark task which chains install -> speed compile -> run.
androidComponents {
    onVariants { variant ->
        val testApplicationId = variant.androidTest?.applicationId ?: return@onVariants
        val variantName = variant.name.replaceFirstChar { it.uppercase() }

        // Captured outside the task actions - referencing the script receiver
        // from inside a doLast block breaks the configuration cache.
        val providerFactory = providers

        val compileTask =
            tasks.register("aotCompile${variantName}BenchmarkPackage") {
                group = "benchmark"
                description = "Speed compiles the installed benchmark test package on the connected device."
                // The compile has to happen after the install, because installing
                // the APK again resets the compilation state.
                dependsOn("install${variantName}AndroidTest")
                doLast {
                    val output =
                        providerFactory.exec {
                            commandLine(
                                "adb", "shell", "cmd", "package", "compile",
                                "-m", "speed", "-f", testApplicationId.get(),
                            )
                        }.standardOutput.asText.get()
                    logger.lifecycle(output)
                }
            }

        tasks.register("aot${variantName}Benchmark") {
            group = "benchmark"
            description = "Installs, speed compiles and runs the benchmark tests on the connected device."
            dependsOn(compileTask)
            doLast {
                val output =
                    providerFactory.exec {
                        commandLine(
                            "adb", "shell", "am", "instrument", "-w",
                            "-e", "androidx.benchmark.suppressErrors", "EMULATOR,LOW-BATTERY",
                            "${testApplicationId.get()}/androidx.benchmark.junit4.AndroidBenchmarkRunner",
                        )
                    }.standardOutput.asText.get()
                logger.lifecycle(output)
            }
        }
    }
}
