package nodomain.freeyourgadget.gadgetbridge.model.workout

import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.data.ChartData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IDataSet
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZones

data class WorkoutChart @JvmOverloads constructor(
    val id: String,
    val title: String,
    val group: String,
    val chartData: ChartData<out IDataSet<out Entry>>,
    var chartYLabelFormatter: ValueFormatter? = null,
    var unitString: String? = null,
    val lineChart: (BarLineChartBase<*>) -> Unit = {},
    // Time-in-zone (index 0..5) and the resolved thresholds for the HR-zone overlay; null on non-HR charts.
    var secondsInZone: IntArray? = null,
    var zoneThresholds: HeartRateZones? = null
)
