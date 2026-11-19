package com.promptshift.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

data class RealmeReadinessState(
    val canDrawOverlays: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
)

object RealmeReadinessHelper {

    fun checkState(context: Context): RealmeReadinessState {
        val overlayGranted = Settings.canDrawOverlays(context)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        return RealmeReadinessState(
            canDrawOverlays = overlayGranted,
            isIgnoringBatteryOptimizations = batteryUnrestricted,
        )
    }

    fun openOverlayPermissionSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
        launchFirstAvailable(context, intents)
    }

    fun openBatteryAndBackgroundSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        // On Realme UI / ColorOS, App Info provides direct access to "Battery usage -> Allow background activity"
        // while standard Android battery optimization settings works across all devices.
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        launchFirstAvailable(context, intents)
    }

    private fun launchFirstAvailable(context: Context, intents: List<Intent>) {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Try next fallback
            }
        }
    }
}
