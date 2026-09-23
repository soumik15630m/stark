package com.soumik.stark.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.core.util.Format
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.map.MapSurface
import java.time.LocalDate

@Composable
fun TimelineScreen(onOpenTrip: (Long) -> Unit, vm: TimelineViewModel = viewModel()) {
    val dateKey by vm.dateKey.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            MapSurface(modifier = Modifier.fillMaxSize(), speedTracks = day.speedTracks, stops = day.stops)
            if (day.speedTracks.isEmpty()) {
                Text("No routes this day", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
            Text(formatDate(dateKey), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            IconButton(onClick = { vm.shiftDay(1) }) { Icon(Icons.Filled.ChevronRight, "Next day") }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (day.tripCount > 0) {
                item {
                    SectionCard(Modifier.fillMaxWidth()) {
                        Text("Home → home", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("${Format.km(day.outingKm)} km", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${day.tripCount} trips • out ${Format.duration(day.outingSpanS)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item { Text("Nothing logged this day.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(day.rows, key = { it.leg.id }) { row -> TripRow(row) { onOpenTrip(row.leg.id) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TripRow(row: LegRow, onClick: () -> Unit) {
    val leg = row.leg
    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        if (row.waitBeforeS >= 60) {
            val big = row.waitBeforeS >= 300
            Text(
                "${if (big) "⏹ stopped" else "⏸ paused"} ${fmt(row.waitBeforeS)} ${row.startName?.let { "at $it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = if (big) Color(0xFFE57373) else Color(0xFFFFB74D),
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (row.leg.label ?: "${row.startName ?: "Start"} → ${row.endName ?: "End"}"),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${Format.clock(leg.startT, leg.offsetMin)}${leg.endT?.let { " – " + Format.clock(it, leg.offsetMin) } ?: ""}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${Format.km(leg.distanceM)} km", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.duration(leg.durationS), style = MaterialTheme.typography.bodyMedium)
                Text("avg ${row.avgMovingKmh} • max ${Format.kmh(leg.maxSpeedMps)} km/h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (leg.hasEstimatedGap) Text("~estimated", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB74D))
            }
        }
    }
}

private fun fmt(s: Long): String {
    val m = s / 60
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private fun formatDate(dateKey: Int): String {
    val d = LocalDate.of(dateKey / 10000, (dateKey / 100) % 100, dateKey % 100)
    val months = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val dow = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return "${dow[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${months[d.monthValue]} ${d.year}"
}
