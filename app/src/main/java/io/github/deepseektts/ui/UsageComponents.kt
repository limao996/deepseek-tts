package io.github.deepseektts.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.deepseektts.billing.DayUsage
import io.github.deepseektts.billing.TtsPricing
import io.github.deepseektts.billing.UsageSnapshot
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun UsagePage(usage: UsageSnapshot?, onClearUsage: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (usage == null) {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text("正在读取本地统计…", Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        } else if (usage.isEmpty) {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text("暂无朗读记录。完成一次试听或系统朗读后，这里会显示统计与热力图。", Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            OverviewCard(usage)
            HeatmapCard(usage.days, usage.maxDayCharacters)
        }
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.align(Alignment.Start)) { Text("清除本地统计") }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清除本地统计？") },
        text = { Text("仅删除本机统计记录，不影响 DeepSeek 账单。") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClearUsage() }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
}

@Composable
private fun OverviewCard(usage: UsageSnapshot) {
    val groups = listOf("今日" to usage.todayCharacters, "近 7 天" to usage.weekCharacters, "近 30 天" to usage.monthCharacters, "累计" to usage.totalCharacters)
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("用量概览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            groups.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { (label, chars) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatCount(chars) + " 字符", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(TtsPricing.formatCny(TtsPricing.costCny(chars)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                }
            }
            Text("按官方限时价 ¥1.5 / 万字符估算，以 DeepSeek 账单为准。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeatmapCard(days: List<DayUsage>, maxDayCharacters: Int) {
    val gridColor = heatColors()
    val ordered = remember(days) { days.associateBy { it.date } }
    val today = days.lastOrNull()?.date ?: LocalDate.now()
    val start = today.minusDays(181).with(DayOfWeek.MONDAY)
    val columns = (ChronoUnit.DAYS.between(start, today).toInt() / 7) + 1
    // The newest weeks matter most, so the grid opens scrolled to the current week.
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { if (scroll.maxValue > 0) scroll.scrollTo(scroll.maxValue) }
    // Month spans drive the axis label widths so labels line up with the columns below them.
    val monthSpans = remember(columns, start) {
        buildList {
            var runStart = 0
            var runMonth = start.monthValue
            for (col in 1 until columns) {
                val month = start.plusWeeks(col.toLong()).monthValue
                if (month != runMonth) {
                    add(runMonth to (col - runStart))
                    runStart = col
                    runMonth = month
                }
            }
            add(runMonth to (columns - runStart))
        }
    }
    val cell = 12.dp
    val gap = 3.dp
    val axisStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp)
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("每日用量", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("最近约 6 个月 · " + formatCount(days.sumOf { it.characters }) + " 字符", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Top) {
                // Weekday labels share the grid's row pitch (cell + gap) so each label sits on its row.
                // 15dp month-label row + 2dp top padding of the grid keeps rows and labels in sync.
                Column(Modifier.padding(top = 17.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Box(Modifier.size(width = 15.dp, height = cell), contentAlignment = Alignment.CenterStart) {
                            Text(label, style = axisStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                Column(Modifier.horizontalScroll(scroll)) {
                    // Month labels live inside the scroll container so they track the columns they name.
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        monthSpans.forEach { (month, span) ->
                            val spanWidth = cell * span + gap * (span - 1)
                            Box(Modifier.size(width = spanWidth, height = 15.dp), contentAlignment = Alignment.BottomStart) {
                                Text(month.toString() + " 月", style = axisStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    Row(Modifier.padding(end = 8.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(columns) { col ->
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                repeat(7) { row ->
                                    val date = start.plusDays((col * 7L) + row)
                                    // Days after today have not happened yet: reserve the slot so the
                                    // week rows stay aligned, but draw nothing.
                                    if (date.isAfter(today)) {
                                        Spacer(Modifier.size(cell))
                                        return@repeat
                                    }
                                    val entry = ordered[date]
                                    val level = if (entry == null) 0 else intensity(entry.characters, maxDayCharacters)
                                    androidx.compose.material3.Surface(color = gridColor[level], shape = RoundedCornerShape(3.dp), modifier = Modifier.size(cell).semantics {
                                        contentDescription = if (entry != null) date.toString() + "：" + formatCount(entry.characters) + " 字符" else date.toString() + "：无记录"
                                    }) {}
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("少", style = axisStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(5.dp))
                gridColor.forEach { color -> androidx.compose.material3.Surface(color = color, shape = RoundedCornerShape(3.dp), modifier = Modifier.padding(horizontal = 2.dp).size(cell)) {} }
                Spacer(Modifier.size(5.dp))
                Text("多", style = axisStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun heatColors(): List<androidx.compose.ui.graphics.Color> = listOf(
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
    MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
    MaterialTheme.colorScheme.primary,
)

private fun intensity(value: Int, max: Int): Int {
    if (value <= 0 || max <= 0) return 0
    val ratio = value.toDouble() / max.toDouble()
    return when { ratio >= .8 -> 4; ratio >= .55 -> 3; ratio >= .3 -> 2; else -> 1 }
}

private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value.coerceAtLeast(0))
