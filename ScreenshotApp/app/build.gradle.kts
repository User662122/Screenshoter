plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
    
    // Add the Crashlytics Gradle plugin
    id("com.google.firebase.firebase-crashlytics")
}

android {
    namespace = "com.uitreecapture"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.uitreecapture"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")

    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))

    // Add the dependencies for Firebase products
    // When using the BoM, you don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}
