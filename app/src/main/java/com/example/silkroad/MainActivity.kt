package com.example.silkroad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.silkroad.data.firebase.FirebaseManager
import com.example.silkroad.presentation.ui.SilkRoadApp
import com.example.silkroad.ui.theme.SilkRoadTheme

class MainActivity : ComponentActivity() {
    private val deepLinkUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Práctica 5: LoginScreen oculta la contraseña y no la conserva en estado guardado.
        // ShopRules.credentialsError rechaza usuario vacío o solo espacios, limita usuario a 40
        // caracteres y contraseña a 8..128. AuthService devuelve errores genéricos.
        // RetrofitInstance mantiene el registro HTTP en NONE para no imprimir credenciales.
        // [TC07 & TC08]: Inicialización de Firebase con persistencia offline habilitada
        FirebaseManager.initialize(this)

        deepLinkUriState.value = intent?.data

        setContent {
            SilkRoadTheme {
                SilkRoadApp(deepLinkUri = deepLinkUriState.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUriState.value = intent.data
    }
}
