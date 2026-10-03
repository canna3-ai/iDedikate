plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.secrets.gradle.plugin)
}

android {
    namespace = "com.memoria.idedikate"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.memoria.idedikate"
        minSdk = 24
        targetSdk = 37
        versionCode = 16
        versionName = "3.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Comma-separated AdMob test device IDs (logged by the Ads SDK on first ad request), e.g.
            // admobTestDeviceIds=ABC123 in ~/.gradle/gradle.properties. Emulators are test devices already.
            val testDeviceIds = providers.gradleProperty("admobTestDeviceIds").getOrElse("")
            buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", "\"$testDeviceIds\"")
        }
        release {
            buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", "\"\"")
            proguardFiles("proguard-rules.pro")
            optimization {
                enable = true
            }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.material)
    implementation(libs.play.services.location)
    implementation(libs.play.services.ads)
    implementation(libs.osmdroid.android)
    implementation(libs.arcore)
    implementation(libs.arsceneview)
    // Keyless authorization for ARCore's Geospatial API (location-based AR)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}