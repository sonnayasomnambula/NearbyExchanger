plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

val gitHash: String by lazy {
    try {
        val process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

android {
    namespace = "org.sonnayasomnambula.nearby.exchanger"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.sonnayasomnambula.nearby.exchanger"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.all {
        outputs.all {
            val isDebuggable = name.contains("debug", ignoreCase = true)
            val version = android.defaultConfig.versionName ?: "unknown"
            val projectName = rootProject.name
            val suffix = if (isDebuggable) "-d" else ""

            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "$projectName.$version.$gitHash$suffix.apk"
        }
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.documentfile)
    implementation(libs.play.services.nearby)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material.icons.extended)

    // Тесты
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}