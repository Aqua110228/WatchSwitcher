package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi_hipee

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState

class XiaomiHipeeP1SupportTest {
    @Test
    fun encodesPairingCommandsFromCapture() {
        assertArrayEquals(
            byteArrayOf(0x09, 0x01, 0x01, 0xf5.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x01),
        )
        assertArrayEquals(
            byteArrayOf(0x09, 0x01, 0x03, 0xf3.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x03),
        )
    }

    @Test
    fun encodesVariableCapturedCommand() {
        assertArrayEquals(
            byteArrayOf(0x09, 0x05, 0x05, 0x6a, 0xa5.toByte(), 0x49, 0x04, 0x91.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x05, 0x6a, 0xa5.toByte(), 0x49, 0x04),
        )
    }

    @Test
    fun encodesAndDecodesTimestampSynchronization() {
        // Math.round(new Date / 1000) after 0x04 status response.
        val timestamp = 0x6aa54904L
        assertArrayEquals(
            byteArrayOf(0x09, 0x05, 0x05, 0x6a, 0xa5.toByte(), 0x49, 0x04, 0x91.toByte()),
            XiaomiHipeeP1Support.encodeTimestamp(timestamp),
        )
        assertEquals(
            timestamp,
            XiaomiHipeeP1Support.parseTimestampAcknowledgement(payload(
                byteArrayOf(0x09, 0x05, 0x06, 0x6a, 0xa5.toByte(), 0x49, 0x04, 0x90.toByte()),
            )),
        )
    }

    @Test
    fun encodesFirmware27DoubleReminderFromInterval() {
        // The interval is the source of truth: nonzero enables and zero disables 0x50.
        assertArrayEquals(
            byteArrayOf(0x09, 0x06, 0x50, 0x01, 0x00, 0x00, 0x1e, 0x01, 0x81.toByte()),
            XiaomiHipeeP1Support.encodeDoubleReminder(30),
        )
        assertArrayEquals(
            byteArrayOf(0x09, 0x06, 0x50, 0x00, 0x00, 0x00, 0x00, 0x01, 0xa0.toByte()),
            XiaomiHipeeP1Support.encodeDoubleReminder(0),
        )
    }

    @Test
    fun encodesCapturedPostureCommands() {
        assertArrayEquals(
            byteArrayOf(0x09, 0x01, 0x32, 0xc4.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x32),
        )
        assertArrayEquals(
            byteArrayOf(0x09, 0x04, 0x34, 0x00, 0x14, 0x01, 0xaa.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x34, 0x00, 0x14, 0x01),
        )
        assertArrayEquals(
            byteArrayOf(0x09, 0x04, 0x34, 0x00, 0x14, 0x00, 0xab.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x34, 0x00, 0x14, 0x00),
        )
    }

    @Test
    fun encodesCapturedStoredReadingsRequests() {
        assertArrayEquals(
            byteArrayOf(0x09, 0x02, 0x36, 0x03, 0xbc.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x36, 0x03),
        )
        assertArrayEquals(
            byteArrayOf(0x09, 0x02, 0x36, 0x01, 0xbe.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x36, 0x01),
        )
        assertArrayEquals(
            byteArrayOf(0x09, 0x02, 0x36, 0x02, 0xbd.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x36, 0x02),
        )
        assertArrayEquals(
            byteArrayOf(0x09, 0x02, 0x36, 0x00, 0xbf.toByte()),
            XiaomiHipeeP1Support.encodeCommand(0x36, 0x00),
        )
    }

    @Test
    fun encodesCaptureBackedReminderSettings() {
        assertArrayEquals(
            // 5° was sampled with the normal 3 s delay.
            byteArrayOf(0x09, 0x10, 0x30, 0x01, 0x64, 0x05, 0x0f, 0x01, 0xf4.toByte(), 0x07, 0xd0.toByte(), 0x0e, 0x10, 0x00, 0x03, 0x00, 0x0a, 0x0f, 0x38),
            XiaomiHipeeP1Support.encodeReminderSettings(5, 3, false),
        )
        assertArrayEquals(
            // The 20 s sample occurs after 133 s and retains the default 10° angle.
            byteArrayOf(0x09, 0x10, 0x30, 0x01, 0x64, 0x0a, 0x0f, 0x01, 0xf4.toByte(), 0x07, 0xd0.toByte(), 0x0e, 0x10, 0x00, 0x14, 0x00, 0x0a, 0x0f, 0x22),
            XiaomiHipeeP1Support.encodeReminderSettings(10, 20, false),
        )
    }

    @Test
    fun encodesSourceBackedReminderModesAndDurations() {
        assertArrayEquals(
            byteArrayOf(
                0x09, 0x10, 0x30, 0x00, 0x64, 0x14, 0x0f, 0x01, 0xf4.toByte(),
                0x07, 0xd0.toByte(), 0x38, 0x40, 0x00, 0x05, 0x00, 0x14, 0x0f, 0xc4.toByte(),
            ),
            XiaomiHipeeP1Support.encodeReminderSettings(
                angleDegrees = 20,
                delaySeconds = 5,
                vibrationDisabled = false,
                longVibration = true,
                exerciseAngleDegrees = 20,
                sedentaryMinutes = 240,
            ),
        )
        assertArrayEquals(
            byteArrayOf(
                0x09, 0x10, 0x30, 0x01, 0x64, 0x0a, 0x0f, 0x01, 0xf4.toByte(),
                0x07, 0xd0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x00, 0x0a, 0x0f, 0x51,
            ),
            XiaomiHipeeP1Support.encodeReminderSettings(
                angleDegrees = 10,
                delaySeconds = 3,
                vibrationDisabled = false,
                longVibration = false,
                exerciseAngleDegrees = 10,
                sedentaryMinutes = 0,
            ),
        )
    }

    @Test
    fun decodesEveryRawFieldOfStoredReading() {
        val frame = XiaomiHipeeP1Support.encodeCommand(
            0x37, 0x02, 0x12, 0x34, 0xab.toByte(), 0xcd.toByte(),
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x2a, 0x8f.toByte(),
        )
        val payload = requireNotNull(XiaomiHipeeP1Support.decodeCommand(frame))
        val record = requireNotNull(XiaomiHipeeP1Support.parseReadingRecord(payload))

        assertEquals(2, record.type)
        assertEquals(0x1234, record.dataNum)
        assertEquals(0xabcd, record.dataCount)
        assertEquals(0x01020304L, record.startupTime)
        assertEquals(0x05060708L, record.time)
        assertEquals(0x2a, record.forwardAngle)
        assertEquals(0x8f, record.bankAngle)
        assertEquals(15, record.leftAngle)
        assertEquals(0, record.rightAngle)
    }

    @Test
    fun retainsRightBankAngleAndExcludesTransportControlRows() {
        val right = XiaomiHipeeP1Support.parseReadingRecord(requireNotNull(
            XiaomiHipeeP1Support.decodeCommand(XiaomiHipeeP1Support.encodeCommand(
                0x37, 0x01, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 20, 63,
            )),
        ))
        assertEquals(0, right?.leftAngle)
        assertEquals(63, right?.rightAngle)
        for (type in listOf(0x00, 0x04, 0x05)) {
            assertEquals(null, XiaomiHipeeP1Support.parseReadingRecord(requireNotNull(
                XiaomiHipeeP1Support.decodeCommand(XiaomiHipeeP1Support.encodeCommand(
                    0x37, type.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                )),
            )))
        }
    }

    @Test
    fun retainsZeroValuedPostureReading() {
        val record = XiaomiHipeeP1Support.parseReadingRecord(requireNotNull(
            XiaomiHipeeP1Support.decodeCommand(XiaomiHipeeP1Support.encodeCommand(
                0x37, 0x01, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0,
            )),
        ))

        requireNotNull(record)
        assertEquals(0, record.forwardAngle)
        assertEquals(0, record.bankAngle)
    }

    @Test
    fun decodesStoredPostureRowsFromSecondCapture() {
        val first = XiaomiHipeeP1Support.parseReadingRecord(payload(
            byteArrayOf(0x09, 0x10, 0x37, 0x01, 0x00, 0x02, 0x00, 0x01,
                0x6a, 0xa7.toByte(), 0xef.toByte(), 0xbd.toByte(), 0x00, 0x00, 0x05, 0x22, 0x00, 0x00, 0xc8.toByte()),
        ))
        assertEquals(1, first?.type)
        assertEquals(2, first?.dataNum)
        assertEquals(1, first?.dataCount)
        assertEquals(0x6aa7efbdL, first?.startupTime)
        assertEquals(0x522L, first?.time)

        val posture = XiaomiHipeeP1Support.parseReadingRecord(payload(
            byteArrayOf(0x09, 0x10, 0x37, 0x02, 0x00, 0x07, 0x00, 0x02,
                0x6a, 0xa7.toByte(), 0xf4.toByte(), 0x58, 0x00, 0x00, 0x00, 0x01, 0x15, 0x80.toByte(), 0xb2.toByte()),
        ))
        assertEquals(2, posture?.type)
        assertEquals(7, posture?.dataNum)
        assertEquals(2, posture?.dataCount)
        assertEquals(0x6aa7f458L, posture?.startupTime)
        assertEquals(1L, posture?.time)
        assertEquals(0x15, posture?.forwardAngle)
        assertEquals(0x80, posture?.bankAngle)
        assertEquals(0, posture?.leftAngle)
        assertEquals(0, posture?.rightAngle)
    }

    @Test
    fun decodesCaptureBackedReminderSettings() {
        val settings = XiaomiHipeeP1Support.parseReminderSettings(payload(
            byteArrayOf(0x09, 0x10, 0x47, 0x01, 0x64, 0x0a, 0x00, 0x01, 0xf4.toByte(), 0x07, 0xd0.toByte(), 0x0e, 0x10, 0x00, 0x03, 0x00, 0x0a, 0x00, 0x3a),
        ))

        val decodedSettings = requireNotNull(settings)
        assertEquals(10, decodedSettings.angleDegrees)
        assertFalse(decodedSettings.longVibration)
        assertEquals(10, decodedSettings.exerciseAngleDegrees)
        assertEquals(60, decodedSettings.sedentaryMinutes)
        assertEquals(3, decodedSettings.delaySeconds)
        assertFalse(decodedSettings.vibrationDisabled)
    }

    @Test
    fun decodesDoubleReminderDisabledStatus() {
        assertFalse(
            XiaomiHipeeP1Support.parseDoubleReminderStatus(payload(
                byteArrayOf(0x09, 0x06, 0x51, 0x00, 0x00, 0x00, 0x00, 0x00, 0xa0.toByte()),
            ))!!,
        )
        assertTrue(
            XiaomiHipeeP1Support.parseDoubleReminderStatus(payload(
                byteArrayOf(0x09, 0x06, 0x51, 0x01, 0x00, 0x22, 0x00, 0x00, 0x7d),
            ))!!,
        )
    }

    @Test
    fun rejectsReminderSettingsOutsideTheCapturedOptions() {
        assertEquals(
            null,
            XiaomiHipeeP1Support.parseReminderSettings(payload(
                byteArrayOf(0x09, 0x10, 0x47, 0x01, 0x64, 0x07, 0x00, 0x01, 0xf4.toByte(), 0x07, 0xd0.toByte(), 0x0e, 0x10, 0x00, 0x03, 0x00, 0x0a, 0x00, 0x3d),
            )),
        )
    }

    @Test
    fun recognizesFramedBindingNotifications() {
        val buttonConfirmation = payload(byteArrayOf(0x09, 0x02, 0x02, 0x01, 0xf2.toByte()))
        assertEquals(0x02.toByte(), buttonConfirmation.command)
        assertEquals(0x01.toByte(), buttonConfirmation[0])
        assertEquals(listOf(0x01.toByte()), buttonConfirmation.toList())
        assertTrue(
            XiaomiHipeeP1Support.isBindingConfirmation(
                payload(byteArrayOf(0x09, 0x08, 0x04, 0x2b, 0x00, 0x1d, 0x00, 0x00, 0x03, 0xff.toByte(), 0xa1.toByte())),
            ),
        )
        assertFalse(XiaomiHipeeP1Support.isValidCommandFrame(byteArrayOf(0x09, 0x02, 0x02, 0x01, 0xf3.toByte())))
        // The checksum is valid, but the declared two-byte payload is actually one byte.
        assertFalse(XiaomiHipeeP1Support.isValidCommandFrame(byteArrayOf(0x09, 0x02, 0x01, 0xf4.toByte())))
    }

    @Test
    fun decodesCapturedChargingStatus() {
        val status = XiaomiHipeeP1Support.parseChargingStatus(payload(
            byteArrayOf(0x09, 0x03, 0x45, 0x1a, 0x02, 0x93.toByte()),
        ))

        assertEquals(26, status?.level)
        assertEquals(BatteryState.BATTERY_NORMAL, status?.state)
    }

    @Test
    fun mapsFinishedChargingStatus() {
        val status = XiaomiHipeeP1Support.parseChargingStatus(requireNotNull(
            XiaomiHipeeP1Support.decodeCommand(XiaomiHipeeP1Support.encodeCommand(0x45, 0x00, 0x01)),
        ))

        assertEquals(BatteryState.BATTERY_CHARGING_FULL, status?.state)
    }

    @Test
    fun chargingNotificationSuppliesUpdatedBatteryLevelAndState() {
        val chargingStatus = XiaomiHipeeP1Support.parseChargingStatus(payload(
            byteArrayOf(0x09, 0x03, 0x45, 0x55, 0x00, 0x5a),
        ))

        assertEquals(85, chargingStatus?.level)
        assertEquals(BatteryState.BATTERY_CHARGING, chargingStatus?.state)
    }

    @Test
    fun rejectsChargingNotificationWithInvalidPercentage() {
        val chargingStatus = XiaomiHipeeP1Support.parseChargingStatus(payload(
            byteArrayOf(0x09, 0x03, 0x45, 0x65, 0x02, 0x48),
        ))

        assertEquals(null, chargingStatus)
    }

    @Test
    fun decodesCapturedDeviceStatus() {
        val status = XiaomiHipeeP1Support.parseDeviceStatus(payload(
            byteArrayOf(0x09, 0x08, 0x04, 0x1a, 0x00, 0x1d, 0x00, 0x00, 0x03, 0xff.toByte(), 0xb2.toByte()),
        ))

        assertEquals(26, status?.batteryLevel)
        assertEquals("V0.29", status?.firmwareVersion)
        assertEquals(1023L, status?.freeStorageRaw)
        assertEquals(99, status?.freeStoragePercent)
        assertEquals("V0.29", XiaomiHipeeP1Support.parseFirmwareVersion(payload(
            byteArrayOf(0x09, 0x08, 0x04, 0x1a, 0x00, 0x1d, 0x00, 0x00, 0x03, 0xff.toByte(), 0xb2.toByte()),
        )))
    }

    @Test
    fun capsDeviceStatusFreeStoragePercentAtOneHundred() {
        val status = XiaomiHipeeP1Support.parseDeviceStatus(payload(
            byteArrayOf(0x09, 0x08, 0x04, 0x00, 0x01, 0x00, 0x00, 0x00, 0x04, 0x00, 0xe6.toByte()),
        ))

        assertEquals("V0.256", status?.firmwareVersion)
        assertEquals(1024L, status?.freeStorageRaw)
        assertEquals(100, status?.freeStoragePercent)
    }

    @Test
    fun decodesCapturedLivePosture() {
        assertEquals(
            1.0,
            XiaomiHipeeP1Support.parseLivePosture(payload(
                byteArrayOf(0x09, 0x11, 0x35, 0x00, 0x00, 0x03, 0x8a.toByte(), 0x01, 0x82.toByte(), 0x00, 0x00, 0x00, 0x7a, 0x00, 0x00, 0x00, 0x00, 0x00, 0x72, 0xb5.toByte()),
            )),
        )
    }

    @Test
    fun acceptsCaptureProvenStandardPostureResults() {
        val validFrames = listOf(
            "090433f7c017f2", "090433f9cb23d9", "090433f5ba0f02", "090433facf27d0",
            "09043304b811f3", "09043308cd25c6", "09043305ca22cf", "09043305b810f3",
            "09043306ae020a", "09043305b50df9", "09043304c11ae1", "09043304c821d3",
            "09043305ce26c7", "09043306d129c0", "09043304b810f4", "090433f6d1d821",
        )

        validFrames.forEach { frame ->
            assertTrue(
                "Expected capture result $frame to be accepted",
                XiaomiHipeeP1Support.parseStandardPostureResult(payload(frame.hexToByteArray()))?.success == true,
            )
        }

        val finalResult = XiaomiHipeeP1Support.parseStandardPostureResult(payload("090433f6d1d821".hexToByteArray()))
        assertEquals(0xf6, finalResult?.code)
        assertEquals(-47, finalResult?.calibrationDecision)
        assertEquals(0xd8, finalResult?.rawThirdByte)
    }

    @Test
    fun rejectsEveryOtherStandardPostureResultFromTheFullCapture() {
        val invalidFrames = listOf(
            "090433053f1963", "090433063c1c62", "09043306352461", "090433062f2962",
            "090433052a2e63", "09043306263262", "09043306203862", "09043304193f64",
            "09043302114766", "090433020c4d65", "09043306352461", "090433053c1c63",
            "09043306352362", "090433062d2b62", "09043306283161", "09043302263365",
            "0904330c32245e", "0904330c3b1b5e", "0904330d490862", "090433094ffc6c",
            "0904330b4bfa70", "090433064e0567", "090433f241f697", "090433072fd7b3",
            "0904330723cbcb", "090433ff24ccd1", "0904330c3be891", "0904330850fe6a",
            "0904330549f181", "0904330433dbae", "0904330219c0e5", "0904330528d0c3",
            "090433041bc2df", "0904330109b006", "090433fcddca1d", "090433f7e6c320",
            "090433fbecbb1e", "090433ff07ae0c", "090433f7d6d221", "09043308213760",
            "09043307d72fb3",
        )

        invalidFrames.forEach { frame ->
            assertFalse(
                "Expected capture result $frame to be rejected",
                XiaomiHipeeP1Support.parseStandardPostureResult(payload(frame.hexToByteArray()))?.success == true,
            )
        }
    }

    private fun payload(value: ByteArray) =
        requireNotNull(XiaomiHipeeP1Support.decodeCommand(value))
}
