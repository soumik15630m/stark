package com.soumik.stark.ui.fuel

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.common.StatColumn
import java.util.Locale

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
private fun f0(v: Double) = String.format(Locale.US, "%.0f", v)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FuelScreen(onBack: () -> Unit, vm: FuelViewModel = viewModel()) {
    val result by vm.result.collectAsStateWithLifecycle()
    val tankL by vm.tankL.collectAsStateWithLifecycle()
    val reserveL by vm.reserveL.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Fuel & mileage") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { StatusCard(result, tankL, reserveL) }
            item { TankSetupCard(tankL, reserveL, onTank = vm::setTank, onReserve = vm::setReserve) }
            item { AddFillCard(onAdd = vm::addFill) }
            if (result.rows.isEmpty()) {
                item { Text("No fills logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            } else {
                item { Text("Ledger", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
                items(result.rows, key = { it.fill.id }) { row -> LedgerRow(row, onDelete = { vm.delete(row.fill.id) }) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatusCard(result: com.soumik.stark.domain.fuel.FuelEstimator.Result, tankL: Double, reserveL: Double) {
    SectionCard(Modifier.fillMaxWidth()) {
        Text("Tank", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        FuelBar(level = result.currentLevelL, capacity = tankL, reserve = reserveL)
        Spacer(Modifier.height(4.dp))
        Text(
            result.currentLevelL?.let { "~${f1(it)} L${if (tankL > 0) " of ${f1(tankL)} L" else ""}" }
                ?: "Add a full or run-dry fill to estimate the level",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatColumn("Range", result.rangeKm?.let { "~${f0(it)} km" } ?: "—")
            StatColumn("Mileage", result.kmPerL?.let { "~${f1(it)} km/l" } ?: "—")
            StatColumn("Cost", result.avgCostPerKm?.let { "~₹${f2(it)}/km" } ?: "—")
        }
        result.distanceToReserveKm?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                if (it > 0) "~${f0(it)} km until reserve" else "Running on reserve",
                style = MaterialTheme.typography.bodySmall,
                color = if (it > 0) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFE57373),
            )
        }
        Text(
            "Estimates (~) are derived from your fills and distance; they sharpen with more fills.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun FuelBar(level: Double?, capacity: Double, reserve: Double) {
    val frac = if (capacity > 0 && level != null) (level / capacity).coerceIn(0.0, 1.0).toFloat() else 0f
    val low = level != null && reserve > 0 && level <= reserve
    Box(
        Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF19212A)),
    ) {
        Box(
            Modifier.fillMaxWidth(frac).height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(if (low) Color(0xFFE57373) else MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun TankSetupCard(tankL: Double, reserveL: Double, onTank: (Double) -> Unit, onReserve: (Double) -> Unit) {
    var tank by remember(tankL) { mutableStateOf(if (tankL > 0) f1(tankL) else "") }
    var reserve by remember(reserveL) { mutableStateOf(if (reserveL > 0) f1(reserveL) else "") }
    SectionCard(Modifier.fillMaxWidth()) {
        Text("Tank setup", style = MaterialTheme.typography.titleMedium)
        Text("Enter these once — everything above is derived from them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                tank, { tank = it; it.toDoubleOrNull()?.let(onTank) },
                label = { Text("Full tank (L)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                reserve, { reserve = it; it.toDoubleOrNull()?.let(onReserve) },
                label = { Text("Reserve (L)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AddFillCard(onAdd: (Double, Double, Long, Boolean, Boolean, Boolean, String?) -> Unit) {
    var litres by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(false) }
    var dry by remember { mutableStateOf(false) }
    var reserve by remember { mutableStateOf(false) }

    SectionCard(Modifier.fillMaxWidth()) {
        Text("Log a fill", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(litres, { litres = it }, label = { Text("Litres added") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(cost, { cost = it }, label = { Text("Cost (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = full, onClick = { full = !full }, label = { Text("Filled full") })
            FilterChip(selected = dry, onClick = { dry = !dry }, label = { Text("Ran dry") })
            FilterChip(selected = reserve, onClick = { reserve = !reserve }, label = { Text("On reserve") })
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                val l = litres.toDoubleOrNull(); val c = cost.toDoubleOrNull() ?: 0.0
                if (l != null) {
                    onAdd(l, c, System.currentTimeMillis(), full, dry, reserve, note)
                    litres = ""; cost = ""; note = ""; full = false; dry = false; reserve = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add fill") }
        Text("Tick “Filled full” or “Ran dry” whenever you can — those anchor the mileage math.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LedgerRow(row: com.soumik.stark.domain.fuel.FuelRow, onDelete: () -> Unit) {
    val f = row.fill
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${f2(f.litres)} L • ₹${f0(f.costInr)}", fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(row.segmentKmPerL?.let { "${f1(it)} km/l" } ?: "no anchor yet")
                        row.costPerKm?.let { append(" • ₹${f2(it)}/km") }
                        row.kmSinceLast?.let { append(" • ${f0(it)} km since last") }
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.levelAfterL?.let { Text("tank ~${f1(it)} L after", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val flags = buildList {
                    if (f.filledToFull) add("full")
                    if (f.ranDryBefore) add("ran dry")
                    if (f.onReserveBefore) add("reserve")
                }
                if (flags.isNotEmpty()) Text(flags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                f.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}
