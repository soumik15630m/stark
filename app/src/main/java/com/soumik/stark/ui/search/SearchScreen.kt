package com.soumik.stark.ui.search

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.core.util.Format
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)
    val legs: StateFlow<List<Leg>> = repo.observeClosedLegs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val placeNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    init { viewModelScope.launch { placeNames.value = repo.placeDao.all().mapNotNull { p -> p.name?.let { p.id to it } }.toMap() } }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenTrip: (Long) -> Unit, vm: SearchViewModel = viewModel()) {
    val legs by vm.legs.collectAsStateWithLifecycle()
    val names by vm.placeNames.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf<TravelMode?>(null) }
    var minKm by remember { mutableFloatStateOf(0f) }

    val results = legs.filter { leg ->
        (mode == null || leg.mode == mode) &&
            leg.distanceM >= minKm * 1000 &&
            (query.isBlank() || run {
                val hay = buildString {
                    leg.label?.let { append(it).append(' ') }
                    names[leg.startPlaceId]?.let { append(it).append(' ') }
                    names[leg.endPlaceId]?.let { append(it) }
                }
                hay.contains(query, ignoreCase = true)
            })
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Search trips") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Place or label") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(mode == null, { mode = null }, label = { Text("All") })
                FilterChip(mode == TravelMode.VEHICLE, { mode = TravelMode.VEHICLE }, label = { Text("Bike") })
                FilterChip(mode == TravelMode.WALK, { mode = TravelMode.WALK }, label = { Text("Walk") })
                FilterChip(mode == TravelMode.RUN, { mode = TravelMode.RUN }, label = { Text("Run") })
                FilterChip(mode == TravelMode.BICYCLE, { mode = TravelMode.BICYCLE }, label = { Text("Cycle") })
            }
            Text("Min distance: ${minKm.toInt()} km", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Slider(minKm, { minKm = it }, valueRange = 0f..50f)
            Text("${results.size} trips", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { leg ->
                    com.soumik.stark.ui.common.SectionCard(Modifier.fillMaxWidth().clickable { onOpenTrip(leg.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(leg.label ?: "${names[leg.startPlaceId] ?: "Start"} → ${names[leg.endPlaceId] ?: "End"}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                                Text(dateStr(leg.dateKey), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${Format.km(leg.distanceM)} km", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun dateStr(k: Int) = "${k % 100}/${(k / 100) % 100}/${k / 10000}"
