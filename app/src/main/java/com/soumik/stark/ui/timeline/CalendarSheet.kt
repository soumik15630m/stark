package com.soumik.stark.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private fun keyOf(d: LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(
    currentKey: Int,
    dailyKm: Map<Int, Double>,
    streakDays: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = LocalDate.of(currentKey / 10000, (currentKey / 100) % 100, currentKey % 100)
    var month by remember { mutableStateOf(YearMonth.of(selected.year, selected.monthValue)) }
    val today = LocalDate.now()
    // Subtle intensity scale relative to the busiest day in view.
    val maxKm = (dailyKm.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
                Text(
                    "${monthName(month.monthValue)} ${month.year}",
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "Next month") }
            }

            if (streakDays > 0) {
                Text(
                    "🔥 $streakDays day${if (streakDays == 1) "" else "s"} riding streak",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(Modifier.fillMaxWidth()) {
                for (h in listOf("M", "T", "W", "T", "F", "S", "S")) {
                    Text(h, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))

            val first = month.atDay(1)
            val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7 // Monday-based offset
            val daysInMonth = month.lengthOfMonth()
            val cells = lead + daysInMonth
            val rows = (cells + 6) / 7

            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        val dayNum = cellIndex - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..daysInMonth) {
                                val date = month.atDay(dayNum)
                                val key = keyOf(date)
                                val km = dailyKm[key] ?: 0.0
                                val future = date.isAfter(today)
                                val level = if (km <= 0) 0f else (0.25f + 0.75f * (km / maxKm).toFloat()).coerceIn(0f, 1f)
                                val bg = when {
                                    future -> Color.Transparent
                                    level == 0f -> Color(0xFF20272E)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = level)
                                }
                                val isSelected = key == currentKey
                                val base = Modifier.fillMaxWidth().aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(bg)
                                    .clickable(enabled = !future) { onJump(key); onDismiss() }
                                Box(
                                    if (isSelected) base.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else base,
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$dayNum",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (level > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun monthName(m: Int) =
    arrayOf("", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[m]
