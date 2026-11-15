package com.openlumen.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openlumen.MainActivity
import com.openlumen.R

/**
 * The one notification automatic location needs. The device zone moved away
 * from the one the solar location was saved in, so sunset is probably being
 * computed for the wrong place. Nothing can read the location from the
 * background (that would need background location), so this asks the user to
 * open the app, where an automatic location refreshes itself and a manual one
 * can be changed.
 *
 * Same id each time: a second hop replaces the first. It is cancelled once
 * the stored zone matches the device again, so it never lingers saying the
 * location is stale after the user fixed it.
 */
internal object ZoneChangeNotice {

    internal const val NOTIFICATION_ID = 4244
    private const val TAG = "OpenLumen/ZoneNotice"

    fun post(context: Context, autoLocation: Boolean) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@runCatching
            val channelId = context.getString(R.string.notif_location_channel_id)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        context.getString(R.string.notif_location_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = context.getString(R.string.notif_location_channel_desc) }
                )
            }
            val text = context.getString(
                if (autoLocation) R.string.notif_zone_changed_auto else R.string.notif_zone_changed_manual
            )
            val tap = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            nm.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.notif_zone_changed_title))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            )
        }.onFailure { Log.w(TAG, "zone change notification: ${it.message}") }
    }

    fun cancel(context: Context) {
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancel(NOTIFICATION_ID)
        }
    }
}
