package com.example.silkroad.data.firebase

import android.content.Context
import com.example.silkroad.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/** Una sola base y Authentication para ambos dispositivos. No fabrica datos si falla la red. */
object FirebaseManager {
    @Synchronized
    fun initialize(context: Context): FirebaseFirestore {
        if (FirebaseApp.getApps(context).isEmpty()) {
            check(BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank()) {
                "Agrega app/google-services.json con el paquete com.example.silkroad."
            }
            check(BuildConfig.FIREBASE_EMULATORS || !BuildConfig.FIREBASE_PROJECT_ID.startsWith("demo-")) {
                "Configura Firebase real para producción."
            }
            FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID).setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY).build())
        }
        if (!configured) {
            val db = FirebaseFirestore.getInstance()
            db.firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(true).build()
            if (BuildConfig.FIREBASE_EMULATORS) {
                db.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 8080)
                FirebaseAuth.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099)
            }
            configured = true
        }
        return FirebaseFirestore.getInstance()
    }
    private var configured = false
    val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
}
