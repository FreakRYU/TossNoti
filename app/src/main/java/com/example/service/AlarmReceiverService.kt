package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    private var reconnectJob: Job? = null

    private lateinit var repository: NotificationRepository
    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite read timeout for streaming
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val notificationIdCounter = AtomicInteger(NOTIFICATION_ID + 1)

    // Tracks the PIN the service was started with so we can transparently
    // reconnect after screen-off → screen-on cycles.
    @Volatile private var currentPin: String? = null

    // BroadcastReceiver hooked to system screen on/off events. Registered in
    // onCreate, unregistered in onDestroy. Used to pause/resume the ntfy stream
    // and dramatically reduce battery drain while the user isn't looking at
    // the phone — ntfy.sh holds queued messages for ~12h, and `?since=<id>`
    // catches us up on reconnect.
    private var screenStateReceiver: BroadcastReceiver? = null

    companion object {
        const val CHANNEL_ID = "alarmtoss_receiver_channel"
        const val SYSTEM_NOTIFICATION_CHANNEL_ID = "alarmtoss_system_channel"
        private const val NOTIFICATION_ID = 9999
        private const val PREF_KEY_LAST_MSG_PREFIX = "last_message_id_"

        // Debounce so brief screen-wakes (fingerprint unlock, glance) do not
        // immediately reconnect. ntfy retains messages so a slight delay
        // costs us nothing.
        private const val SCREEN_ON_DEBOUNCE_MS = 10_000L

        private var isServiceRunningStatus = false

        fun isRunning(): Boolean = isServiceRunningStatus
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunningStatus = true
        repository = (applicationContext as AlarmTossApplication).repository
        createNotificationChannels()
        registerScreenStateReceiver()
        Log.i("AlarmReceiverService", "AlarmReceiverService Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pin = intent?.getStringExtra("pin") ?: ""

        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            Log.e("AlarmReceiverService", "Invalid PIN passed to receiver service, refusing to start.")
            stopSelf()
            return START_NOT_STICKY
        }

        currentPin = pin
        val initiallyInteractive = isScreenInteractive()
        val notification = buildForegroundNotification(pin, paused = !initiallyInteractive)
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

        // Only open the ntfy stream right away if the screen is on. Otherwise
        // wait for SCREEN_ON to come through screenStateReceiver.
        if (initiallyInteractive) {
            startListening(pin)
        } else {
            Log.i("AlarmReceiverService", "Service started with screen off — deferring stream until screen-on.")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunningStatus = false
        reconnectJob?.cancel()
        streamJob?.cancel()
        serviceJob.cancel()
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w("AlarmReceiverService", "Failed to unregister screenStateReceiver: ${e.message}")
            }
        }
        screenStateReceiver = null
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

    // ── Screen state plumbing ────────────────────────────────────────────

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> onScreenOn()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
    }

    private fun onScreenOff() {
        Log.i("AlarmReceiverService", "Screen OFF — suspending ntfy stream to save battery")
        reconnectJob?.cancel()
        reconnectJob = null
        streamJob?.cancel()
        streamJob = null
        currentPin?.let { refreshForegroundNotification(it, paused = true) }
    }

    private fun onScreenOn() {
        val pin = currentPin ?: return
        // Already streaming — nothing to do (likely SCREEN_ON arrived while
        // we were never disconnected, e.g. screen toggled too fast).
        if (streamJob?.isActive == true) return
        // Already in the debounce window — don't restart the timer.
        if (reconnectJob?.isActive == true) return

        Log.d("AlarmReceiverService", "Screen ON — debouncing ${SCREEN_ON_DEBOUNCE_MS / 1000}s before reconnect")
        reconnectJob = serviceScope.launch {
            try {
                delay(SCREEN_ON_DEBOUNCE_MS)
            } catch (e: CancellationException) {
                Log.d("AlarmReceiverService", "Debounce cancelled (screen went off again)")
                return@launch
            }
            // The user may have turned the screen back off during the debounce.
            // PowerManager.isInteractive is cheap and always up-to-date.
            if (!isScreenInteractive()) {
                Log.d("AlarmReceiverService", "Screen turned off during debounce — abort reconnect")
                return@launch
            }
            Log.i("AlarmReceiverService", "Reconnecting after screen-on debounce")
            refreshForegroundNotification(pin, paused = false)
            startListening(pin)
        }
    }

    private fun isScreenInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun refreshForegroundNotification(pin: String, paused: Boolean) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildForegroundNotification(pin, paused))
    }

    // ── Streaming ────────────────────────────────────────────────────────

    private fun startListening(pin: String) {
        streamJob?.cancel()
        streamJob = serviceScope.launch {
            val topic = CryptoUtils.deriveTopic(pin)
            val sharedPref = applicationContext.getSharedPreferences("AlarmTossPref", Context.MODE_PRIVATE)
            val sinceKey = PREF_KEY_LAST_MSG_PREFIX + pin

            var retryDelay = 3_000L
            while (isActive) {
                try {
                    val lastMessageId = sharedPref.getString(sinceKey, null)
                    val url = buildString {
                        append("https://ntfy.sh/")
                        append(topic)
                        append("/json")
                        if (!lastMessageId.isNullOrEmpty()) {
                            append("?since=")
                            append(lastMessageId)
                        }
                    }

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
                                    handleStreamLine(jsonLine, pin, sinceKey)
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

    private fun handleStreamLine(jsonLine: String, pin: String, sinceKey: String) {
        try {
            val ntfyObj = JSONObject(jsonLine)
            val event = ntfyObj.optString("event")
            Log.d("AlarmReceiverService", "Stream event=$event size=${jsonLine.length}B")

            if (event == "message") {
                val rawMsg = ntfyObj.optString("message")
                val msgId = ntfyObj.optString("id", "")

                val decrypted = CryptoUtils.decrypt(rawMsg, pin)
                if (decrypted == null) {
                    Log.w("AlarmReceiverService", "Failed to decrypt incoming message (size=${rawMsg.length}); either PIN mismatch or injected garbage. Dropping.")
                    if (msgId.isNotEmpty()) {
                        applicationContext.getSharedPreferences("AlarmTossPref", Context.MODE_PRIVATE)
                            .edit().putString(sinceKey, msgId).apply()
                    }
                    return
                }

                val detailObj = JSONObject(decrypted)
                val title = detailObj.optString("title", "알림토스")
                val message = detailObj.optString("message", "")
                val packageName = detailObj.optString("packageName", "unknown")
                val appLabel = detailObj.optString("appLabel", "").ifBlank {
                    packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                }

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
                        if (msgId.isNotEmpty()) {
                            applicationContext.getSharedPreferences("AlarmTossPref", Context.MODE_PRIVATE)
                                .edit().putString(sinceKey, msgId).apply()
                        }
                    } catch (e: Exception) {
                        Log.e("AlarmReceiverService", "Failed to process received notification payload: ${e.message}", e)
                    }
                }
            } else if (event == "keepalive" || event == "open") {
                // ignore
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiverService", "Error unpacking message JSON: ${e.message}")
        }
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

    private fun buildForegroundNotification(pin: String, paused: Boolean = false): Notification {
        val resultIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (paused) "TossNoti 절전 대기 중" else "TossNoti 실시간 수신 작동 중"
        val text = if (paused) {
            "화면 켜면 자동 재연결 · PIN: $pin"
        } else {
            "기기 연동 PIN: $pin (다른 기기로부터 원격 알림 수신 중...)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
