// FILE: app/build.gradle.kts  (REEMPLAZA COMPLETO)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.tiendamascotas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tiendamascotas"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // (si aún usas Google Maps en otra parte)
        resValue("string", "google_maps_key", "AQUI_TU_API_KEY_O_UN_PLACEHOLDER")

        // BuildConfig: URLs/keys
        buildConfigField("String", "ASSISTANT_BASE_URL", "\"http://192.168.0.100:8080\"")

        // ⚠️ Lee desde gradle.properties -> MAPTILER_API_KEY=TU_KEY
        buildConfigField(
            "String",
            "MAPTILER_API_KEY",
            "\"${project.findProperty("MAPTILER_API_KEY") ?: ""}\""
        )
    }

    buildTypes {
        debug {
            buildConfigField("String", "ASSISTANT_BASE_URL", "\"http://192.168.0.100:8080\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ASSISTANT_BASE_URL", "\"https://tu-dominio-o-workers.example\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    buildFeatures {
        compose = true
        buildConfig = true // necesario para BuildConfig.*
    }
}

dependencies {
    // ----- Jetpack Compose (BOM) -----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation)

    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ----- Core AndroidX -----
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // ----- Firebase (BOM) -----
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // ----- Imágenes -----
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ----- Mapas (MapLibre) -----
    implementation("org.maplibre.gl:android-sdk:12.0.1") // SDK principal

    // GeoJSON (para Feature/Point/FeatureCollection)
    implementation("com.mapbox.mapboxsdk:mapbox-sdk-geojson:5.8.0")

    // ----- Ubicación (Fused Location Provider) -----
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ----- Red -----
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // ----- Test -----
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
