package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_125;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_12K;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_16K;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_1K;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_250;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_2K;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_4K;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_500;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_62;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_BAND_8K;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsEqualizerBand {
    HZ_62(62, PREF_REDMI_BUDS_EQUALIZER_BAND_62, R.string.redmi_buds_5_pro_equalizer_band_62),
    HZ_125(125, PREF_REDMI_BUDS_EQUALIZER_BAND_125, R.string.redmi_buds_5_pro_equalizer_band_125),
    HZ_250(250, PREF_REDMI_BUDS_EQUALIZER_BAND_250, R.string.redmi_buds_5_pro_equalizer_band_250),
    HZ_500(500, PREF_REDMI_BUDS_EQUALIZER_BAND_500, R.string.redmi_buds_5_pro_equalizer_band_500),
    HZ_1K(1000, PREF_REDMI_BUDS_EQUALIZER_BAND_1K, R.string.redmi_buds_5_pro_equalizer_band_1k),
    HZ_2K(2016, PREF_REDMI_BUDS_EQUALIZER_BAND_2K, R.string.redmi_buds_5_pro_equalizer_band_2k),
    HZ_4K(4000, PREF_REDMI_BUDS_EQUALIZER_BAND_4K, R.string.redmi_buds_5_pro_equalizer_band_4k),
    HZ_8K(8000, PREF_REDMI_BUDS_EQUALIZER_BAND_8K, R.string.redmi_buds_5_pro_equalizer_band_8k),
    HZ_12K(12000, PREF_REDMI_BUDS_EQUALIZER_BAND_12K, R.string.redmi_buds_5_pro_equalizer_band_12k),
    HZ_16K(16000, PREF_REDMI_BUDS_EQUALIZER_BAND_16K, R.string.redmi_buds_5_pro_equalizer_band_16k),
    ;

    private final int frequency;
    private final String preferenceKey;
    @StringRes
    private final int label;

    RedmiBudsEqualizerBand(final int frequency, final String preferenceKey, @StringRes final int label) {
        this.frequency = frequency;
        this.preferenceKey = preferenceKey;
        this.label = label;
    }

    public int getFrequency() {
        return frequency;
    }

    @NonNull
    public String getPreferenceKey() {
        return preferenceKey;
    }

    @StringRes
    public int getLabel() {
        return label;
    }
}
