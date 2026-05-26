package com.example.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.AlarmTossApplication
import com.example.database.NotificationRepository
import com.example.relay.AppMetadataHelper
import com.example.relay.RelaySender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections

class AlarmListenerService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var repository: NotificationRepository

    // Dedup cache: notification signature → last-seen timestamp. Android frequently
    // re-posts the same notification (e.g. "메시지 1개" → "메시지 2개") on the same
    // sbn.key, so we suppress identical (pkg|title|text) triplets within DEDUP_TTL_MS.
    private val dedupCache: MutableMap<String, Long> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean = size > 64
        }
    )

    override fun onCreate() {
        super.onCreate()
        repository = (applicationContext as AlarmTossApplication).repository
        Log.i("AlarmListenerService", "AlarmListenerService Created")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Skip our own app notifications to avoid loop
        if (packageName == applicationContext.packageName) {
            return
        }

        val sharedPref = applicationContext.getSharedPreferences("AlarmTossPref", Context.MODE_PRIVATE)
        val isSenderMode = sharedPref.getBoolean("is_sender_mode", true)
        if (!isSenderMode) {
            return
        }

        val pin = sharedPref.getString("sender_pin", "") ?: ""
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            Log.w("AlarmListenerService", "Active PIN missing or malformed, skipping intercept!")
            return
        }

        val targetApps = sharedPref.getStringSet("target_apps", setOf("com.instagram.android"))
            ?: setOf("com.instagram.android")

        if (!(targetApps.contains(packageName) || targetApps.contains("all_apps"))) {
            return
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getString("android.title")
            ?: "새 알림"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getString("android.text")
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        val dedupKey = "$packageName|$title|$text"
        if (isRecentDuplicate(dedupKey)) {
            Log.d("AlarmListenerService", "Skipping duplicate notification: $dedupKey")
            return
        }

        Log.d("AlarmListenerService", "가로채기 성공! 패키지: $packageName, 제목: $title")

        scope.launch {
            val appLabel = AppMetadataHelper.loadLabel(applicationContext, packageName)
            RelaySender.sendNotification(
                title = title,
                text = text,
                packageName = packageName,
                pin = pin,
                repository = repository,
                appLabel = appLabel
            )
        }
    }

    private fun isRecentDuplicate(key: String, ttlMs: Long = 5_000L): Boolean {
        val now = System.currentTimeMillis()
        val last = dedupCache[key]
        if (last != null && now - last < ttlMs) {
            return true
        }
        dedupCache[key] = now
        return false
    }
}
