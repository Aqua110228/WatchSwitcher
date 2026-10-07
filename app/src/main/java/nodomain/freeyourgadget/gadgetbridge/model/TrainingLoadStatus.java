package nodomain.freeyourgadget.gadgetbridge.model;

/**
 * Training-status zone a device reports for its training load, for devices that surface a status
 * on-screen rather than an acute and chronic load pair whose ratio the status is derived from.
 */
public enum TrainingLoadStatus {
    LOW,
    OPTIMAL,
    HIGH,
    VERY_HIGH,
}
