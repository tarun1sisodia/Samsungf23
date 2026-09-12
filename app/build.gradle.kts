import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load signing info if present (committed for this personal app — see keystore.properties).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

android {
    namespace = "com.tarun1sisodia.catparallax"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tarun1sisodia.catparallax"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Don't let lint fail CI release builds for style-level issues.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// Deliberately empty: this app is built against the Android framework only.
// That keeps the APK tiny (<300 KB) and removes dependency-resolution failure
// modes from CI. PrefsRepository uses SharedPreferences rather than DataStore
// for the same reason (see docs in that class).
dependencies {
}
