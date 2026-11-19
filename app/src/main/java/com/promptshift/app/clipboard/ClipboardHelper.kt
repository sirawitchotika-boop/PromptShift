package com.promptshift.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {

    fun writePlainText(context: Context, label: String, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readPlainText(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return null
            val clip = clipboard.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            val item = clip.getItemAt(0) ?: return null
            item.text?.toString() ?: item.coerceToText(context)?.toString()
        } catch (_: Exception) {
            null
        }
    }
}
