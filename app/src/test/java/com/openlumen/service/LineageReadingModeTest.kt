package com.openlumen.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * #32. LineageOS keeps reading mode in its own settings provider, which only
 * exists on LineageOS builds. These stand in for that provider in both of the
 * ways it can answer, and check the read stays quiet where it is missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LineageReadingModeTest {

    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver

    @Test fun `reading mode is read through the provider's call`() {
        FakeLineageSettings.rows = mapOf("system/display_reading_mode" to "1")
        FakeLineageSettings.answerCalls = true
        Robolectric.buildContentProvider(FakeLineageSettings::class.java).create("lineagesettings")

        val state = readSystemColorState(resolver)

        assertThat(state.readingModeActive).isEqualTo(1)
        assertThat(state.systemGrayscale).isTrue()
    }

    @Test fun `a provider that only answers queries is read the same way`() {
        FakeLineageSettings.rows = mapOf("system/display_reading_mode" to "1")
        FakeLineageSettings.answerCalls = false
        Robolectric.buildContentProvider(FakeLineageSettings::class.java).create("lineagesettings")

        assertThat(readLineageInt(resolver, SystemColorSettingsWatcher.KEY_LINEAGE_READING_MODE))
            .isEqualTo(1)
    }

    @Test fun `the secure table is checked when the system table has no row`() {
        FakeLineageSettings.rows = mapOf("secure/display_reading_mode" to "0")
        FakeLineageSettings.answerCalls = true
        Robolectric.buildContentProvider(FakeLineageSettings::class.java).create("lineagesettings")

        assertThat(readLineageInt(resolver, SystemColorSettingsWatcher.KEY_LINEAGE_READING_MODE))
            .isEqualTo(0)
    }

    @Test fun `off LineageOS there is no reading mode and no error`() {
        val state = readSystemColorState(resolver)

        assertThat(state.readingModeActive).isNull()
        assertThat(state.systemGrayscale).isFalse()
    }
}

/** Answers like LineageOS's settings provider: `GET_<table>` calls and item queries. */
class FakeLineageSettings : ContentProvider() {
    companion object {
        @Volatile var rows: Map<String, String> = emptyMap()
        @Volatile var answerCalls = true
    }

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!answerCalls || !method.startsWith("GET_")) return null
        val value = rows["${method.removePrefix("GET_")}/$arg"] ?: return null
        return Bundle().apply { putString("value", value) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val value = rows[uri.pathSegments.joinToString("/")] ?: return null
        return MatrixCursor(arrayOf("value")).apply { addRow(arrayOf(value)) }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ) = 0
}
