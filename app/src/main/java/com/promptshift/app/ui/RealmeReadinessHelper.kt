package com.promptshift.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

data class RealmeReadinessState(
    val canDrawOverlays: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val isPromptShiftKeyboardEnabled: Boolean = false,
    val isPromptShiftKeyboardActive: Boolean = false,
)

object RealmeReadinessHelper {

    fun checkState(context: Context): RealmeReadinessState {
        val overlayGranted = Settings.canDrawOverlays(context)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = try {
            imm?.enabledInputMethodList.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val imeEnabled = enabledList.any { it.packageName == context.packageName }
        val defaultIme = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        } catch (_: Exception) {
            ""
        }
        val imeActive = defaultIme.startsWith("${context.packageName}/")

        return RealmeReadinessState(
            canDrawOverlays = overlayGranted,
            isIgnoringBatteryOptimizations = batteryUnrestricted,
            isPromptShiftKeyboardEnabled = imeEnabled,
            isPromptShiftKeyboardActive = imeActive,
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
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        launchFirstAvailable(context, intents)
    }

    fun openInputMethodSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        launchFirstAvailable(context, intents)
    }

    fun showInputMethodPicker(context: Context) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        } catch (_: Exception) {
        }
    }

    private fun launchFirstAvailable(context: Context, intents: List<Intent>) {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }
}
