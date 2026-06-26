package com.rethinkingstudio.clawlink.core.utils

import android.content.Context
import android.provider.Settings
import java.util.Locale
import java.util.UUID

object MobileDeviceId {
    private const val PREFS_NAME = "clawlink_device_identity"
    private const val DEVICE_ID_KEY = "mobile_device_id"

    fun resolve(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(DEVICE_ID_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return it
        }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotEmpty() && it != "9774d56d682e549c" }
        val created = if (androidId != null) {
            "android_$androidId"
        } else {
            "android_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        }
        prefs.edit().putString(DEVICE_ID_KEY, created).apply()
        return created
    }
}
