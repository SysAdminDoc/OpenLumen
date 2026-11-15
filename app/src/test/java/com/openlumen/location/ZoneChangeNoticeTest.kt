package com.openlumen.location

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.openlumen.R
import com.openlumen.service.ScheduleClockChangeReceiver
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * PR #26 (issue #25). The notice the clock receiver posts after a timezone
 * change, and the manifest promises the feature rests on: coarse location
 * only, and a receiver that only the system can reach.
 */
@RunWith(RobolectricTestRunner::class)
class ZoneChangeNoticeTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    @After fun clear() {
        nm.cancelAll()
    }

    private fun posted(): Notification? = shadowOf(nm).getNotification(ZoneChangeNotice.NOTIFICATION_ID)

    @Test fun `the notice asks for a new location when it is manual`() {
        ZoneChangeNotice.post(context, autoLocation = false)

        val n = checkNotNull(posted())
        assertThat(n.channelId).isEqualTo(context.getString(R.string.notif_location_channel_id))
        assertThat(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo(context.getString(R.string.notif_zone_changed_manual))
        assertThat(nm.getNotificationChannel(n.channelId).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test fun `a second hop replaces the notice instead of stacking`() {
        ZoneChangeNotice.post(context, autoLocation = false)
        ZoneChangeNotice.post(context, autoLocation = true)

        assertThat(shadowOf(nm).allNotifications).hasSize(1)
        assertThat(posted()!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo(context.getString(R.string.notif_zone_changed_auto))
    }

    @Test fun `cancel takes the notice down`() {
        ZoneChangeNotice.post(context, autoLocation = true)
        ZoneChangeNotice.cancel(context)

        assertThat(posted()).isNull()
    }

    @Test fun `the app asks for coarse location and never fine or background`() {
        @Suppress("DEPRECATION")
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()

        assertThat(requested).contains(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertThat(requested).doesNotContain(Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(requested).doesNotContain(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertThat(requested).doesNotContain("android.permission.INTERNET")
    }

    @Test fun `the clock receiver gets timezone changes and is not exported`() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_TIMEZONE_CHANGED).setPackage(context.packageName),
            0
        )
        val clock = receivers.single {
            it.activityInfo.name == ScheduleClockChangeReceiver::class.java.name
        }
        // Not exported: the system still delivers its own broadcasts, and no
        // other app can fake a zone change to post the notice.
        assertThat(clock.activityInfo.exported).isFalse()
        assertThat(clock.activityInfo.enabled).isTrue()
    }
}
