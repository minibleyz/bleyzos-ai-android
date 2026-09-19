package ru.bleyzos.ai.api.auth

import android.content.Context
import org.json.JSONObject
import ru.bleyzos.ai.api.BzApiException
import ru.bleyzos.ai.api.Cancelable
import ru.bleyzos.ai.api.MainThread
import ru.bleyzos.ai.api.model.AuthUser
import ru.bleyzos.ai.api.net.BzHttp
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Ответ POST /api/auth/device. */
data class DeviceCode(
    val deviceCode: String,
    /** Короткий код вида XXXX-XXXX — показываем пользователю. */
    val userCode: String,
    /** Ссылку нужно открыть в браузере: там пройдёт OAuth-вход Bleyzos. */
    val verificationUri: String,
    val expiresInSec: Int,
    val intervalSec: Int,
)

interface LoginListener {
    /** Код получен: покажите его и откройте [DeviceCode.verificationUri] в браузере. */
    fun onCode(code: DeviceCode)
    fun onSuccess(user: AuthUser)
    fun onError(message: String)
    /** Код истёк, пока пользователь не вошёл. */
    fun onExpired()
}

/**
 * Вход через Device Authorization Flow (серверные /api/auth/device и /api/auth/device/verify):
 * приложение получает код → пользователь входит в браузере → приложение опрашивает сервер.
 */
class AuthManager internal constructor(
    context: Context,
    private val http: BzHttp,
) {
    private val prefs = context.applicationContext.getSharedPreferences("bleyzos_ai_auth", Context.MODE_PRIVATE)
    private val listeners = ArrayList<() -> Unit>()

    var user: AuthUser? = null
        private set

    val isLoggedIn: Boolean get() = user != null

    init {
        val raw = prefs.getString(KEY_USER, null)
        val token = prefs.getString(KEY_TOKEN, null)
        if (raw != null && token != null) {
            try {
                val o = JSONObject(raw)
                user = AuthUser(o.optString("id"), o.optString("email"), o.optString("name"))
                applyToken(token)
            } catch (_: Exception) {
                clearStored()
            }
        }
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun notifyChanged() = listeners.toList().forEach { it() }

    private fun applyToken(token: String?) {
        // Сервер читает сессию из куки bleyzos_session; Bearer — на будущее.
        http.cookies.set(BzHttp.SESSION, token)
        http.bearer = token
    }

    private fun clearStored() {
        prefs.edit().remove(KEY_USER).remove(KEY_TOKEN).apply()
    }

    fun logout() {
        user = null
        clearStored()
        applyToken(null)
        notifyChanged()
    }

    fun startLogin(listener: LoginListener): Cancelable {
        val cancelled = AtomicBoolean(false)
        MainThread.io.execute {
            try {
                val code = requestCode()
                MainThread.post { if (!cancelled.get()) listener.onCode(code) }
                poll(code, cancelled, listener)
            } catch (e: BzApiException) {
                if (!cancelled.get()) MainThread.post { listener.onError(e.message ?: "Не удалось начать вход") }
            } catch (e: IOException) {
                if (!cancelled.get()) MainThread.post { listener.onError("Нет соединения с сервером") }
            } catch (e: Exception) {
                if (!cancelled.get()) MainThread.post { listener.onError(e.message ?: "Не удалось начать вход") }
            }
        }
        return Cancelable { cancelled.set(true) }
    }

    private fun requestCode(): DeviceCode {
        val conn = http.open("POST", "/api/auth/device/")
        try {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(0)
            val status = conn.responseCode
            http.absorb(conn)
            if (status !in 200..299) {
                throw BzApiException(http.errorMessage(conn, "Ошибка сервера ($status)"), status)
            }
            val o = JSONObject(conn.inputStream.use { String(http.readAll(it, 64 * 1024), Charsets.UTF_8) })
            return DeviceCode(
                deviceCode = o.getString("device_code"),
                userCode = o.getString("user_code"),
                verificationUri = o.getString("verification_uri"),
                expiresInSec = o.optInt("expires_in", 600),
                intervalSec = o.optInt("interval", 3).coerceAtLeast(1),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun poll(code: DeviceCode, cancelled: AtomicBoolean, listener: LoginListener) {
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
        while (!cancelled.get()) {
            try {
                Thread.sleep(code.intervalSec * 1000L)
            } catch (_: InterruptedException) {
                return
            }
            if (cancelled.get()) return
            if (System.currentTimeMillis() > deadline) {
                MainThread.post { if (!cancelled.get()) listener.onExpired() }
                return
            }
            val result = try {
                verify(code.deviceCode)
            } catch (_: Exception) {
                continue // временный сбой сети — пробуем на следующем тике
            }
            when (result) {
                is Verify.Pending -> Unit
                is Verify.Expired -> {
                    MainThread.post { if (!cancelled.get()) listener.onExpired() }
                    return
                }
                is Verify.Authorized -> {
                    MainThread.post {
                        if (cancelled.get()) return@post
                        user = result.user
                        prefs.edit()
                            .putString(
                                KEY_USER,
                                JSONObject().put("id", result.user.id).put("email", result.user.email)
                                    .put("name", result.user.name).toString()
                            )
                            .putString(KEY_TOKEN, result.token)
                            .apply()
                        applyToken(result.token)
                        notifyChanged()
                        listener.onSuccess(result.user)
                    }
                    return
                }
            }
        }
    }

    private sealed class Verify {
        object Pending : Verify()
        object Expired : Verify()
        class Authorized(val token: String, val user: AuthUser) : Verify()
    }

    private fun verify(deviceCode: String): Verify {
        val conn = http.open("POST", "/api/auth/device/verify/")
        try {
            val body = JSONObject().put("device_code", deviceCode).toString().toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            if (status !in 200..299) throw BzApiException("Ошибка сервера ($status)", status)
            val o = JSONObject(conn.inputStream.use { String(http.readAll(it, 64 * 1024), Charsets.UTF_8) })
            return when (o.optString("status")) {
                "authorized" -> {
                    val u = o.optJSONObject("user")
                    val token = o.optString("token")
                    if (u == null || token.isEmpty()) throw BzApiException("Сервер не выдал токен")
                    Verify.Authorized(token, AuthUser(u.optString("id"), u.optString("email"), u.optString("name")))
                }
                "expired" -> Verify.Expired
                else -> Verify.Pending
            }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val KEY_USER = "user"
        const val KEY_TOKEN = "token"
    }
}
