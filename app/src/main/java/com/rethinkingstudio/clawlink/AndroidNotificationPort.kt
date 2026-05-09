package com.rethinkingstudio.clawlink

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.domain.NotificationPort

class AndroidNotificationPort(
    context: Context
) : NotificationPort {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        createChannel()
    }

    override fun showReplyNotification(sessionKey: String, title: String, body: String) {
        if (!notificationManager.areNotificationsEnabled()) return

        val notificationId = sessionKey.hashCode() and Int.MAX_VALUE
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("session_key", sessionKey)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    override fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    override fun cancelAll() {
        notificationManager.cancelAll()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            choose(CHANNEL_NAME, CHANNEL_NAME_ZH),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = choose(CHANNEL_DESCRIPTION, CHANNEL_DESCRIPTION_ZH)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "clawlink_replies"
        const val CHANNEL_NAME = "Reply notifications"
        const val CHANNEL_DESCRIPTION = "Notifications for new assistant replies"
        const val CHANNEL_NAME_ZH = "回复通知"
        const val CHANNEL_DESCRIPTION_ZH = "新助手回复通知"
    }
}
