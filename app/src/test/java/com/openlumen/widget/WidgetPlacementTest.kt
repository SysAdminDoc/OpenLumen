package com.openlumen.widget

import com.google.common.truth.Truth.assertThat
import com.openlumen.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser

/**
 * Both provider files carried `android:configure=""`. The platform only checks
 * that attribute for null, so the empty string became a configure activity
 * with no class. The Pixel launcher then tried to run it, got no widget id
 * back, and deleted the widget the moment it was dropped: on Android 17 neither
 * widget could be placed at all.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetPlacementTest {

    private fun providerAttribute(xmlId: Int, name: String): String? {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(xmlId)
        parser.use {
            while (it.next() != XmlPullParser.END_DOCUMENT) {
                if (it.eventType == XmlPullParser.START_TAG && it.name == "appwidget-provider") {
                    return it.getAttributeValue(ANDROID_NS, name)
                }
            }
        }
        error("no appwidget-provider element")
    }

    @Test fun `neither widget asks the launcher for a configure activity`() {
        for (xml in listOf(R.xml.widget_toggle_info, R.xml.widget_preset_info)) {
            assertThat(providerAttribute(xml, "configure")).isNull()
        }
    }

    @Test fun `the provider files are read where the launcher reads them`() {
        // Positive control: an attribute that is there comes back, so the
        // null above is not just a parser that sees nothing.
        assertThat(providerAttribute(R.xml.widget_toggle_info, "widgetCategory")).isNotNull()
        assertThat(providerAttribute(R.xml.widget_preset_info, "widgetCategory")).isNotNull()
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
