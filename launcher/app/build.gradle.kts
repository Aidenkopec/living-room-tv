plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "tv.livingroom.home"
    compileSdk = 37

    defaultConfig {
        applicationId = "tv.livingroom.home"
        minSdk = 31
        targetSdk = 31 // The TV's Android 12.
        // Increases with every build installed on the TV.
        versionCode = 49
        versionName = "0.1.$versionCode"
        // The TV is 32-bit ARM only; a Compose library ships one small native file, so only that ABI is packaged.
        ndk { abiFilters += "armeabi-v7a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Sideloaded over ADB only and never published to Play, so the debug key is enough.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // Play's target-version rule; this app is never published to Play.
        disable += "ExpiredTargetSdkVersion"
        // Built for one 32-bit ARM TV; there's no ChromeOS build.
        disable += "ChromeOsAbiSupport"
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
}

kotlin {
    // Zero compiler warnings, kept that way.
    compilerOptions { allWarningsAsErrors = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
