package org.sensorhub.android.ui.features.dashboard

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.opengis.swe.v20.Count
import net.opengis.swe.v20.DataArray
import net.opengis.swe.v20.DataComponent
import net.opengis.swe.v20.Quantity
import net.opengis.swe.v20.Text
import net.opengis.swe.v20.Time
import org.sensorhub.android.R
import org.sensorhub.android.SensorHubService
import org.sensorhub.android.ui.features.sensors.data.SensorUiEntry
import org.sensorhub.android.ui.components.OSHExpandableCard
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.api.data.IDataProducer
import org.sensorhub.api.data.IStreamingDataInterface
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class ReadingStatus { LIVE, WAITING, STALE, UNAVAILABLE }
data class MeasurementUi(val label: String, val value: String = "—")
data class SensorCardUi(
    val id: String,
    val title: String,
    val status: ReadingStatus,
    val measurements: List<MeasurementUi> = emptyList(),
    val lastReadingTime: Long? = null
)

class SensorCardReader(private val context: Context) {
    fun read(service: SensorHubService?, sensors: List<SensorUiEntry>, includeMeasurements: Boolean = true): List<SensorCardUi> {
        val modules = service?.sensorHub?.moduleRegistry?.loadedModules?.toList().orEmpty()
        val running = service?.hubState == SensorHubService.HubState.RUNNING
        return sensors.map { sensor ->
            val title = context.getString(sensor.nameRes)
            try {
                val module = modules.firstOrNull {
                    it.localID == sensor.runtimeModuleId
                }
                val outputs = (module as? IDataProducer)?.outputs?.values?.toList().orEmpty().filter { output ->
                    sensor.runtime.matchesOutput(output)
                }
                val status = when {
                    !running -> ReadingStatus.UNAVAILABLE
                    outputs.isEmpty() -> ReadingStatus.UNAVAILABLE
                    outputs.any { it.latestRecord == null } -> ReadingStatus.WAITING
                    outputs.any { output ->
                        val period = output.averageSamplingPeriod
                        val threshold = if (period.isFinite() && period > 0) maxOf(10000.0, period * 3000) else Double.POSITIVE_INFINITY
                        output.latestRecord != null && System.currentTimeMillis() - output.latestRecordTime > threshold
                    } -> ReadingStatus.STALE
                    else -> ReadingStatus.LIVE
                }
                val rows = if (!running || !includeMeasurements) emptyList() else outputs.flatMap { output ->
                    measurements(output, outputs.size > 1)
                }
                SensorCardUi(sensor.id, title, status, rows,
                    outputs.filter { it.latestRecord != null }.map { it.latestRecordTime }
                        .filter { it > 0 }.maxOrNull())
            } catch (_: Exception) {
                SensorCardUi(sensor.id, title, ReadingStatus.UNAVAILABLE)
            }
        }
    }

    private fun measurements(output: IStreamingDataInterface, includeOutput: Boolean): List<MeasurementUi> {
        // Never attach data to the driver's shared schema. Arrays stay collapsed.
        val schema = output.recordDescription.copy()
        output.latestRecord?.let { schema.data = it }
        val rows = mutableListOf<MeasurementUi>()
        fun visit(component: DataComponent, prefix: String) {
            if (component is Time) return
            val label = component.label?.takeIf { it.isNotBlank() } ?: component.name.orEmpty().replace('_', ' ')
            if (component is DataArray) {
                rows += MeasurementUi(label, if (output.latestRecord != null) context.getString(R.string.ui_available) else "—")
                return
            }
            if (component.componentCount > 0) {
                for (i in 0 until component.componentCount) visit(component.getComponent(i), prefix)
                return
            }
            val unit = (component as? Quantity)?.uom?.code.orEmpty()
            val value = if (!component.hasData()) "—" else when (component) {
                is Quantity -> format(component.data.doubleValue, if (component.name in listOf("lat", "lon")) 6 else 3) + if (unit.isBlank() or unit.equals("1")) "" else " $unit"
                is Count -> component.data.intValue.toString()
                is net.opengis.swe.v20.Boolean -> component.data.booleanValue.toString()
                is Text -> component.data.stringValue.orEmpty().take(120)
                else -> "—"
            }
            rows += MeasurementUi(prefix + label, value)
        }
        visit(schema, if (includeOutput) output.name.replace('_', ' ') + " · " else "")
        return rows
    }

    private fun format(value: Double, decimals: Int = 3): String = when {
        value == Double.NEGATIVE_INFINITY -> "−∞"
        !value.isFinite() -> "—"
        else -> String.format(Locale.getDefault(), "%.${decimals}f", value)
    }
}

@Composable
internal fun SensorOutputCard(
    sensor: SensorCardUi,
    expanded: Boolean? = null,
    onExpandChange: ((Boolean) -> Unit)? = null
) {
    val status = when (sensor.status) {
        ReadingStatus.LIVE -> "ok"
        ReadingStatus.WAITING, ReadingStatus.STALE -> "nok"
        ReadingStatus.UNAVAILABLE -> "error"
    }
    OSHExpandableCard(
        title = sensor.title,
        status = status,
        expanded = expanded,
        onExpandChange = onExpandChange,
        collapsedContent = {
            Text(
                sensor.lastReadingTime?.let {
                    stringResource(R.string.dashboard_last_reading,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it)))
                } ?: stringResource(R.string.dashboard_no_reading_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        expandedContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sensor.measurements.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            row.label,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(row.value, modifier = Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun SensorCardsPreview() {
    OSHTheme {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SensorOutputCard(
                SensorCardUi(
                    "orient_e",
                    "Euler Orientation",
                    ReadingStatus.LIVE,
                    listOf(
                    MeasurementUi(
                        "Heading angle",
                        "125.400 deg"
                    ),
                    MeasurementUi(
                        "Pitch angle",
                        "2.100 deg"
                    ),
                    MeasurementUi(
                        "Roll angle",
                        "−0.300 deg"
                    )
                )))
        }
    }
}
