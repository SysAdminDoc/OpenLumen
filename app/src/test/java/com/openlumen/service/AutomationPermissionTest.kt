package com.openlumen.service

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.google.common.truth.Truth.assertThat
import com.openlumen.BuildConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * PR #21. The permission was declared as the literal
 * `com.openlumen.permission.AUTOMATION`, so the debug build
 * (`com.openlumen.debug`) declared the same name as an installed release and
 * the package manager refused to install it next to one
 * (INSTALL_FAILED_DUPLICATE_PERMISSION).
 *
 * These run against the debug variant's merged manifest, which is the one the
 * unit tests load. The release name is derived the same way from
 * `com.openlumen`, so it stays `com.openlumen.permission.AUTOMATION`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationPermissionTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun declaredPermissions(): List<PermissionInfo> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .permissions
            .orEmpty()
            .toList()

    @Test fun `the build declares the automation permission under its own application id`() {
        val names = declaredPermissions().map { it.name }

        assertThat(names).contains("${BuildConfig.APPLICATION_ID}.permission.AUTOMATION")
        if (BuildConfig.APPLICATION_ID != RELEASE_APPLICATION_ID) {
            // The collision itself: a debug build must not claim the
            // release's name.
            assertThat(names).doesNotContain("$RELEASE_APPLICATION_ID.permission.AUTOMATION")
        }
    }

    @Test fun `the filter state broadcast asks for the permission this build declares`() {
        // A broadcast scoped to a permission nobody declares reaches no one,
        // so the name the service sends with has to be one the manifest owns.
        val declared = declaredPermissions()
            .singleOrNull { it.name == LumenService.automationPermission(context) }

        assertThat(declared).isNotNull()
        assertThat(declared!!.protection).isEqualTo(PermissionInfo.PROTECTION_SIGNATURE)
    }

    private companion object {
        const val RELEASE_APPLICATION_ID = "com.openlumen"
    }
}
