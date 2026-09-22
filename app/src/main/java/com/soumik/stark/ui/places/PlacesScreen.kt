package com.soumik.stark.ui.places

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.data.entity.Place
import com.soumik.stark.data.entity.PlaceCategory
import com.soumik.stark.data.repo.TrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlacesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)
    val places: StateFlow<List<Place>> = repo.placeDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun rename(id: Long, name: String) = viewModelScope.launch { repo.renamePlace(id, name) }
    fun setCategory(id: Long, c: PlaceCategory) = viewModelScope.launch { repo.setPlaceCategory(id, c) }
    fun setHome(id: Long) = viewModelScope.launch { repo.setHomePlace(id) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlacesScreen(onBack: () -> Unit, vm: PlacesViewModel = viewModel()) {
    val places by vm.places.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Places") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            if (places.isEmpty()) {
                item { Text("Places you visit repeatedly will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            }
            items(places, key = { it.id }) { place ->
                com.soumik.stark.ui.common.SectionCard(Modifier.fillMaxWidth()) {
                    var name by remember(place.id, place.name) { mutableStateOf(place.name ?: "") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true)
                        IconButton(onClick = { vm.setHome(place.id) }) {
                            Icon(Icons.Filled.Home, "Set home", tint = if (place.isBaseHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("${place.visitCount} visits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlaceCategory.entries.forEach { cat ->
                            FilterChip(place.category == cat, { vm.setCategory(place.id, cat) }, label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    AssistChip(onClick = { vm.rename(place.id, name) }, label = { Text("Save name") })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
