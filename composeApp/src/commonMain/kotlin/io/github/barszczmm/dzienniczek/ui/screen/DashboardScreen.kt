package io.github.barszczmm.dzienniczek.ui.screen

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Looks6
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Looks6
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.barszczmm.dzienniczek.navigation.Route
import io.github.barszczmm.dzienniczek.theme.expressiveGroupShape
import io.github.barszczmm.dzienniczek.viewmodel.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

private enum class DashboardTab(
    val label: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector,
    val route: Route
) {
    START("Start", Icons.Outlined.Home, Icons.Filled.Home, Route.Start),
    TIMETABLE("Plan", Icons.Outlined.Backpack, Icons.Filled.Backpack, Route.Timetable),
    EXAMS("Sprawdziany", Icons.Outlined.CalendarToday, Icons.Filled.CalendarToday, Route.Exams),
    MESSAGES("Wiadomości", Icons.AutoMirrored.Outlined.Message, Icons.AutoMirrored.Filled.Message, Route.Messages),
    MORE("Więcej", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz, Route.More)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val activeStudent by viewModel.activeStudent.collectAsStateWithLifecycle()
    val studentSessions by viewModel.studentSessions.collectAsStateWithLifecycle()

    val currentAccount = viewModel.currentAccount
    val enabledStudents = studentSessions.filter { it.isEnabled }

    var showStudentBottomSheet by remember { mutableStateOf(false) }

    val backStack = remember { mutableStateListOf<Route>(Route.Start) }
    val currentRoute = backStack.lastOrNull()

    // Screens opened from "Więcej" – the "Więcej" tab stays selected and a back arrow is shown.
    val moreRoutes = listOf(
        Route.Grades,
        Route.Notes,
        Route.Announcements,
        Route.Homework,
        Route.Account,
        Route.Settings
    )
    val isSubScreen = currentRoute is Route.MessageDetails || currentRoute in moreRoutes

    val pageTitle = when (currentRoute) {
        is Route.Start -> "Start"
        is Route.Grades -> "Oceny"
        is Route.Timetable -> "Plan lekcji"
        is Route.Exams -> "Sprawdziany"
        is Route.Homework -> "Zadania domowe"
        is Route.More -> "Więcej"
        is Route.Notes -> "Uwagi"
        is Route.Announcements -> "Ogłoszenia"
        is Route.Messages -> "Wiadomości"
        is Route.MessageDetails -> "Wiadomość"
        is Route.Account -> "Konto"
        is Route.Settings -> "Ustawienia"
        else -> "Dzienniczek"
    }

    if (showStudentBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStudentBottomSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Konta uczniów",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                enabledStudents.forEachIndexed { index, student ->
                    val isSelected = student.id == activeStudent?.id
                    Surface(
                        onClick = {
                            showStudentBottomSheet = false
                            viewModel.selectStudent(student.id)
                        },
                        shape = expressiveGroupShape(index = index, count = enabledStudents.size),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val initials = "${student.account.pupil.firstName.firstOrNull() ?: ""}${student.account.pupil.surname.firstOrNull() ?: ""}"
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${student.account.pupil.firstName} ${student.account.pupil.surname}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = student.account.unit.displayName.ifBlank { student.account.unit.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    showStudentBottomSheet = false
                                    viewModel.selectStudent(student.id)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        showStudentBottomSheet = false
                        if (currentRoute != Route.More) {
                            backStack.clear()
                            backStack.add(Route.More)
                        }
                        backStack.add(Route.Account)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Zarządzaj uczniami / Dodaj konto")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (isSubScreen) {
                        IconButton(onClick = { backStack.removeLastOrNull() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Wróć"
                            )
                        }
                    }
                },
                actions = {
                    val initials = currentAccount?.let {
                        "${it.pupil.firstName.firstOrNull() ?: ""}${it.pupil.surname.firstOrNull() ?: ""}"
                    } ?: "?"

                    Surface(
                        onClick = { if (enabledStudents.isNotEmpty()) showStudentBottomSheet = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                DashboardTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == tab.route) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        selected = currentRoute == tab.route ||
                            (tab.route == Route.More && currentRoute in moreRoutes) ||
                            (tab.route == Route.Messages && currentRoute is Route.MessageDetails),
                        onClick = {
                            if (currentRoute != tab.route) {
                                backStack.clear()
                                backStack.add(tab.route)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            entryProvider = entryProvider {
                entry<Route.Start> { StartScreen(onNavigate = { backStack.add(it) }) }
                entry<Route.Grades> { GradesScreen() }
                entry<Route.Timetable> { TimetableScreen() }
                entry<Route.Exams> { ExamsScreen() }
                entry<Route.Homework> { HomeworkScreen() }
                entry<Route.More> { MoreScreen(onNavigate = { backStack.add(it) }) }
                entry<Route.Notes> { NotesScreen() }
                entry<Route.Announcements> { AnnouncementsScreen() }
                entry<Route.Messages> { MessagesScreen(onNavigate = { backStack.add(it) }) }
                entry<Route.MessageDetails> { MessageDetailsScreen(route = it) }
                entry<Route.Settings> { SettingsScreen() }
                entry<Route.Account> { AccountScreen(onLogout = onLogout, onNavigateToAddAccount = onNavigateToAddAccount) }
            }
        )
    }
}
