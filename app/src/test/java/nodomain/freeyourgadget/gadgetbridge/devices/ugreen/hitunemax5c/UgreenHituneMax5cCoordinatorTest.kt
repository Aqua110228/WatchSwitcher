package nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_DUAL_CONNECT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_GAME_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_HIGH_QUALITY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ScreenSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.SwitchSetting
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UgreenHituneMax5cCoordinatorTest {

    @Test
    fun testSupportsOSBatteryLevel() {
        val coordinator = UgreenHituneMax5cCoordinator()
        val device = GBDevice("00:11:22:33:44:55", "UGREEN HiTune Max 5c", null, null, DeviceType.UGREEN_HITUNE_MAX_5C)
        assertTrue(coordinator.supportsOSBatteryLevel(device))
    }

    @Test
    fun testCustomActionsRegistered() {
        val coordinator = UgreenHituneMax5cCoordinator()
        val actions = coordinator.getCustomActions()
        assertEquals(2, actions.size)

        val ancAction = actions[0]
        val eqAction = actions[1]
        assertNotNull(ancAction)
        assertNotNull(eqAction)
    }

    @Test
    fun testAncShortLabels() {
        assertEquals(R.string.off, UgreenAncMode.OFF.shortLabel)
        assertEquals(R.string.redmi_buds_5_pro_anc_light, UgreenAncMode.LIGHT.shortLabel)
        assertEquals(R.string.ugreen_anc_mode_medium, UgreenAncMode.MEDIUM.shortLabel)
        assertEquals(R.string.redmi_buds_5_pro_anc_deep, UgreenAncMode.DEEP.shortLabel)
        assertEquals(R.string.prefs_active_noise_cancelling_adaptive, UgreenAncMode.ADAPTIVE.shortLabel)
        assertEquals(R.string.prefs_active_noise_cancelling_transparency, UgreenAncMode.TRANSPARENCY.shortLabel)
    }

    @Test
    fun testEqualizerCyclingOrder() {
        val entries = UgreenEqualizer.entries
        assertEquals(8, entries.size)
        assertEquals(UgreenEqualizer.CLASSIC, entries[0])
        assertEquals(UgreenEqualizer.POP, entries[1])
        assertEquals(UgreenEqualizer.BASS, entries[2])
        assertEquals(UgreenEqualizer.JAZZ, entries[3])
        assertEquals(UgreenEqualizer.ELECTRONIC, entries[4])
        assertEquals(UgreenEqualizer.ROCK, entries[5])
        assertEquals(UgreenEqualizer.TREBLE, entries[6])
        assertEquals(UgreenEqualizer.CLASSICAL, entries[7])

        for (i in entries.indices) {
            val next = entries[(i + 1) % entries.size]
            assertEquals(entries[(i + 1) % entries.size], next)
        }
    }

    private fun getNextAncMode(current: UgreenAncMode, lastActive: UgreenAncMode?): UgreenAncMode {
        return when (current) {
            UgreenAncMode.OFF -> lastActive?.takeIf { it != UgreenAncMode.OFF && it != UgreenAncMode.TRANSPARENCY }
                ?: UgreenAncMode.ADAPTIVE
            UgreenAncMode.TRANSPARENCY -> UgreenAncMode.OFF
            UgreenAncMode.ADAPTIVE,
            UgreenAncMode.DEEP,
            UgreenAncMode.MEDIUM,
            UgreenAncMode.LIGHT -> UgreenAncMode.TRANSPARENCY
        }
    }

    @Test
    fun testAncModeTransitionLogic() {
        // From OFF -> default ADAPTIVE
        var current = UgreenAncMode.OFF
        var lastActive: UgreenAncMode? = null
        var next = getNextAncMode(current, lastActive)
        assertEquals(UgreenAncMode.ADAPTIVE, next)

        // From ADAPTIVE -> TRANSPARENCY
        current = next
        if (current != UgreenAncMode.OFF && current != UgreenAncMode.TRANSPARENCY) {
            lastActive = current
        }
        next = getNextAncMode(current, lastActive)
        assertEquals(UgreenAncMode.TRANSPARENCY, next)

        // From TRANSPARENCY -> OFF
        current = next
        next = getNextAncMode(current, lastActive)
        assertEquals(UgreenAncMode.OFF, next)

        // From OFF with lastActive set to DEEP -> DEEP
        lastActive = UgreenAncMode.DEEP
        current = UgreenAncMode.OFF
        next = getNextAncMode(current, lastActive)
        assertEquals(UgreenAncMode.DEEP, next)
    }

    @Test
    fun testSettingsLdacDependencyConfiguration() {
        val coordinator = UgreenHituneMax5cCoordinator()
        val device = GBDevice("00:11:22:33:44:55", "UGREEN HiTune Max 5c", null, null, DeviceType.UGREEN_HITUNE_MAX_5C)
        val spec = coordinator.getDeviceSettings(device)

        val soundScreen = spec.findScreen(DeviceSpecificSettingsScreen.SOUND.key)
        assertNotNull(soundScreen)

        val switchSettings = soundScreen!!.children.filterIsInstance<SwitchSetting>()
        val hqSetting = switchSettings.firstOrNull { it.key == PREF_UGREEN_HIGH_QUALITY }
        val gameSetting = switchSettings.firstOrNull { it.key == PREF_UGREEN_GAME_MODE }
        val dualSetting = switchSettings.firstOrNull { it.key == PREF_UGREEN_DUAL_CONNECT }

        assertNotNull(hqSetting)
        assertNotNull(gameSetting)
        assertNotNull(dualSetting)

        assertTrue(hqSetting!!.disableDependentsState)
        assertEquals(PREF_UGREEN_HIGH_QUALITY, gameSetting!!.dependency)
        assertEquals(PREF_UGREEN_HIGH_QUALITY, dualSetting!!.dependency)

        val hqIndex = soundScreen.children.indexOf(hqSetting)
        val gameIndex = soundScreen.children.indexOf(gameSetting)
        val dualIndex = soundScreen.children.indexOf(dualSetting)
        assertTrue(hqIndex < gameIndex)
        assertTrue(hqIndex < dualIndex)
    }
}
