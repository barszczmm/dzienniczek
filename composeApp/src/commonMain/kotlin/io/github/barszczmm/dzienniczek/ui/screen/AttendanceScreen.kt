package io.github.barszczmm.dzienniczek.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.barszczmm.dzienniczek.ui.model.AttendanceEntry
import io.github.barszczmm.dzienniczek.ui.model.AttendanceKind
import io.github.barszczmm.dzienniczek.viewmodel.AttendanceFilter
import io.github.barszczmm.dzienniczek.viewmodel.AttendanceViewModel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.koin.compose.viewmodel.koinViewModel

// Colours follow the eduVulcan legend.
private val ColorAbsent = Color(0xFFE53935)
private val ColorExcused = Color(0xFF1E88E5)
private val ColorLate = Color(0xFFF9A825)
private val ColorLateExcused = Color(0xFF8E24AA)
private val ColorReleased = Color(0xFF607D8B)

fun AttendanceKind.color(): Color = when (this) {
    AttendanceKind.ABSENT -> ColorAbsent
    AttendanceKind.ABSENT_EXCUSED -> ColorExcused
    AttendanceKind.LATE -> ColorLate
    AttendanceKind.LATE_EXCUSED -> ColorLateExcused
    AttendanceKind.RELEASED -> ColorReleased
}

private fun AttendanceKind.icon(): ImageVector = when (this) {
    AttendanceKind.ABSENT -> Icons.Filled.Close
    AttendanceKind.ABSENT_EXCUSED -> Icons.Filled.Check
    AttendanceKind.LATE, AttendanceKind.LATE_EXCUSED -> Icons.Filled.Schedule
    AttendanceKind.RELEASED -> Icons.AutoMirrored.Filled.ExitToApp
}

private fun AttendanceKind.label(): String = when (this) {
    AttendanceKind.ABSENT -> "Nieobecność"
    AttendanceKind.ABSENT_EXCUSED -> "Nieobecność usprawiedliwiona"
    AttendanceKind.LATE -> "Spóźnienie"
    AttendanceKind.LATE_EXCUSED -> "Spóźnienie usprawiedliwione"
    AttendanceKind.RELEASED -> "Zwolnienie / przyczyny szkolne"
}

/** "1 lekcja", "2 lekcje", "5 lekcji". */
fun lessonsCount(n: Int): String = "$n " + when {
    n == 1 -> "lekcja"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "lekcje"
    else -> "lekcji"
}

private val DAY_NAMES = mapOf(
    DayOfWeek.MONDAY to "Poniedziałek", DayOfWeek.TUESDAY to "Wtorek", DayOfWeek.WEDNESDAY to "Środa",
    DayOfWeek.THURSDAY to "Czwartek", DayOfWeek.FRIDAY to "Piątek", DayOfWeek.SATURDAY to "Sobota",
    DayOfWeek.SUNDAY to "Niedziela"
)

private fun LocalDate.label(): String =
    "${DAY_NAMES[dayOfWeek] ?: ""}, ${day.toString().padStart(2, '0')}.${month.number.toString().padStart(2, '0')}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(viewModel: AttendanceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<AttendanceEntry?>(null) }

    details?.let { entry ->
        AlertDialog(
            onDismissRequest = { details = null },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Zamknij") } },
            title = { Text(entry.typeName.ifBlank { entry.kind.label() }) },
            text = {
                Column {
                    Text("Data: ${entry.date.label()}.${entry.date.year}")
                    entry.lessonNumber?.let { Text("Lekcja: $it" + (entry.time?.let { t -> " ($t)" } ?: "")) }
                    if (entry.subject.isNotBlank()) Text("Przedmiot: ${entry.subject}")
                    if (entry.teacher.isNotBlank()) Text("Nauczyciel: ${entry.teacher}")
                }
            }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val options = listOf(1 to "Okres 1", 2 to "Okres 2", 0 to "Cały rok")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    options.forEachIndexed { index, (value, title) ->
                        SegmentedButton(
                            selected = state.semester == value,
                            onClick = { viewModel.setSemester(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            icon = {},
                            label = { Text(title, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                IconButton(onClick = { viewModel.load(forceRefresh = true) }, enabled = !state.isLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryTile(state.unexcused, "nieuspraw.", ColorAbsent, state.filter == AttendanceFilter.UNEXCUSED, Modifier.weight(1f)) {
                    viewModel.setFilter(AttendanceFilter.UNEXCUSED)
                }
                SummaryTile(state.excused, "uspraw.", ColorExcused, state.filter == AttendanceFilter.EXCUSED, Modifier.weight(1f)) {
                    viewModel.setFilter(AttendanceFilter.EXCUSED)
                }
                SummaryTile(state.late, "spóźnienia", ColorLate, state.filter == AttendanceFilter.LATE, Modifier.weight(1f)) {
                    viewModel.setFilter(AttendanceFilter.LATE)
                }
            }
            if (viewModel.justifyUrl != null && state.toJustify > 0) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.openJustification() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Usprawiedliw w eduVulcan")
                }
                Text(
                    text = "Otwiera frekwencję w eduVulcan w przeglądarce (przycisk „Usprawiedliw”). " +
                        "Jeśli trzeba było się zalogować i otworzyła się „Tablica”, wybierz „Frekwencja”. " +
                        "Po wysłaniu odśwież tę listę ↻.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        when {
            state.isLoading && state.all.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> item { ErrorScreen(state.error!!, onRetry = { viewModel.load() }) }
            state.visible.isEmpty() -> item {
                Text(
                    text = if (state.filter == AttendanceFilter.ALL) "Brak nieobecności i spóźnień 🎉" else "Brak wpisów tego rodzaju",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth()
                )
            }
            else -> {
                state.visible.groupBy { it.date }.forEach { (date, entries) ->
                    item(key = "d$date") {
                        Text(
                            text = date.label().uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                        )
                    }
                    entries.groupBy { it.kind }.forEach { (kind, group) ->
                        item(key = "d$date-$kind") {
                            DayKindCard(kind, group, onClick = { details = it })
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryTile(
    count: Int,
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        border = if (selected) BorderStroke(2.dp, color) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun DayKindCard(kind: AttendanceKind, entries: List<AttendanceEntry>, onClick: (AttendanceEntry) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).background(kind.color(), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(kind.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = entries.first().typeName.ifBlank { kind.label() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = lessonsCount(entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            entries.forEach { entry ->
                Text(
                    text = listOfNotNull(entry.lessonNumber?.let { "$it." }, entry.subject.ifBlank { null })
                        .joinToString(" ").ifBlank { "lekcja" } + (entry.time?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick(entry) }
                        .padding(start = 40.dp, top = 4.dp, bottom = 4.dp)
                )
            }
        }
    }
}
