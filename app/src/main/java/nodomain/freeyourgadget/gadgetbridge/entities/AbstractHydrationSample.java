package nodomain.freeyourgadget.gadgetbridge.entities;

import androidx.annotation.NonNull;

import nodomain.freeyourgadget.gadgetbridge.model.HydrationSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public abstract class AbstractHydrationSample extends AbstractTimeSample implements HydrationSample {
    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "timestamp=" + DateTimeUtils.formatDateTime(DateTimeUtils.parseTimestampMillis(getTimestamp())) +
            ", day=" + getDay() +
            ", volumeMl=" + getVolumeMl() +
            ", userId=" + getUserId() +
            ", deviceId=" + getDeviceId() +
            "}";
    }
}
