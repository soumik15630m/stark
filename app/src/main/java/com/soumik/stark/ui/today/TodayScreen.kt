package com.soumik.stark.ui.today

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.core.util.Format
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.common.StatColumn
import com.soumik.stark.ui.common.animatedKm

@Composable
fun TodayScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSpeedo: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    vm: TodayViewModel = viewModel(),
) {
    val lifetime by vm.lifetime.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val trips by vm.todayTrips.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    var showLifetime by remember { mutableStateOf(true) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            OdometerHeader(
                heroBikeKm = if (showLifetime) (lifetime?.distanceBikeM ?: 0.0)
                else (today?.distanceBikeM ?: 0.0),
                allModesKm = if (showLifetime) (lifetime?.distanceAllM ?: 0.0)
                else (today?.distanceAllM ?: 0.0),
                showLifetime = showLifetime,
                onToggle = { showLifetime = !showLifetime },
            )
        }

        if (live.enabled) {
            item { LiveCard(live) }
        }

        item {
            Button(
                onClick = if (live.enabled) onStop else onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (live.enabled) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                ),
            ) {
                Icon(if (live.enabled) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                Spacer(Modifier.height(0.dp))
                Text(
                    if (live.enabled) "  Turn tracking off" else "  Turn tracking on",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatColumn("Today", "${Format.km(today?.distanceBikeM ?: 0.0)} km")
                    StatColumn("Trips", "${today?.tripCount ?: 0}")
                    StatColumn("All modes", "${Format.km(today?.distanceAllM ?: 0.0)} km")
                }
            }
        }

        item {
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Speedometer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenSpeedo) { Text("Open") }
                }
                Text(
                    "Full-screen dial for mounting on the bike.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                "Today's trips",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        if (trips.isEmpty()) {
            item {
                Text(
                    "No rides yet today. Take your first ride — your odometer starts here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
        } else {
            items(trips, key = { it.id }) { leg -> TripRow(leg, onClick = { onOpenTrip(leg.id) }) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OdometerHeader(
    heroBikeKm: Double,
    allModesKm: Double,
    showLifetime: Boolean,
    onToggle: () -> Unit,
) {
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showLifetime) "LIFETIME" else "TODAY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggle) {
                Text(if (showLifetime) "Show today" else "Show lifetime")
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                animatedKm(heroBikeKm),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "  km",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text(
            "bike • all modes ${Format.km(allModesKm)} km",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveCard(live: com.soumik.stark.tracking.service.LiveState) {
    SectionCard(Modifier.fillMaxWidth()) {
        val label = when (live.state) {
            com.soumik.stark.tracking.service.TrackState.ACTIVE -> "TRACKING"
            com.soumik.stark.tracking.service.TrackState.PAUSED -> "PAUSED"
            com.soumik.stark.tracking.service.TrackState.ARMED -> "ON • WAITING FOR A RIDE"
            else -> ""
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (live.state != com.soumik.stark.tracking.service.TrackState.ARMED) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("Speed", "${live.speedKmh.toInt()} km/h")
                StatColumn("Trip", "${Format.km(live.tripDistanceM)} km")
                StatColumn("Time", Format.duration(live.tripDurationS))
            }
        }
    }
}

@Composable
private fun TripRow(leg: Leg, onClick: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
                    "max ${Format.kmh(leg.maxSpeedMps)} km/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (leg.hasEstimatedGap) {
                    Text("~estimated", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB74D))
                }
            }
        }
    }
}
