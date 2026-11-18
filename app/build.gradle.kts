import java.util.Properties
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Configuración de Elasticsearch leída de local.properties (no versionado).
// La genera elasticsearch/index-products.ps1. Si falta, la app usa la búsqueda local.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun esProp(name: String) = "\"" + localProps.getProperty("elasticsearch.$name", "").trim() + "\""
val useFirebaseEmulators = localProps.getProperty("firebase.emulators", "false").toBoolean()
val firebaseJson = file("google-services.json").takeIf { it.exists() }?.let {
    JsonSlurper().parseText(it.readText()) as Map<*, *>
}
val firebaseProject = firebaseJson?.get("project_info") as? Map<*, *>
val firebaseClient = (firebaseJson?.get("client") as? List<*>)?.map { it as Map<*, *> }?.firstOrNull {
    val info = it["client_info"] as? Map<*, *>
    val android = info?.get("android_client_info") as? Map<*, *>
    android?.get("package_name") == "com.example.silkroad"
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val firebaseAppId = (firebaseClient?.get("client_info") as? Map<*, *>)?.get("mobilesdk_app_id") as? String ?: ""
val firebaseApiKey = ((firebaseClient?.get("api_key") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key") as? String ?: ""

android {
    namespace = "com.example.silkroad"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.silkroad"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.3-hostinger"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ES_URL", esProp("url"))
        buildConfigField("String", "ES_API_KEY", esProp("apiKey"))
        buildConfigField("String", "ES_INDEX", esProp("index"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(if (useFirebaseEmulators) "demo-silkroad" else firebaseProject?.get("project_id") as? String ?: ""))
        buildConfigField("String", "FIREBASE_APP_ID", quoted(if (useFirebaseEmulators) "1:1234567890:android:0123456789abcdef" else firebaseAppId))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(if (useFirebaseEmulators) "demo-silkroad-api-key" else firebaseApiKey))
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", quoted(localProps.getProperty("firebase.emulatorHost", "10.0.2.2")))
    }
    buildTypes {
        getByName("debug") { buildConfigField("boolean", "FIREBASE_EMULATORS", useFirebaseEmulators.toString()) }
        getByName("release") { buildConfigField("boolean", "FIREBASE_EMULATORS", "false") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.9"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Lifecycle ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Navigation & Coil
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Firebase & Firestore (Práctica 7)
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
