package nodomain.freeyourgadget.gadgetbridge.devices;

import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import de.greenrobot.dao.query.QueryBuilder;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericBloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericBloodPressureSampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.bloodpressure.BloodPressureProfile;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class GenericBloodPressureSampleProvider extends AbstractTimeSampleProvider<GenericBloodPressureSample> {
    public GenericBloodPressureSampleProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        super(device, session);
    }

    @NonNull
    @Override
    public AbstractDao<GenericBloodPressureSample, ?> getSampleDao() {
        return getSession().getGenericBloodPressureSampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return GenericBloodPressureSampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return GenericBloodPressureSampleDao.Properties.DeviceId;
    }

    @NonNull
    @Override
    public GenericBloodPressureSample createSample() {
        return new GenericBloodPressureSample();
    }

    @Override
    protected void applyAdditionalFilters(final QueryBuilder<GenericBloodPressureSample> qb) {
        final int activeUser = getActiveUserIndex();
        if (activeUser == BloodPressureProfile.USER_ID_UNKNOWN) {
            qb.whereOr(
                GenericBloodPressureSampleDao.Properties.UserIndex.eq(activeUser),
                GenericBloodPressureSampleDao.Properties.UserIndex.isNull()
            );
        } else if (activeUser >= 0) {
            qb.where(GenericBloodPressureSampleDao.Properties.UserIndex.eq(activeUser));
        }
    }

    /**
     * The user index selected in the device preferences, or -1 for all users.
     */
    private int getActiveUserIndex() {
        final Prefs devicePrefs = GBApplication.getDevicePrefs(getDevice());
        return devicePrefs.getInt(DeviceSettingsPreferenceConst.PREF_BLOOD_PRESSURE_ACTIVE_USER, -1);
    }

    /**
     * The distinct user indexes in the samples of the device, in ascending order.
     * Samples without a user count as {@link BloodPressureProfile#USER_ID_UNKNOWN}.
     */
    @NonNull
    public List<Integer> getUserIndexes() {
        final Device dbDevice = DBHelper.findDevice(getDevice(), getSession());
        if (dbDevice == null) {
            return Collections.emptyList();
        }

        final String sql = String.format(
            Locale.ROOT,
            "SELECT DISTINCT IFNULL(%2$s, %4$d) AS U FROM %1$s WHERE %3$s = ? ORDER BY U",
            GenericBloodPressureSampleDao.TABLENAME,
            GenericBloodPressureSampleDao.Properties.UserIndex.columnName,
            GenericBloodPressureSampleDao.Properties.DeviceId.columnName,
            BloodPressureProfile.USER_ID_UNKNOWN
        );

        final List<Integer> userIndexes = new ArrayList<>();
        try (final Cursor c = getSession().getDatabase().rawQuery(sql, new String[]{String.valueOf(dbDevice.getId())})) {
            while (c.moveToNext()) {
                userIndexes.add(c.getInt(0));
            }
        }
        return userIndexes;
    }
}
