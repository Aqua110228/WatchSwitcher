package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsEqualizerBandLevel implements RedmiBudsPreferenceEntry {
    MINUS_6((byte) 0x86, R.string.redmi_buds_5_pro_equalizer_neg6),
    MINUS_5((byte) 0x85, R.string.redmi_buds_5_pro_equalizer_neg5),
    MINUS_4((byte) 0x84, R.string.redmi_buds_5_pro_equalizer_neg4),
    MINUS_3((byte) 0x83, R.string.redmi_buds_5_pro_equalizer_neg3),
    MINUS_2((byte) 0x82, R.string.redmi_buds_5_pro_equalizer_neg2),
    MINUS_1((byte) 0x81, R.string.redmi_buds_5_pro_equalizer_neg1),
    FLAT((byte) 0x00, R.string.redmi_buds_5_pro_equalizer_zero),
    PLUS_1((byte) 0x01, R.string.redmi_buds_5_pro_equalizer_1),
    PLUS_2((byte) 0x02, R.string.redmi_buds_5_pro_equalizer_2),
    PLUS_3((byte) 0x03, R.string.redmi_buds_5_pro_equalizer_3),
    PLUS_4((byte) 0x04, R.string.redmi_buds_5_pro_equalizer_4),
    PLUS_5((byte) 0x05, R.string.redmi_buds_5_pro_equalizer_5),
    PLUS_6((byte) 0x06, R.string.redmi_buds_5_pro_equalizer_6),
    ;

    private final byte code;
    @StringRes
    private final int label;

    RedmiBudsEqualizerBandLevel(final byte code, @StringRes final int label) {
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
