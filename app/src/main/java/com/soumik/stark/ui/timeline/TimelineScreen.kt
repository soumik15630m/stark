package com.soumik.stark.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.core.util.Format
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.common.StatColumn
import com.soumik.stark.ui.map.MapSurface
import java.time.LocalDate
import java.util.Locale

@Composable
fun TimelineScreen(onOpenTrip: (Long) -> Unit, onReplayOuting: (Long) -> Unit = {}, vm: TimelineViewModel = viewModel()) {
    val dateKey by vm.dateKey.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val dailyKm by vm.dailyKm.collectAsStateWithLifecycle()
    val streak by vm.streakDays.collectAsStateWithLifecycle()

    var showCalendar by remember { mutableStateOf(false) }
    // Collapsed by default; the outing distance is the headline, trips hide behind a tap.
    val expanded = remember { mutableStateMapOf<Long?, Boolean>() }

    // Horizontal swipe anywhere on the day switches the date (vertical scroll is unaffected).
    var dragDx by remember { mutableStateOf(0f) }
    val swipe = Modifier.pointerInput(dateKey) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (dragDx <= -60f) vm.shiftDay(1) else if (dragDx >= 60f) vm.shiftDay(-1)
                dragDx = 0f
            },
            onHorizontalDrag = { _, delta -> dragDx += delta },
        )
    }

    LazyColumn(Modifier.fillMaxSize().then(swipe), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                MapSurface(modifier = Modifier.fillMaxSize(), speedTracks = day.speedTracks, stops = day.stops)
                if (day.speedTracks.isEmpty()) {
                    Text("No routes this day", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
                Row(
                    Modifier.weight(1f).clickable { showCalendar = true },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatDate(dateKey), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.CalendarMonth, "Open calendar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { vm.shiftDay(1) }) { Icon(Icons.Filled.ChevronRight, "Next day") }
            }
        }

        if (day.tripCount > 0) {
            item { DaySummaryCard(day.summary) }
            items(day.groups, key = { it.outingId ?: -1L }) { group ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    OutingCard(
                        group = group,
                        expanded = expanded[group.outingId] ?: false,
                        onToggle = { expanded[group.outingId] = !(expanded[group.outingId] ?: false) },
                        onOpenTrip = onOpenTrip,
                        onReplayOuting = onReplayOuting,
                    )
                }
            }
        } else {
            item { Text("Nothing logged this day.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCalendar) {
        CalendarSheet(
            currentKey = dateKey,
            dailyKm = dailyKm,
            streakDays = streak,
            onJump = { vm.jumpTo(it) },
            onDismiss = { showCalendar = false },
        )
    }
}

@Composable
private fun DaySummaryCard(s: DaySummary) {
    SectionCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Day summary", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatColumn("Distance", "${Format.km(s.km * 1000)} km")
            StatColumn("Time", Format.duration(s.ridingTimeS))
            StatColumn("Max", "${s.maxSpeedKmh} km/h")
            StatColumn("Trips", "${s.trips}")
        }
        if (s.estFuelL != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("~${String.format(Locale.US, "%.2f", s.estFuelL)} L used")
                    s.estCostInr?.let { append(" • ~₹${String.format(Locale.US, "%.0f", it)}") }
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutingCard(group: OutingGroup, expanded: Boolean, onToggle: () -> Unit, onOpenTrip: (Long) -> Unit, onReplayOuting: (Long) -> Unit) {
    SectionCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("${Format.km(group.distanceKm * 1000)} km", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${group.tripCount} trip${if (group.tripCount == 1) "" else "s"} • out ${Format.duration(group.spanS)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Replay the whole home→home outing (only real outings, not the "Other trips" bucket).
            group.outingId?.let { oid ->
                IconButton(onClick = { onReplayOuting(oid) }) {
                    Icon(Icons.Filled.PlayCircle, "Replay outing", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                if (expanded) "Collapse" else "Show trips",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                group.rows.forEach { row ->
                    TripRow(row) { onOpenTrip(row.leg.id) }
                    Spacer(Modifier.height(8.dp))
                }
            }
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
