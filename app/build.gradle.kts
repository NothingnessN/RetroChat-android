plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.nothingnessn.retrochat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nothingnessn.retrochat"
        minSdk = 15
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Compose'u tamamen kapat
    buildFeatures {
        compose = false
    }
}

dependencies {
    // Sadece klasik AndroidX
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Test (istersen kalabilir)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}