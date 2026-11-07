plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.s7zb.photoframetv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.s7zb.photoframetv"
        minSdk = 21          // Mi TV Stick (Android 9, API 28) and older Android TV boxes
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // One fixed key committed to the repo so every build is signed identically
    // and new APKs install over old ones as updates.
    signingConfigs {
        create("fixed") {
            storeFile = file("photoframe.keystore")
            storePassword = "photoframe"
            keyAlias = "photoframe"
            keyPassword = "photoframe"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
}
