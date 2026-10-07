package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsTransparencyStrength implements RedmiBudsPreferenceEntry {
    REGULAR((byte) 0x00, R.string.redmi_buds_5_pro_transparency_regular),
    VOICE((byte) 0x01, R.string.redmi_buds_5_pro_transparency_voice),
    AMBIENT((byte) 0x02, R.string.redmi_buds_5_pro_transparency_ambient),
    ;

    private final byte code;
    @StringRes
    private final int label;

    RedmiBudsTransparencyStrength(final byte code, @StringRes final int label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Override
    public int getLabel() {
        return label;
    }
}
