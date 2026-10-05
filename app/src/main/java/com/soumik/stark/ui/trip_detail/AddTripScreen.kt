package com.soumik.stark.ui.trip_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.ui.common.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddTripScreen(dateKey: Int, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialDate = remember(dateKey) {
        LocalDate.of(dateKey / 10000, (dateKey / 100) % 100, dateKey % 100)
    }
    var date by remember { mutableStateOf(initialDate.toString()) } // yyyy-MM-dd
    var time by remember { mutableStateOf("09:00") }
    var durationMin by remember { mutableStateOf("") }
    var distanceKm by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TravelMode.VEHICLE) }
    var error by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Add a trip") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(Modifier.fillMaxWidth()) {
                Text("When", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(time, { time = it }, label = { Text("Start (HH:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Text("Trip", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(distanceKm, { distanceKm = it }, label = { Text("Distance (km)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(durationMin, { durationMin = it }, label = { Text("Duration (minutes)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(label, { label = it }, label = { Text("Label (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Bike", mode == TravelMode.VEHICLE) { mode = TravelMode.VEHICLE }
                    ModeChip("Walk", mode == TravelMode.WALK) { mode = TravelMode.WALK }
                    ModeChip("Run", mode == TravelMode.RUN) { mode = TravelMode.RUN }
                    ModeChip("Cycle", mode == TravelMode.BICYCLE) { mode = TravelMode.BICYCLE }
                }
            }

            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = {
                    val startT = parseStart(date, time)
                    val dist = distanceKm.replace(',', '.').toDoubleOrNull()
                    val durMin = durationMin.toLongOrNull()
                    when {
                        startT == null -> error = "Check the date and time (YYYY-MM-DD and HH:MM)."
                        dist == null || dist <= 0 -> error = "Enter a distance in km."
                        durMin == null || durMin <= 0 -> error = "Enter a duration in minutes."
                        else -> {
                            error = ""
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    TrackRepository.get(context).addManualLeg(
                                        startT = startT,
                                        durationS = durMin * 60,
                                        distanceM = dist * 1000.0,
                                        mode = mode,
                                        label = label,
                                    )
                                }
                                com.soumik.stark.ui.widget.TodayWidget.refresh(context)
                                onSaved()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save trip") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(label) })
}

/** Build an epoch-millis start time from local date + time text, or null if either is malformed. */
private fun parseStart(date: String, time: String): Long? = try {
    val d = LocalDate.parse(date.trim())
    val parts = time.trim().split(":")
    val lt = LocalTime.of(parts[0].toInt(), parts.getOrElse(1) { "0" }.toInt())
    d.atTime(lt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}
