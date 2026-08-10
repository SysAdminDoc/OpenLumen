package com.openlumen.service

import android.os.Process
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutomationReceiverTest {

    @Test fun appUidIsTrusted() {
        assertThat(
            AutomationReceiver.isTrustedCaller(
                callingUid = 10_001,
                appUid = 10_001,
                packages = arrayOf("com.example.app")
            )
        ).isTrue()
    }

    @Test fun adbShellAndRootAreTrusted() {
        assertThat(
            AutomationReceiver.isTrustedCaller(
                callingUid = Process.SHELL_UID,
                appUid = 10_001,
                packages = null
            )
        ).isTrue()
        assertThat(
            AutomationReceiver.isTrustedCaller(
                callingUid = Process.ROOT_UID,
                appUid = 10_001,
                packages = null
            )
        ).isTrue()
    }

    @Test fun documentedAutomationPackagesAreTrusted() {
        assertThat(
            AutomationReceiver.isTrustedCaller(
                callingUid = 20_001,
                appUid = 10_001,
                packages = arrayOf("net.dinglisch.android.taskerm")
            )
        ).isTrue()
        assertThat(
            AutomationReceiver.isTrustedCaller(
                callingUid = 20_002,
                appUid = 10_001,
                packages = arrayOf("com.termux")
            )
        ).isTrue()
    }

    @Test fun unknownPackageIsRejectedEvenIfItDeclaresThePermission() {
        assertThat(
            AutomationReceiver.isTrustedCaller(
                callingUid = 20_003,
                appUid = 10_001,
                packages = arrayOf("com.example.untrusted")
            )
        ).isFalse()
    }
}
