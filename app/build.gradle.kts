plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ccompiler.pro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ccompiler.pro"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Signing key comes from CI secrets (see SIGNING steps). Nothing secret is stored in the repo.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_FILE")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // No obfuscation: readable, reviewable code is better for trust (and avoids heuristic AV hits).
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        // Toolchain executables ship as lib*.so and must be extracted to nativeLibraryDir to be executable.
        jniLibs { useLegacyPackaging = true }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
