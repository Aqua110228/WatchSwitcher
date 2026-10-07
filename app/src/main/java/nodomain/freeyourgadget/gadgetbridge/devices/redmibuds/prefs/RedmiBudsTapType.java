package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_DOUBLE_TAP_LEFT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_DOUBLE_TAP_RIGHT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_LONG_TAP_MODE_LEFT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_LONG_TAP_MODE_RIGHT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_SINGLE_TAP_LEFT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_SINGLE_TAP_RIGHT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_TRIPLE_TAP_LEFT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_TRIPLE_TAP_RIGHT;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsTapType {
    SINGLE(
        (byte) 0x04,
        R.string.single_tap,
        R.drawable.ic_filter_1,
        PREF_REDMI_BUDS_CONTROL_SINGLE_TAP_LEFT,
        PREF_REDMI_BUDS_CONTROL_SINGLE_TAP_RIGHT
    ),
    DOUBLE(
        (byte) 0x01,
        R.string.double_tap,
        R.drawable.ic_filter_2,
        PREF_REDMI_BUDS_CONTROL_DOUBLE_TAP_LEFT,
        PREF_REDMI_BUDS_CONTROL_DOUBLE_TAP_RIGHT
    ),
    TRIPLE(
        (byte) 0x02,
        R.string.triple_tap,
        R.drawable.ic_filter_3,
        PREF_REDMI_BUDS_CONTROL_TRIPLE_TAP_LEFT,
        PREF_REDMI_BUDS_CONTROL_TRIPLE_TAP_RIGHT
    ),
    LONG(
        (byte) 0x03,
        R.string.long_press,
        R.drawable.ic_touch,
        PREF_REDMI_BUDS_CONTROL_LONG_TAP_MODE_LEFT,
        PREF_REDMI_BUDS_CONTROL_LONG_TAP_MODE_RIGHT
    ),
    ;

    private final byte code;
    @StringRes
    private final int title;
    @DrawableRes
    private final int icon;
    private final String leftKey;
    private final String rightKey;

    RedmiBudsTapType(final byte code,
                     @StringRes final int title,
                     @DrawableRes final int icon,
                     final String leftKey,
                     final String rightKey) {
        this.code = code;
        this.title = title;
        this.icon = icon;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
    }

    public byte getCode() {
        return code;
    }

    @Nullable
    public static RedmiBudsTapType fromCode(final byte code) {
        for (final RedmiBudsTapType tapType : values()) {
            if (tapType.code == code) {
                return tapType;
            }
        }

        return null;
    }

    @StringRes
    public int getTitle() {
        return title;
    }

    @DrawableRes
    public int getIcon() {
        return icon;
    }

    @NonNull
    public String getPreferenceKey(@NonNull final RedmiBudsPosition position) {
        return position == RedmiBudsPosition.LEFT ? leftKey : rightKey;
    }
}
