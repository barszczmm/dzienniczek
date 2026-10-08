package io.github.barszczmm.dzienniczek.ui.screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Looks6
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.barszczmm.dzienniczek.navigation.Route
import io.github.barszczmm.dzienniczek.theme.expressiveGroupShape
import io.github.barszczmm.dzienniczek.viewmodel.StartViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartScreen(
    onNavigate: (Route) -> Unit,
    viewModel: StartViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        if (state.firstName.isNotBlank()) {
            item {
                Text(
                    text = "Cześć, ${state.firstName}!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }


        if (state.unexcusedAbsences > 0) {
            item {
                ElevatedCard(
                    onClick = { onNavigate(Route.Attendance) },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.EventBusy, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = unexcusedLabel(state.unexcusedAbsences),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "w tym roku szkolnym – dotknij, aby zobaczyć",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                onClick = { onNavigate(Route.Grades) },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Looks6,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Ostatnie oceny",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (state.recentGrades.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.recentGrades.forEach { grade ->
                                GradeChip(grade)
                            }
                        }
                    } else {
                        EmptinessText("Brak nowych ocen z ostatnich 7 dni.")
                    }
                }
            }
        }

        item {
            ElevatedCard(
                onClick = { onNavigate(Route.Exams) },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Najbliższe sprawdziany",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (state.upcomingExams.isNotEmpty()) {
                        Column {
                            state.upcomingExams.forEachIndexed { index, exam ->
                                ExamCard(
                                    exam = exam,
                                    shape = expressiveGroupShape(index = index, count = state.upcomingExams.size),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                if (index < state.upcomingExams.size - 1) {
                                    Spacer(Modifier.height(3.dp))
                                }
                            }
                        }
                    } else {
                        EmptinessText("Brak sprawdzianów zapowiedzianych na nadchodzący tydzień.")
                    }
                }
            }
        }

        item {
            ElevatedCard(
                onClick = { onNavigate(Route.Homework) },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Nadchodzące zadania domowe",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (state.upcomingHomework.isNotEmpty()) {
                        Column {
                            state.upcomingHomework.forEachIndexed { index, hw ->
                                HomeworkCard(
                                    hw = hw,
                                    shape = expressiveGroupShape(index = index, count = state.upcomingHomework.size),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                if (index < state.upcomingHomework.size - 1) {
                                    Spacer(Modifier.height(3.dp))
                                }
                            }
                        }
                    } else {
                        EmptinessText("Brak zadań domowych z terminem na najbliższe 7 dni.")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptinessText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** "1 nieusprawiedliwiona nieobecność", "3 nieusprawiedliwione nieobecności", "5 nieusprawiedliwionych nieobecności". */
private fun unexcusedLabel(n: Int): String = when {
    n == 1 -> "1 nieusprawiedliwiona nieobecność"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n nieusprawiedliwione nieobecności"
    else -> "$n nieusprawiedliwionych nieobecności"
}
