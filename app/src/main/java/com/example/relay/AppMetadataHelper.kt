package com.example.relay

import android.content.Context

/**
 * Resolves the source app's display label off the sender device via
 * PackageManager. Used by AlarmListenerService and RelaySender so the
 * intercepted notification can be labelled with the actual app name
 * (e.g. "Instagram", "카카오톡") even though the receiver device doesn't
 * have those apps installed.
 *
 * Falls back to the last segment of the package name if the label lookup
 * fails for any reason (uninstalled, hidden, no metadata, etc.).
 */
object AppMetadataHelper {

    fun loadLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
}
