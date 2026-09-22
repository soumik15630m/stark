package com.soumik.stark.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.core.util.Format
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.domain.stats.RecordTypes
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.common.StatColumn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val daily by vm.daily.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val hours by vm.hourHistogram.collectAsStateWithLifecycle()
    val streak by vm.streakDays.collectAsStateWithLifecycle()

    val byDate = daily.associateBy { LocalDate.of(it.dateKey / 10000, (it.dateKey / 100) % 100, it.dateKey % 100) }
    val today = LocalDate.now()
    val weekStart = today.with(WeekFields.of(DayOfWeek.MONDAY, 1).dayOfWeek(), 1L)
    fun sumFrom(pred: (LocalDate) -> Boolean) = byDate.filter { pred(it.key) }.values.sumOf { it.distanceAllM }
    val weekKm = sumFrom { !it.isBefore(weekStart) }
    val monthKm = sumFrom { it.year == today.year && it.month == today.month }
    val yearKm = sumFrom { it.year == today.year }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatColumn("This week", "${Format.km(weekKm)} km")
                    StatColumn("This month", "${Format.km(monthKm)} km")
                    StatColumn("This year", "${Format.km(yearKm)} km")
                }
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Riding streak", style = MaterialTheme.typography.titleSmall)
                Text("$streak ${if (streak == 1) "day" else "days"} in a row", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Activity — last 17 weeks", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                CalendarHeatmap(byDate)
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Text("When you ride", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                HourBars(hours)
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Records", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                val map = records.associateBy { it.type }
                RecordRow("Longest ride", map[RecordTypes.LONGEST_RIDE_M]?.let { "${Format.km(it.value)} km" })
                RecordRow("Top speed", map[RecordTypes.TOP_SPEED_MPS]?.let { "${Math.round(it.value * 3.6)} km/h" })
                RecordRow("Most in a day", map[RecordTypes.MOST_KM_DAY_M]?.let { "${Format.km(it.value)} km" })
                RecordRow("Longest duration", map[RecordTypes.LONGEST_DURATION_S]?.let { Format.duration(it.value.toLong()) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CalendarHeatmap(byDate: Map<LocalDate, DailyTotal>) {
    val today = LocalDate.now()
    val weeks = 17
    val start = today.minusWeeks((weeks - 1).toLong()).with(WeekFields.of(DayOfWeek.MONDAY, 1).dayOfWeek(), 1L)
    val maxKm = (byDate.values.maxOfOrNull { it.distanceAllM } ?: 1.0).coerceAtLeast(1.0)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (w in 0 until weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    val km = byDate[date]?.distanceAllM ?: 0.0
                    val level = if (km <= 0) 0f else (0.25f + 0.75f * (km / maxKm).toFloat()).coerceIn(0f, 1f)
                    val color = if (date.isAfter(today)) Color.Transparent
                    else if (level == 0f) Color(0xFF20272E)
                    else MaterialTheme.colorScheme.primary.copy(alpha = level)
                    Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(color))
                }
            }
        }
    }
}

@Composable
private fun HourBars(hours: DoubleArray) {
    val max = (hours.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (h in 0 until 24) {
            val frac = (hours[h] / max).toFloat().coerceIn(0f, 1f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().height((4 + 72 * frac).dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f + 0.7f * frac)))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("6", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("18", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("23", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", fontWeight = FontWeight.SemiBold)
    }
}
