package nodomain.freeyourgadget.gadgetbridge.widgets

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.ActiveTimeWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BloodPressureWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyEnergyWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.CaloriesActiveWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.CaloriesSegmentedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.DistanceWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.GoalsWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HeartRateWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HrvWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HydrationWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.PaiWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepScoreWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Spo2Widget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StepsWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressBreakdownWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressSegmentedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressSimpleWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.TodayWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxAnyWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxCyclingWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxRunningWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BmiWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BasalMetabolicRateWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyFatWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyWaterWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.MuscleMassWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.WeightWidget

/**
 * Resolves [GBWidget] implementations by their IDs ([GBWidget.id]) - the common ones
 * plus the ones that coordinators of currently paired devices declare via
 * [nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator.getWidgetsProvider].
 */
object WidgetRegistry {
    private val common: List<GBWidget<*>> = listOf(
        ActiveTimeWidget,
        BasalMetabolicRateWidget,
        BloodPressureWidget,
        BmiWidget,
        BodyEnergyWidget,
        BodyFatWidget,
        BodyWaterWidget,
        CaloriesActiveWidget,
        CaloriesSegmentedWidget,
        DistanceWidget,
        GoalsWidget,
        HeartRateWidget,
        HrvWidget,
        HydrationWidget,
        MuscleMassWidget,
        PaiWidget,
        SleepScoreWidget,
        SleepWidget,
        Spo2Widget,
        StepsWidget,
        StressBreakdownWidget,
        StressSegmentedWidget,
        StressSimpleWidget,
        TodayWidget,
        Vo2MaxAnyWidget,
        Vo2MaxCyclingWidget,
        Vo2MaxRunningWidget,
        WeightWidget,
    )

    /**
     * All known widget kinds: common + device-specific.
     */
    fun all(): List<GBWidget<*>> {
        val deviceSpecificWidgets = GBApplication.app().deviceManager.devices
            .map { it.deviceCoordinator }
            .distinct()
            .flatMap { it.widgetsProvider.getWidgets() }

        val byId = LinkedHashMap<String, GBWidget<*>>()
        for (widget in common) byId.putIfAbsent(widget.id, widget)
        for (widget in deviceSpecificWidgets) byId.putIfAbsent(widget.id, widget)
        return byId.values.toList()
    }

    fun byId(id: String): GBWidget<*>? = all().find { it.id == id }

    /**
     * Widget kinds supported by at least one currently paired device.
     */
    fun available(): List<GBWidget<*>> {
        val devices = GBApplication.app().deviceManager.devices
        return all().filter { widget -> devices.any { widget.isSupportedBy(it) } }
    }
}
