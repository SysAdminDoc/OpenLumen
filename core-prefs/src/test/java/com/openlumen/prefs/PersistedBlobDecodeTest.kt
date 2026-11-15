package com.openlumen.prefs

import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C309. Every migration test built a Preferences object, so nothing ever fed
 * the store a real persisted blob. That left the whole decode path unexercised:
 * v0 detection, the corruption quarantine, a future schema version, and the
 * token redaction on export.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistedBlobDecodeTest {

    private val context get() = RuntimeEnvironment.getApplication()
    // One instance per test: recovery is state on the store, so reading it
    // from a freshly constructed one always says null and the corruption
    // assertions pass without proving anything.
    private val store by lazy { PreferencesStore(context, setOf("night", "amber")) }

    private fun seed(raw: String) = runBlocking {
        context.dataStore.edit { it[stringPreferencesKey("prefs-v1")] = raw }
    }

    @Test fun `a blob written before schema versions existed is walked through every step`() {
        // The version is read off the raw JSON, not off the decoded object,
        // because kotlinx fills a missing field with the current default.
        // Reading it from the object would silently mark a pre-C29 blob as
        // already current and skip every migration, so the assertions here are
        // the migrations' own effects rather than the stamped number: 1 -> 2
        // rewrites a root engine recorded before the probe existed, and
        // 2 -> 3 closes the automation surface.
        seed(
            """{"activePresetKey":"amber","engine":"SurfaceFlinger",""" +
                """"automationEnabled":true,"automationToken":"${"d".repeat(32)}"}"""
        )

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.engine).isEqualTo(EngineKindDto.Auto)
        assertThat(loaded.automationEnabled).isFalse()
        assertThat(loaded.automationToken).isEmpty()
        assertThat(loaded.schemaVersion).isEqualTo(Preferences.CURRENT_SCHEMA_VERSION)
        assertThat(loaded.activePresetKey).isEqualTo("amber")
    }

    @Test fun `a blob that records the current version is not walked back through the steps`() {
        // Positive control for the case above: the steps have to fire on the
        // gap, not on every read, or a user who enables automation loses it on
        // the next launch.
        seed(
            """{"schemaVersion":3,"engine":"Kcal",""" +
                """"automationEnabled":true,"automationToken":"${"e".repeat(32)}"}"""
        )

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.engine).isEqualTo(EngineKindDto.Kcal)
        assertThat(loaded.automationEnabled).isTrue()
    }

    @Test fun `an upgrading blob reaches the app with the automation surface closed`() {
        seed(
            """{"schemaVersion":2,"automationEnabled":true,"automationToken":"${"a".repeat(32)}"}"""
        )

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.automationEnabled).isFalse()
        assertThat(loaded.automationToken).isEmpty()
    }

    @Test fun `a blob this build cannot parse leaves defaults running and a recovery record`() {
        seed("{ this is not json")

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded).isEqualTo(Preferences())
        val recovery = store.recovery.value
        assertThat(recovery).isNotNull()
        assertThat(recovery!!.errorType).isNotEmpty()
        assertThat(recovery.rawCharacterCount).isEqualTo("{ this is not json".length)
    }

    @Test fun `a blob from a newer build is left alone rather than rewritten`() {
        // Downgrade path: the app has no step for it, so it must not pretend
        // to migrate and must not throw.
        seed("""{"schemaVersion":99,"activePresetKey":"night"}""")

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.schemaVersion).isEqualTo(99)
        assertThat(loaded.activePresetKey).isEqualTo("night")
    }

    @Test fun `an export never carries the automation token`() {
        // A backup is meant to be shared. The token is the only thing between
        // a hostile local app and full control of the filter, so it must not
        // travel with the file.
        val token = "c".repeat(32)
        seed("""{"schemaVersion":3,"automationEnabled":true,"automationToken":"$token"}""")
        val out = ByteArrayOutputStream()
        val uri = Uri.parse("content://test/backup.json")
        shadowOf(context.contentResolver).registerOutputStream(uri, out)

        val result = runBlocking { store.exportTo(uri) }

        assertThat(result.isSuccess).isTrue()
        val body = out.toString(Charsets.UTF_8.name())
        assertThat(body).doesNotContain(token)
        assertThat(body).contains("\"automationEnabled\": false")
    }

    @Test fun `an export still carries the settings it is a backup of`() {
        // Positive control: redaction must remove the token, not the file.
        seed("""{"schemaVersion":3,"activePresetKey":"night","dim":0.25}""")
        val out = ByteArrayOutputStream()
        val uri = Uri.parse("content://test/backup2.json")
        shadowOf(context.contentResolver).registerOutputStream(uri, out)

        runBlocking { store.exportTo(uri) }

        assertThat(out.toString(Charsets.UTF_8.name())).contains("\"activePresetKey\": \"night\"")
    }

    @Test fun `a solar location saved before automatic location decodes as manual`() {
        // PR #26 added three schedule fields. A blob from an older build has
        // none of them and has to come back as a manual location with nothing
        // recorded, so the first zone change after updating does not notify.
        seed(
            """{"schemaVersion":3,"schedule":{"mode":"Solar",""" +
                """"latitude":52.2297,"longitude":21.0122}}"""
        )

        val schedule = runBlocking { store.flow.first() }.schedule

        assertThat(schedule.mode).isEqualTo(ScheduleModeDto.Solar)
        assertThat(schedule.latitude).isEqualTo(52.2297)
        assertThat(schedule.autoLocation).isFalse()
        assertThat(schedule.locationFixAtMs).isEqualTo(0L)
        assertThat(schedule.locationZoneId).isNull()
    }

    @Test fun `automatic location bookkeeping from a hand-edited blob is cleaned`() {
        seed(
            """{"schemaVersion":3,"schedule":{"mode":"Solar","latitude":52.2297,""" +
                """"longitude":21.0122,"autoLocation":true,"locationFixAtMs":-5,""" +
                """"locationZoneId":"Mars/Olympus"}}"""
        )

        val schedule = runBlocking { store.flow.first() }.schedule

        assertThat(schedule.autoLocation).isTrue()
        assertThat(schedule.locationFixAtMs).isEqualTo(0L)
        assertThat(schedule.locationZoneId).isNull()
    }

    @Test fun `a schedule saved before the day filter decodes with it off`() {
        // PR #27 added three more schedule fields. An existing schedule has to
        // keep switching off outside its window after the update.
        seed(
            """{"schemaVersion":3,"schedule":{"mode":"FixedTime","startHour":20,""" +
                """"startMinute":0,"endHour":7,"endMinute":0}}"""
        )

        val schedule = runBlocking { store.flow.first() }.schedule

        assertThat(schedule.mode).isEqualTo(ScheduleModeDto.FixedTime)
        assertThat(schedule.dayFilter).isFalse()
        assertThat(schedule.dayKelvin).isEqualTo(ScheduleDto.DEFAULT_DAY_KELVIN)
        assertThat(schedule.dayDim).isEqualTo(0f)
    }

    @Test fun `day filter values from a hand-edited blob are clamped on read`() {
        seed(
            """{"schemaVersion":3,"schedule":{"mode":"FixedTime","startHour":20,""" +
                """"endHour":7,"dayFilter":true,"dayKelvin":100,"dayDim":7.5}}"""
        )

        val schedule = runBlocking { store.flow.first() }.schedule

        assertThat(schedule.dayFilter).isTrue()
        assertThat(schedule.dayKelvin).isEqualTo(ScheduleDto.DAY_KELVIN_MIN)
        assertThat(schedule.dayDim).isEqualTo(ScheduleDto.DAY_DIM_MAX)
    }

    @Test fun `an export and import carry the day filter, in a saved profile too`() {
        val day = ScheduleDto(
            mode = ScheduleModeDto.FixedTime,
            startHour = 20,
            endHour = 7,
            dayFilter = true,
            dayKelvin = 5500,
            dayDim = 0.2f
        )
        runBlocking {
            store.update { current ->
                Profiles.saveCurrentAs(current.copy(schedule = day), "Workday")
            }
        }
        val out = ByteArrayOutputStream()
        val exportUri = Uri.parse("content://test/day-export.json")
        shadowOf(context.contentResolver).registerOutputStream(exportUri, out)
        runBlocking { store.exportTo(exportUri) }

        // Wipe the live schedule, then bring the file back. The profile entry
        // is edited out of range on the way to prove imports clamp it.
        runBlocking { store.update { it.copy(schedule = ScheduleDto(), savedProfiles = emptyList()) } }
        val exported = out.toString(Charsets.UTF_8.name())
        val tampered = exported.replaceFirst(
            Regex("(\"savedProfiles\"[\\s\\S]*?\"dayKelvin\": )5500"),
            "$199999"
        )
        assertThat(tampered).isNotEqualTo(exported)
        val importUri = Uri.parse("content://test/day-import.json")
        shadowOf(context.contentResolver)
            .registerInputStream(importUri, tampered.byteInputStream(Charsets.UTF_8))
        val result = runBlocking { store.importFrom(importUri) }

        assertThat(result.isSuccess).isTrue()
        val loaded = runBlocking { store.flow.first() }
        assertThat(loaded.schedule.dayFilter).isTrue()
        assertThat(loaded.schedule.dayKelvin).isEqualTo(5500)
        assertThat(loaded.schedule.dayDim).isEqualTo(0.2f)
        val profile = loaded.savedProfiles.single { it.name == "Workday" }.snapshot.schedule
        assertThat(profile.dayFilter).isTrue()
        assertThat(profile.dayKelvin).isEqualTo(ScheduleDto.DAY_KELVIN_MAX)
    }

    @Test fun `a good blob leaves no recovery record`() {
        // Positive control for the corruption case: the record has to come
        // from the failure, not from every read. Same store instance, so a
        // record appearing here would be this read's doing.
        seed("""{"schemaVersion":3,"activePresetKey":"night"}""")

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.activePresetKey).isEqualTo("night")
        assertThat(store.recovery.value).isNull()
    }
}
