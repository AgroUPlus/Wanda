plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.wander.android.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Generating a profile needs API 28+; the app itself still supports 26.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The app's `fastDebug` build is optimised like release and not debuggable, which is what a
    // macrobenchmark needs; it is also signed with the debug key, so it installs without a keystore.
    buildTypes {
        create("fastDebug") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        // Pairs with the app's `nonMinified` build, which profiles are recorded against.
        create("nonMinified") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
