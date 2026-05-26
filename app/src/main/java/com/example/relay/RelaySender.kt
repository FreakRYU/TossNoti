package com.example.relay

import android.util.Log
import com.example.database.NotificationLog
import com.example.database.NotificationRepository
import com.example.security.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shared "send a notification through ntfy.sh" pipeline. Used by both the
 * background AlarmListenerService (real interception) and the in-app "test
 * send" button so that the test exercises the same path real notifications
 * take.
 *
 * Payload is tiny (typically <300 bytes encrypted) since we only transmit
 * text fields — no images. The receiver's notification UI uses the local
 * TossNoti icon, falling back to the app label for source identification.
 */
object RelaySender {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun sendNotification(
        title: String,
        text: String,
        packageName: String,
        pin: String,
        repository: NotificationRepository,
        appLabel: String? = null
    ): Result = withContext(Dispatchers.IO) {
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            return@withContext Result.Failure("PIN이 비어 있거나 형식이 잘못됨")
        }

        // Local log first so the row appears even if the network call fails.
        try {
            repository.insertLog(
                NotificationLog(
                    title = title,
                    message = text,
                    packageName = packageName,
                    pin = pin,
                    isSent = true,
                    appLabel = appLabel.orEmpty()
                )
            )
        } catch (e: Exception) {
            Log.w("RelaySender", "Local log insert failed: ${e.message}")
        }

        val topic = CryptoUtils.deriveTopic(pin)
        val url = "https://ntfy.sh/$topic"

        val plaintext = JSONObject().apply {
            put("title", title)
            put("message", text)
            put("packageName", packageName)
            put("timestamp", System.currentTimeMillis())
            if (!appLabel.isNullOrBlank()) put("appLabel", appLabel)
        }.toString()
        val bodyText = CryptoUtils.encrypt(plaintext, pin)
        Log.d("RelaySender", "Encrypted body size: ${bodyText.length} chars")

        return@withContext try {
            val mediaType = "text/plain; charset=utf-8".toMediaType()
            val body = bodyText.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("X-Title", "TossNoti")
                .header("X-Tags", "bell")
                .header("Priority", "high")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i("RelaySender", "Sent OK to topic: $topic")
                    Result.Success
                } else {
                    val respBody = try {
                        response.body?.string()?.take(180) ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    val msg = "HTTP ${response.code} ${response.message} ($respBody)"
                    Log.e("RelaySender", "Send failed: $msg")
                    Result.Failure(msg)
                }
            }
        } catch (e: Exception) {
            Log.e("RelaySender", "Send exception: ${e.message}", e)
            Result.Failure(e.localizedMessage ?: "Network error")
        }
    }
}
