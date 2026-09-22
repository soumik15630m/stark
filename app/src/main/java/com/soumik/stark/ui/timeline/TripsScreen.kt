package com.soumik.stark.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.core.util.Format
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.ui.common.SectionCard

@Composable
fun TripsScreen(vm: TripsViewModel = viewModel()) {
    val byDay by vm.byDay.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        if (byDay.isEmpty()) {
            item {
                Text(
                    "Your ride timeline will fill in here as you ride.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        byDay.forEach { (dateKey, legs) ->
            item(key = "h$dateKey") {
                val dayKm = legs.sumOf { it.distanceM }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDate(dateKey),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${Format.km(dayKm)} km",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(legs.size, key = { legs[it].id }) { i -> TripRow(legs[i]) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TripRow(leg: Leg) {
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${Format.clock(leg.startT, leg.offsetMin)}${leg.endT?.let { " – " + Format.clock(it, leg.offsetMin) } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${Format.km(leg.distanceM)} km",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.duration(leg.durationS), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "max ${Format.kmh(leg.maxSpeedMps)} • ${leg.pointCount} pts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDate(dateKey: Int): String {
    val y = dateKey / 10000
    val m = (dateKey / 100) % 100
    val d = dateKey % 100
    val months = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "$d ${months.getOrElse(m) { "" }} $y"
}
