package unloop.launcher

import unloop.launcher.util.PermissionUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapToLockSettingTest {

    @Test
    fun testSamsungDoubleTapToSleep_enabled_returnsTrue() {
        val settings = mapOf("double_tap_to_sleep" to 1)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { key -> settings[key] }
        )
        assertTrue(result)
    }

    @Test
    fun testSamsungDoubleTapToSleep_disabled_returnsFalse() {
        val settings = mapOf("double_tap_to_sleep" to 0)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { key -> settings[key] }
        )
        assertFalse(result)
    }

    @Test
    fun testOnePlusDoubleTapToLock_enabled_returnsTrue() {
        val settings = mapOf("double_tap_to_lock" to 1)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { key -> settings[key] }
        )
        assertTrue(result)
    }

    @Test
    fun testOnePlusDoubleTapToLock_disabled_returnsFalse() {
        val settings = mapOf("double_tap_to_lock" to 0)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { key -> settings[key] }
        )
        assertFalse(result)
    }

    @Test
    fun testLineageOSDoubleTapSleepGesture_enabled_returnsTrue() {
        val settings = mapOf("double_tap_sleep_gesture" to 1)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { key -> settings[key] }
        )
        assertTrue(result)
    }

    @Test
    fun testLineageOSDoubleTapSleepGesture_disabled_returnsFalse() {
        val settings = mapOf("double_tap_sleep_gesture" to 0)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { key -> settings[key] }
        )
        assertFalse(result)
    }

    @Test
    fun testSecureSettingsFallback_enabled_returnsTrue() {
        val secureSettings = mapOf("double_tap_to_sleep" to 1)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { null },
            secureSettingsGetter = { key -> secureSettings[key] }
        )
        assertTrue(result)
    }

    @Test
    fun testSecureSettingsFallback_disabled_returnsFalse() {
        val secureSettings = mapOf("double_tap_to_sleep" to 0)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { null },
            secureSettingsGetter = { key -> secureSettings[key] }
        )
        assertFalse(result)
    }

    @Test
    fun testNoOEMSettingFound_defaultsToTrue() {
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { null },
            secureSettingsGetter = { null }
        )
        assertTrue(result)
    }

    @Test
    fun testUnrelatedSettings_defaultsToTrue() {
        val settings = mapOf("accelerometer_rotation" to 1, "screen_off_timeout" to 30000)
        val result = PermissionUtils.isDoubleTapToLockSettingEnabled(
            systemSettingsGetter = { key -> settings[key] }
        )
        assertTrue(result)
    }
}
