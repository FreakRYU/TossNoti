package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.database.AlarmTossDatabase
import com.example.database.NotificationRepository
import java.util.concurrent.atomic.AtomicBoolean

class AlarmTossApplication : Application() {

    val database: AlarmTossDatabase by lazy { AlarmTossDatabase.getDatabase(this) }
    val repository: NotificationRepository by lazy { NotificationRepository(database.notificationDao) }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler(this)
    }
}

private val crashHandlerInstalled = AtomicBoolean(false)

fun installCrashHandler(context: Context) {
    if (!crashHandlerInstalled.compareAndSet(false, true)) return

    val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    val appContext = context.applicationContext

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            Log.e("AlarmTossCrash", "UNCAUGHT CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            val sharedPref = appContext.getSharedPreferences("AlarmTossPref", Context.MODE_PRIVATE)
            val stackTrace = Log.getStackTraceString(throwable)
            sharedPref.edit()
                .putString(
                    "last_crash_log",
                    "Thread: ${thread.name}\nException: ${throwable.localizedMessage}\n\n$stackTrace"
                )
                .commit()
        } catch (_: Exception) {
            // best-effort
        }
        originalHandler?.uncaughtException(thread, throwable)
    }
}
