package nodomain.freeyourgadget.gadgetbridge.model;

/**
 * The side of the device a device that must be found. For now, only LEFT/RIGHT, mostly
 * useful for earbuds. Single-piece devices always use {@link #ALL}.
 */
public enum FindDeviceTarget {
    ALL,
    LEFT,
    RIGHT,
    ;
}
