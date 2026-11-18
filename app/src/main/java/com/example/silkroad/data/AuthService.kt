package com.example.silkroad.data

import com.example.silkroad.data.remote.FakeStoreApi
import com.example.silkroad.data.remote.LoginRequest
import com.example.silkroad.domain.ShopRules
import com.example.silkroad.domain.model.User
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class AuthService(private val api: FakeStoreApi) {
    suspend fun login(username: String, password: String, demo: Boolean): Result<User> {
        ShopRules.credentialsError(username, password)?.let { return Result.failure(IllegalArgumentException(it)) }
        return try {
            if (demo) {
                require(username.trim() == "demo" && password == "SilkRoad2026") { "Usuario o contraseña incorrectos." }
                Result.success(User("demo"))
            } else {
                val result = api.login(LoginRequest(username.trim(), password))
                require(!result.token.isNullOrBlank()) { "El servidor no devolvió una sesión válida." }
                Result.success(User(username.trim(), result.token))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(if (e.code() == 401 || e.code() == 400)
                "Usuario o contraseña incorrectos." else "El servicio de acceso no está disponible."))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(IllegalStateException("No se pudo iniciar sesión. Revisa tu conexión o usa la cuenta demo."))
        }
    }
}
