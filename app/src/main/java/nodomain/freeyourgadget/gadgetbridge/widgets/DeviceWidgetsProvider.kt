package nodomain.freeyourgadget.gadgetbridge.widgets

interface DeviceWidgetsProvider {
    fun getWidgets(): List<GBWidget<*>>

    companion object {
        @JvmField
        val DEFAULT: DeviceWidgetsProvider = object : DeviceWidgetsProvider {
            override fun getWidgets(): List<GBWidget<*>> = emptyList()
        }
    }
}
