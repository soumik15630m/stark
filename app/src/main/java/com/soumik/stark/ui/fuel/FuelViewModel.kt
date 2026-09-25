package com.soumik.stark.ui.fuel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.data.entity.FuelFill
import com.soumik.stark.data.entity.Setting
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.domain.fuel.FuelEstimator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FuelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    companion object {
        const val KEY_TANK_L = "fuel_tank_l"
        const val KEY_RESERVE_L = "fuel_reserve_l"
    }

    val tankL: StateFlow<Double> = repo.settingDao.observe(KEY_TANK_L)
        .map { it?.toDoubleOrNull() ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val reserveL: StateFlow<Double> = repo.settingDao.observe(KEY_RESERVE_L)
        .map { it?.toDoubleOrNull() ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Fills + tank/reserve → the fully-derived ledger. Current odometer read once per emission. */
    val result: StateFlow<FuelEstimator.Result> =
        combine(repo.fuelDao.observeAll(), tankL, reserveL) { fills, tank, reserve ->
            val odo = repo.totalsDao.lifetime()?.distanceAllM ?: 0.0
            FuelEstimator.estimate(fills, tank, reserve, odo)
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            FuelEstimator.Result(emptyList(), null, null, null, null, null, null),
        )

    fun addFill(
        litres: Double,
        cost: Double,
        dateMillis: Long,
        filledToFull: Boolean,
        ranDry: Boolean,
        onReserve: Boolean,
        note: String?,
    ) = viewModelScope.launch {
        val odo = repo.totalsDao.lifetime()?.distanceAllM ?: 0.0
        repo.fuelDao.insert(
            FuelFill(
                t = dateMillis, litres = litres, costInr = cost, odoMAtFill = odo,
                note = note?.ifBlank { null },
                filledToFull = filledToFull, ranDryBefore = ranDry, onReserveBefore = onReserve,
            )
        )
    }

    fun setTank(litres: Double) = viewModelScope.launch {
        repo.settingDao.put(Setting(KEY_TANK_L, litres.toString()))
    }

    fun setReserve(litres: Double) = viewModelScope.launch {
        repo.settingDao.put(Setting(KEY_RESERVE_L, litres.toString()))
    }

    fun delete(id: Long) = viewModelScope.launch { repo.fuelDao.delete(id) }
}
