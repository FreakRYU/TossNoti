package com.example.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Queries the device for user-visible apps (those that appear in the launcher).
 *
 * Pattern adapted from common Android open-source app pickers (e.g. KDE Connect,
 * Pushbullet-like notification relayers): use the LAUNCHER intent to get apps
 * that have a UI, exclude our own package, dedupe by package name, and sort by
 * label. This avoids needing the privileged QUERY_ALL_PACKAGES permission as
 * long as the manifest declares a matching <queries> entry.
 */
class InstalledAppsRepository(private val context: Context) {

    fun queryLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvers = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val seen = HashSet<String>()
        val result = ArrayList<InstalledApp>(resolvers.size)
        for (info in resolvers) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            val label = try {
                info.loadLabel(pm)?.toString().orEmpty()
            } catch (_: Exception) {
                pkg
            }.ifBlank { pkg }
            val icon = try {
                info.loadIcon(pm)
            } catch (_: Exception) {
                pm.defaultActivityIcon
            }
            result.add(InstalledApp(packageName = pkg, label = label, icon = icon))
        }
        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        return result
    }
}
