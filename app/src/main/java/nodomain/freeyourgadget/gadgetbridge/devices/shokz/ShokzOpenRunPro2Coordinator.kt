package nodomain.freeyourgadget.gadgetbridge.devices.shokz

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzEqualizer
import java.util.regex.Pattern

class ShokzOpenRunPro2Coordinator : ShokzCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^OpenRun Pro 2 by Shokz$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_shokz_openrun_pro_2
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.HEADPHONES
    }

    // Confirmed against a real device: it does not have onboard storage, so it doesn't
    // support the standalone MP3 media source/playback mode that the OpenSwim Pro exposes.
    override fun supportsMp3(): Boolean {
        return false
    }

    // Confirmed against a real device: CONTROLS_GET goes unanswered, unlike the OpenSwim Pro.
    override fun supportsControls(): Boolean {
        return false
    }

    // Confirmed via a Bluetooth HCI snoop capture of the official Shokz app.
    override fun supportsBassTreble(): Boolean {
        return true
    }

    // Confirmed via a Bluetooth HCI snoop capture of the official Shokz app.
    override fun supportsCustomEqualizer(): Boolean {
        return true
    }

    // Confirmed via a Bluetooth HCI snoop capture of the official Shokz app with its account
    // region set to the US, which is otherwise required for the app itself to show these.
    override fun supportsClassicAndVolumeBoost(): Boolean {
        return true
    }

    // Bytes captured from a live Bluetooth HCI snoop of the official Shokz app talking to a
    // real OpenRun Pro 2. Unlike the OpenSwim Pro, VOCAL uses different tuning parameters here.
    override fun equalizerArgs(equalizer: ShokzEqualizer): ByteArray = when (equalizer) {
        ShokzEqualizer.STANDARD ->
            byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        ShokzEqualizer.VOCAL ->
            byteArrayOf(0x02, 0xfa.toByte(), 0xfe.toByte(), 0x04, 0x05, 0x01, 0x00, 0x00)

        ShokzEqualizer.BASS ->
            byteArrayOf(0x03, 0x09, 0xf9.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)

        ShokzEqualizer.TREBLE ->
            byteArrayOf(0x04, 0x00, 0x00, 0x01, 0x04, 0x05, 0x00, 0x00)

        else -> super.equalizerArgs(equalizer)
    }
}
