package nodomain.freeyourgadget.gadgetbridge.devices.viatom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import de.greenrobot.dao.query.QueryBuilder;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractTimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.F8WeightSample;
import nodomain.freeyourgadget.gadgetbridge.entities.F8WeightSampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class F8WeightSampleProvider extends AbstractTimeSampleProvider<F8WeightSample> {
    public F8WeightSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @NonNull
    @Override
    public @NotNull AbstractDao<F8WeightSample, ?> getSampleDao() {
        return getSession().getF8WeightSampleDao();
    }

    @NonNull
    @Override
    protected @NotNull Property getTimestampSampleProperty() {
        return F8WeightSampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected @NotNull Property getDeviceIdentifierSampleProperty() {
        return F8WeightSampleDao.Properties.DeviceId;
    }

    @Override
    public F8WeightSample createSample() {
        return new F8WeightSample();
    }

    @Override
    protected void applyAdditionalFilters(final QueryBuilder<F8WeightSample> qb) {
        qb.where(F8WeightSampleDao.Properties.UserIndex.eq(getActiveUserIndex()));
    }

    private int getActiveUserIndex() {
        final Prefs devicePrefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()));
        return devicePrefs.getInt("active_user", 1);
    }

}
