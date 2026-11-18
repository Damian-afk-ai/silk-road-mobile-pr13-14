# Silk Road Mobile en Kotlin

Proyecto Android con Jetpack Compose. Las prácticas 13 y 14 cubren las reglas del carrito, el catálogo HTTP y la caché local. Abre esta carpeta en Android Studio con JDK 17 y Android SDK 34.

Para ejecutar el mismo flujo que GitHub Actions:

```bash
bash ./gradlew :app:testDebugUnitTest :app:lintDebug --continue
```

En Windows, usa `gradlew.bat` con las mismas tareas. Las pruebas JVM usan datos de prueba y un servidor en 127.0.0.1. Firebase y Elasticsearch se pueden configurar para ejecutar la aplicación completa. Para Firebase, aporta la configuración de cliente en `app/google-services.json`; el catálogo y la tienda de demostración pueden funcionar sin esa configuración.
