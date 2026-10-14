import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 has Kotlin support built in; the kotlin-android plugin is gone.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ru.noisedune"
    // Compiled against 37, targeting 36: the current AndroidX releases require
    // the newer compile SDK, while 36 is what Play requires apps to target.
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.noisedune"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            // So the tuning build can sit on the phone next to the real app
            // rather than replacing it. DataStore is keyed by application id,
            // so this also gives the dev build its own preferences and keeps
            // a night of experiments out of the app actually in use.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // AGP 9 defaults this off. The developer tuning panel is gated on
        // BuildConfig.DEBUG, which is what lets R8 remove it from release.
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
