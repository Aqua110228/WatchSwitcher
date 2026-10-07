package nodomain.freeyourgadget.gadgetbridge.model;

public interface HydrationSample extends TimeSample {
    /**
     * Local calendar day that the entry applies to, as yyyyMMdd.
     */
    int getDay();

    /**
     * Change to the daily total, in mL. It can be negative.
     */
    double getVolumeMl();
}
