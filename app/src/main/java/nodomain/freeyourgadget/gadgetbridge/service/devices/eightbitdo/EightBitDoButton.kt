package nodomain.freeyourgadget.gadgetbridge.service.devices.eightbitdo

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.util.HidKey

enum class EightBitDoButton(
    val stateByte: Int,
    val stateBit: Int,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val defaultKey: HidKey?,
) {
    A(1, 0x20, R.string.eightbitdo_button_a, R.drawable.ic_hdr_auto, HidKey.G),
    B(1, 0x10, R.string.eightbitdo_button_b, R.drawable.ic_b_circle, HidKey.J),
    X(0, 0x10, R.string.eightbitdo_button_x, R.drawable.ic_cancel, HidKey.H),
    Y(0, 0x20, R.string.eightbitdo_button_y, R.drawable.ic_y_circle, HidKey.I),
    L1(1, 0x04, R.string.eightbitdo_button_l1, R.drawable.ic_game_button_l1, HidKey.K),
    R1(1, 0x08, R.string.eightbitdo_button_r1, R.drawable.ic_game_button_r1, HidKey.M),
    L2(1, 0x40, R.string.eightbitdo_button_l2, R.drawable.ic_game_button_l2, HidKey.L),
    R2(1, 0x80, R.string.eightbitdo_button_r2, R.drawable.ic_game_button_r2, HidKey.R),
    MINUS(0, 0x01, R.string.eightbitdo_button_minus, R.drawable.ic_remove, HidKey.N),
    PLUS(0, 0x08, R.string.eightbitdo_button_plus, R.drawable.ic_add, HidKey.O),
    STAR(2, 0x01, R.string.eightbitdo_button_star, R.drawable.ic_star_gray, null),
    HEART(2, 0x02, R.string.eightbitdo_button_heart, R.drawable.ic_heart, HidKey.S),
    UP(1, 0x02, R.string.eightbitdo_button_up, R.drawable.ic_arrow_upward, HidKey.C),
    DOWN(1, 0x01, R.string.eightbitdo_button_down, R.drawable.ic_arrow_downward, HidKey.D),
    LEFT(0, 0x80, R.string.eightbitdo_button_left, R.drawable.ic_arrow_back, HidKey.E),
    RIGHT(0, 0x40, R.string.eightbitdo_button_right, R.drawable.ic_arrow_forward, HidKey.F),
    ;

    val prefKey: String
        get() = DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_KEYMAP_PREFIX + name.lowercase()
}
