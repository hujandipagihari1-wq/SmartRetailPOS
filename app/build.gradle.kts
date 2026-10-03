plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.industri.smartpos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.industri.smartpos"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // MyApplication membaca BuildConfig.DEBUG untuk deciding DebugTree,
        // dan AGP 8+ tidak lagi men-generate BuildConfig secara default.
        buildConfig = true
    }
}

kotlin {
    // AGP 9 sudah memakai plugin Kotlin bawaan, jadi hanya set target JVM di sini
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Logging Utility Modern
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Coroutines & Lifecycle Scope
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}