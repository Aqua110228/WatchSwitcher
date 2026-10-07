package nodomain.freeyourgadget.gadgetbridge.devices;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import de.greenrobot.dao.query.QueryBuilder;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHydrationSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHydrationSampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class HydrationSampleProvider extends AbstractTimeSampleProvider<GenericHydrationSample> {
    public HydrationSampleProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        super(device, session);
    }

    /**
     * Returns the {@link LocalDate} as a yyyyMMdd day number.
     */
    public static int toDay(@NonNull final LocalDate date) {
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    @NonNull
    @Override
    public AbstractDao<GenericHydrationSample, ?> getSampleDao() {
        return getSession().getGenericHydrationSampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return GenericHydrationSampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return GenericHydrationSampleDao.Properties.DeviceId;
    }

    @NonNull
    @Override
    public GenericHydrationSample createSample() {
        return new GenericHydrationSample();
    }

    /**
     * Returns the total volume in mL for each day between {@code fromDay} and {@code toDay}, both
     * inclusive, as yyyyMMdd. Days without entries are not included in the map.
     */
    @NonNull
    public Map<Integer, Double> getDayTotals(final int fromDay, final int toDay) {
        final Device dbDevice = DBHelper.findDevice(getDevice(), getSession());
        if (dbDevice == null) {
            return Collections.emptyMap();
        }

        final String sql = String.format(
            "SELECT %2$s, SUM(%3$s) FROM %1$s WHERE %4$s = ? AND %2$s >= ? AND %2$s <= ? GROUP BY %2$s",
            GenericHydrationSampleDao.TABLENAME,
            GenericHydrationSampleDao.Properties.Day.columnName,
            GenericHydrationSampleDao.Properties.VolumeMl.columnName,
            GenericHydrationSampleDao.Properties.DeviceId.columnName
        );

        final Map<Integer, Double> totals = new TreeMap<>();
        final String[] args = {
            String.valueOf(dbDevice.getId()),
            String.valueOf(fromDay),
            String.valueOf(toDay)
        };
        try (Cursor c = getSession().getDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                totals.put(c.getInt(0), c.getDouble(1));
            }
        }
        return totals;
    }

    /**
     * Returns the total volume in mL for the day (passed as yyyyMMdd).
     */
    public double getDayTotal(final int day) {
        final Double total = getDayTotals(day, day).get(day);
        return total != null ? total : 0;
    }

    /**
     * Returns the entry of the day with the latest timestamp, or null if the day has no entries.
     */
    @Nullable
    public GenericHydrationSample getLastEntry(final int day) {
        final Device dbDevice = DBHelper.findDevice(getDevice(), getSession());
        if (dbDevice == null) {
            return null;
        }

        final QueryBuilder<GenericHydrationSample> qb = getSampleDao().queryBuilder();
        qb.where(GenericHydrationSampleDao.Properties.DeviceId.eq(dbDevice.getId()))
            .where(GenericHydrationSampleDao.Properties.Day.eq(day))
            .orderDesc(GenericHydrationSampleDao.Properties.Timestamp)
            .limit(1);
        final List<GenericHydrationSample> samples = qb.build().list();
        detachFromSession();
        return !samples.isEmpty() ? samples.get(0) : null;
    }

    /**
     * Stores a change of {@code volumeMl} to the total of the day, as yyyyMMdd. If an entry with
     * the same timestamp exists, the timestamp is incremented by 1 ms until it is unique.
     */
    public void addEntry(final int day, final long timestamp, final double volumeMl) {
        final Device dbDevice = DBHelper.getDevice(getDevice(), getSession());
        final long userId = Objects.requireNonNull(DBHelper.getUser(getSession()).getId());

        final GenericHydrationSample sample = createSample();
        sample.setDeviceId(Objects.requireNonNull(dbDevice.getId()));
        sample.setUserId(userId);
        sample.setDay(day);
        sample.setVolumeMl(volumeMl);
        sample.setTimestamp(timestamp);

        while (true) {
            try {
                getSampleDao().insert(sample);
                return;
            } catch (final SQLiteConstraintException e) {
                sample.setTimestamp(sample.getTimestamp() + 1);
            }
        }
    }
}
