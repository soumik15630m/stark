package com.soumik.stark.ui.fuel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.data.entity.FuelFill
import com.soumik.stark.data.repo.TrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FillRow(val fill: FuelFill, val kmPerL: Double?, val costPerKm: Double?)

class FuelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    val fills: StateFlow<List<FillRow>> =
        repo.fuelDao.observeAll().map { list ->
            val asc = list.sortedBy { it.t }
            val rows = ArrayList<FillRow>()
            for (i in asc.indices) {
                val f = asc[i]
                if (i == 0) {
                    rows.add(FillRow(f, null, null))
                } else {
                    val prev = asc[i - 1]
                    val km = (f.odoMAtFill - prev.odoMAtFill) / 1000.0
                    val kmpl = if (f.litres > 0 && km > 0) km / f.litres else null
                    val cpk = if (km > 0) f.costInr / km else null
                    rows.add(FillRow(f, kmpl, cpk))
                }
            }
            rows.reversed()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFill(litres: Double, cost: Double, note: String?) = viewModelScope.launch {
        val odo = repo.totalsDao.lifetime()?.distanceAllM ?: 0.0
        repo.fuelDao.insert(FuelFill(t = System.currentTimeMillis(), litres = litres, costInr = cost, odoMAtFill = odo, note = note?.ifBlank { null }))
    }

    fun delete(id: Long) = viewModelScope.launch { repo.fuelDao.delete(id) }
}
