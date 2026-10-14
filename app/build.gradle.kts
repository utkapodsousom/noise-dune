import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    // AGP 9 has Kotlin support built in; the kotlin-android plugin is gone.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The release signing key, described by a file that is not in the repository.
// `keystore.properties.example` says what goes in it; the keystore it points at
// lives outside the project tree, because a store that cannot be replaced should
// not sit anywhere a `git clean` or a stray `rm -rf` can reach it.
//
// Absent, the file simply leaves the release build unsigned, which is what it has
// always been: `assembleDebug` and the tests keep working on a fresh clone, and
// the failure mode for a release is a missing signature rather than a broken
// build. RELEASE.md is where the signed build is actually made.
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
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

    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                // Both are AGP's defaults; stated so neither can be lost to a
                // change of default. v2 is the one that matters — it is what
                // Android 7 and later and the store's upload check verify — and
                // the v1 JAR signature rides along for anything older in a
                // toolchain that still reads it.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
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
            signingConfig = signingConfigs.findByName("release")
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
