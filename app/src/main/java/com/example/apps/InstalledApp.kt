package com.example.apps

import android.graphics.drawable.Drawable

/**
 * A user-visible launchable app on the device.
 * Used by the app picker to present targets the user can intercept.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)
