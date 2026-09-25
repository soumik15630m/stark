package com.soumik.stark.domain.fuel

import com.soumik.stark.data.entity.FuelFill

/** One fill plus everything the ledger derives for it. */
data class FuelRow(
    val fill: FuelFill,
    val levelAfterL: Double?,   // estimated tank level right after this fill, if anchorable
    val kmSinceLast: Double?,   // odometer km since the previous fill
    val segmentKmPerL: Double?, // km/l measured over the anchored segment ending at this fill
    val costPerKm: Double?,     // ₹/km over that segment
)

/**
 * Smart fuel ledger (design: derive mileage/level from sparse, partial fills). Refuels are random
 * amounts, so absolute fuel level is only *known* at anchors:
 *   - `ranDryBefore`  → level was 0 just before the fill,
 *   - `filledToFull`  → level is the tank capacity just after the fill.
 * Between two anchors the litres consumed over the odometer distance give a km/l sample. Samples
 * are averaged with more weight on recent ones. Current level = last anchor − distance-since ÷ km/l.
 *
 * Pure and deterministic so it can be unit-tested without Android.
 */
object FuelEstimator {

    data class Result(
        val rows: List<FuelRow>,        // newest-first, mirroring the UI list order
        val kmPerL: Double?,            // best current mileage estimate
        val pricePerL: Double?,         // latest known ₹/L
        val currentLevelL: Double?,     // estimated litres in the tank right now
        val rangeKm: Double?,           // level × km/l
        val distanceToReserveKm: Double?, // (level − reserve) × km/l, floored at 0
        val avgCostPerKm: Double?,      // lifetime ₹ / lifetime km across anchored segments
    )

    /**
     * @param fills any order; distance comes from [FuelFill.odoMAtFill] (lifetime metres).
     * @param tankL full-tank capacity in litres (0/unknown → capacity anchors unavailable).
     * @param reserveL reserve litres.
     * @param currentOdoM current lifetime distance in metres (for the live level estimate).
     */
    fun estimate(fills: List<FuelFill>, tankL: Double, reserveL: Double, currentOdoM: Double): Result {
        val asc = fills.sortedBy { it.odoMAtFill }
        val capacity = if (tankL > 0) tankL else null

        val rows = ArrayList<FuelRow>(asc.size)
        val samples = ArrayList<Pair<Double, Double>>() // (km, litresUsed) over anchored segments

        var level: Double? = null                 // running level after the previous fill (if known)
        var anchorOdoM: Double? = null            // odometer at the last absolute anchor
        var anchorLevel: Double? = null           // level at that anchor

        for ((i, f) in asc.withIndex()) {
            val prev = asc.getOrNull(i - 1)
            val kmSinceLast = if (prev != null) (f.odoMAtFill - prev.odoMAtFill) / 1000.0 else null

            val preLevel: Double? = if (f.ranDryBefore) 0.0 else level

            // A consumption sample closes whenever we can pin the level at both ends of a segment.
            var segKmPerL: Double? = null
            if (anchorOdoM != null && anchorLevel != null) {
                val km = (f.odoMAtFill - anchorOdoM) / 1000.0
                val litresUsed: Double? = when {
                    // Full → full: the litres just added equal what was burned since the last full.
                    anchorLevel == capacity && f.filledToFull -> f.litres
                    // Any known start level → a known pre-fill level (e.g. after-full → ran-dry).
                    preLevel != null -> anchorLevel!! - preLevel
                    else -> null
                }
                if (litresUsed != null && litresUsed > 0 && km > 0) {
                    samples.add(km to litresUsed)
                    segKmPerL = km / litresUsed
                }
            }

            // Post-fill level + new anchor.
            val postLevel: Double? = when {
                f.filledToFull -> capacity
                preLevel != null -> preLevel + f.litres
                else -> null
            }
            if (postLevel != null) {
                level = postLevel
                anchorOdoM = f.odoMAtFill
                anchorLevel = postLevel
            } else {
                level = null // unknown base breaks the chain until the next full/dry anchor
            }

            val costPerKm = if (segKmPerL != null && f.litres > 0)
                (f.costInr / f.litres) / segKmPerL else null
            rows.add(FuelRow(f, level, kmSinceLast, segKmPerL, costPerKm))
        }

        val kmPerL = recencyWeighted(samples)
        val pricePerL = asc.lastOrNull { it.litres > 0 }?.let { it.costInr / it.litres }

        val currentLevel: Double? = if (anchorOdoM != null && anchorLevel != null && kmPerL != null) {
            val kmSince = (currentOdoM - anchorOdoM!!) / 1000.0
            val used = kmSince / kmPerL
            (anchorLevel!! - used).coerceIn(0.0, capacity ?: Double.MAX_VALUE)
        } else null

        val range = if (currentLevel != null && kmPerL != null) currentLevel * kmPerL else null
        val toReserve = if (currentLevel != null && kmPerL != null)
            ((currentLevel - reserveL).coerceAtLeast(0.0)) * kmPerL else null

        val totalKm = samples.sumOf { it.first }
        val totalCost = rows.mapNotNull { r -> r.segmentKmPerL?.let { r.fill.costInr } }.sum()
        val avgCostPerKm = if (totalKm > 0 && totalCost > 0) totalCost / totalKm else null

        return Result(rows.reversed(), kmPerL, pricePerL, currentLevel, range, toReserve, avgCostPerKm)
    }

    /** Weighted mean km/l — newest sample weighted highest (weight = index+1). */
    private fun recencyWeighted(samples: List<Pair<Double, Double>>): Double? {
        if (samples.isEmpty()) return null
        var wSum = 0.0
        var acc = 0.0
        samples.forEachIndexed { i, (km, litres) ->
            val w = (i + 1).toDouble()
            acc += (km / litres) * w
            wSum += w
        }
        return if (wSum > 0) acc / wSum else null
    }
}
