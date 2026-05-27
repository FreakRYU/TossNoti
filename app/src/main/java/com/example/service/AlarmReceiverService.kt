package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.AlarmTossApplication
import com.example.database.NotificationLog
import com.example.database.NotificationRepository
import com.example.security.CryptoUtils
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AlarmReceiverService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var streamJob: Job? = null

    private lateinit var repository: NotificationRepository
    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite read timeout for streaming
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val notificationIdCounter = AtomicInteger(NOTIFICATION_ID + 1)

    companion object {
        const val CHANNEL_ID = "alarmtoss_receiver_channel"
        const val SYSTEM_NOTIFICATION_CHANNEL_ID = "alarmtoss_system_channel"
        private const val NOTIFICATION_ID = 9999

        // Per-PIN unix-second cursor of the most recently processed message.
        // Used as ntfy's ?since=<timestamp> on every reconnect. Replaces the
        // older per-PIN message-ID cursor, which silently failed whenever the
        // boundary ID had been evicted from ntfy's cache.
        private const val PREF_KEY_LAST_MSG_TIME_PREFIX = "last_message_time_"

        // Recent-message-ID dedup window. ntfy may resend the boundary
        // message on ?since=<ts> reconnects (timestamp resolution is one
        // second), and a notification burst can share the same `time` value,
        // so we drop already-seen IDs to avoid double-notifying.
        private const val PREF_KEY_RECENT_IDS_PREFIX = "recent_message_ids_"
        private const val RECENT_IDS_CAPACITY = 64

        // ntfy.sh free tier holds messages for 12h. If our cursor is empty
        // (fresh pairing) or older than this, ask for the full window so
        // cached messages aren't silently dropped on reconnect.
        private const val NTFY_CACHE_WINDOW_SEC = 12L * 60L * 60L
        private const val NTFY_CACHE_WINDOW_PARAM = "12h"

        private var isServiceRunningStatus = false

        fun isRunning(): Boolean = isServiceRunningStatus
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunningStatus = true
        repository = (applicationContext as AlarmTossApplication).repository
        createNotificationChannels()
        Log.i("AlarmReceiverService", "AlarmReceiverService Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pin = intent?.getStringExtra("pin") ?: ""

        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            Log.e("AlarmReceiverService", "Invalid PIN passed to receiver service, refusing to start.")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification(pin)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
            else -> startForeground(NOTIFICATION_ID, notification)
        }

        startListening(pin)

        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunningStatus = false
        streamJob?.cancel()
        serviceJob.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
        Log.i("AlarmReceiverService", "AlarmReceiverService Stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Streaming ────────────────────────────────────────────────────────

    private fun startListening(pin: String) {
        streamJob?.cancel()
        streamJob = serviceScope.launch {
            val topic = CryptoUtils.deriveTopic(pin)
            val sharedPref = applicationContext.getSharedPreferences("AlarmTossPref", Context.MODE_PRIVATE)
            val timeKey = PREF_KEY_LAST_MSG_TIME_PREFIX + pin
            val recentIdsKey = PREF_KEY_RECENT_IDS_PREFIX + pin

            var retryDelay = 3_000L
            while (isActive) {
                try {
                    val sinceParam = computeSinceParam(sharedPref.getLong(timeKey, 0L))
                    val url = "https://ntfy.sh/$topic/json?since=$sinceParam"

                    Log.i("AlarmReceiverService", "Connecting to ntfy.sh stream at: $url")
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("HTTP Error code: ${response.code}")
                        }

                        retryDelay = 3_000L
                        Log.i("AlarmReceiverService", "Successfully connected stream. Listening for messages...")

                        val bodyStream = response.body?.byteStream() ?: throw Exception("Null body stream")
                        BufferedReader(InputStreamReader(bodyStream)).use { reader ->
                            var line: String? = null
                            while (isActive && reader.readLine().also { line = it } != null) {
                                line?.let { jsonLine ->
                                    handleStreamLine(jsonLine, pin, sharedPref, timeKey, recentIdsKey)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    Log.i("AlarmReceiverService", "Stream coroutine cancelled")
                    break
                } catch (e: Exception) {
                    Log.e("AlarmReceiverService", "Stream connection error: ${e.message}, retrying in ${retryDelay / 1000}s...", e)
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    private fun handleStreamLine(
        jsonLine: String,
        pin: String,
        prefs: SharedPreferences,
        timeKey: String,
        recentIdsKey: String,
    ) {
        try {
            val ntfyObj = JSONObject(jsonLine)
            val event = ntfyObj.optString("event")
            Log.d("AlarmReceiverService", "Stream event=$event size=${jsonLine.length}B")

            if (event != "message") return

            val rawMsg = ntfyObj.optString("message")
            val msgId = ntfyObj.optString("id", "")
            val msgTime = ntfyObj.optLong("time", 0L)

            if (msgId.isNotEmpty() && msgId in readRecentIds(prefs, recentIdsKey)) {
                Log.d("AlarmReceiverService", "Dropping duplicate message id=$msgId")
                return
            }

            val decrypted = CryptoUtils.decrypt(rawMsg, pin)
            if (decrypted == null) {
                Log.w("AlarmReceiverService", "Failed to decrypt incoming message (size=${rawMsg.length}); either PIN mismatch or injected garbage. Dropping.")
                advanceCursor(prefs, timeKey, recentIdsKey, msgId, msgTime)
                return
            }

            val detailObj = JSONObject(decrypted)
            val title = detailObj.optString("title", "알림토스")
            val message = detailObj.optString("message", "")
            val packageName = detailObj.optString("packageName", "unknown")
            val appLabel = detailObj.optString("appLabel", "").ifBlank {
                packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            }

            // Mark the cursor synchronously before launching side-effect work
            // so a rapid-fire duplicate (overlapping reconnect, boundary
            // replay) is already in the dedup set by the time the next line
            // is parsed.
            advanceCursor(prefs, timeKey, recentIdsKey, msgId, msgTime)

            serviceScope.launch {
                try {
                    repository.insertLog(
                        NotificationLog(
                            title = title,
                            message = message,
                            packageName = packageName,
                            pin = pin,
                            isSent = false,
                            appLabel = appLabel
                        )
                    )
                    triggerLocalNotification(title, message, packageName, appLabel)
                } catch (e: Exception) {
                    Log.e("AlarmReceiverService", "Failed to process received notification payload: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiverService", "Error unpacking message JSON: ${e.message}")
        }
    }

    // ── Cursor + dedup helpers ───────────────────────────────────────────

    private fun computeSinceParam(lastTimeSec: Long): String {
        val nowSec = System.currentTimeMillis() / 1000L
        return if (lastTimeSec <= 0L || nowSec - lastTimeSec > NTFY_CACHE_WINDOW_SEC) {
            NTFY_CACHE_WINDOW_PARAM
        } else {
            lastTimeSec.toString()
        }
    }

    private fun readRecentIds(prefs: SharedPreferences, key: String): List<String> {
        val raw = prefs.getString(key, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split('\n').filter { it.isNotEmpty() }
    }

    private fun advanceCursor(
        prefs: SharedPreferences,
        timeKey: String,
        recentIdsKey: String,
        msgId: String,
        msgTime: Long,
    ) {
        val editor = prefs.edit()
        if (msgTime > 0L) {
            // Monotonically increasing — don't move the cursor backwards if
            // events arrive out of order (rare but possible with ?since=
            // replays at the boundary).
            val current = prefs.getLong(timeKey, 0L)
            if (msgTime > current) editor.putLong(timeKey, msgTime)
        }
        if (msgId.isNotEmpty()) {
            val ids = readRecentIds(prefs, recentIdsKey).toMutableList()
            ids.remove(msgId)
            ids.add(msgId)
            while (ids.size > RECENT_IDS_CAPACITY) ids.removeAt(0)
            editor.putString(recentIdsKey, ids.joinToString("\n"))
        }
        editor.apply()
    }

    private fun triggerLocalNotification(
        title: String,
        message: String,
        origPackage: String,
        appLabel: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val resultIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationIdCounter.get(),
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Classic notification layout: "[Instagram] 새 팔로워" title with the
        // original body text. Source-app icon is not displayed — the left
        // avatar always shows TossNoti's app icon (Samsung One UI groups
        // multiple notifications under a single "TossNoti" group header).
        val notification = NotificationCompat.Builder(this, SYSTEM_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(com.example.R.mipmap.ic_launcher)
            .setContentTitle("[$appLabel] $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationIdCounter.getAndIncrement(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val foregroundChannel = NotificationChannel(
                CHANNEL_ID,
                "TossNoti 수신 모니터 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "TossNoti가 백그라운드에서 실시간 수신 대기할 때 표시됩니다."
            }
            notificationManager.createNotificationChannel(foregroundChannel)

            val systemChannel = NotificationChannel(
                SYSTEM_NOTIFICATION_CHANNEL_ID,
                "토스받은 원격 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "다른 기기에서 토스받은 실시간 알림을 띄웁니다."
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(systemChannel)
        }
    }

    private fun buildForegroundNotification(pin: String): Notification {
        val resultIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.R.mipmap.ic_launcher)
            .setContentTitle("TossNoti 실시간 수신 작동 중")
            .setContentText("기기 연동 PIN: $pin (다른 기기로부터 원격 알림 수신 중...)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
