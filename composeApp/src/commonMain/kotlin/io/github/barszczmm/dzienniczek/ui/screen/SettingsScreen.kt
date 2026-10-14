package io.github.barszczmm.dzienniczek.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.barszczmm.dzienniczek.notifications.rememberNotificationPermissionRequest
import io.github.barszczmm.dzienniczek.notifications.BackgroundSchedule
import io.github.barszczmm.dzienniczek.notifications.CheckNowState
import io.github.barszczmm.dzienniczek.settings.BackgroundCheck
import io.github.barszczmm.dzienniczek.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val polling by viewModel.messagePolling.collectAsStateWithLifecycle()
    val checks by viewModel.backgroundChecks.collectAsStateWithLifecycle()
    val checkNowState by viewModel.checkNowState.collectAsStateWithLifecycle()
    val checkHours by viewModel.checkHours.collectAsStateWithLifecycle()
    // The result of a check is shown only after the button was pressed on this screen.
    var checkRequested by remember { mutableStateOf(false) }
    val checking = checkNowState is CheckNowState.Running || checkNowState is CheckNowState.Waiting
    val diagnosticsRunning by viewModel.diagnosticsRunning.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Sprawdzanie w tle",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Sprawdzaj nowości w tle",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Mniej więcej co godzinę (losowo ${BackgroundSchedule.MIN_DELAY_MINUTES}–" +
                                "${BackgroundSchedule.MAX_DELAY_MINUTES} min), w wybranych poniżej godzinach, aplikacja sprawdza " +
                                "wszystkich włączonych uczniów (Librus i eduVulcan) i pokazuje powiadomienie " +
                                "o nowych wpisach z zaznaczonych " +
                                "poniżej kategorii. Wiadomości – z pełną treścią.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = polling,
                        onCheckedChange = { enabled ->
                            if (enabled) requestNotificationPermission()
                            viewModel.setMessagePolling(enabled)
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Godziny sprawdzania",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("od", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    HourPicker(hour = checkHours.first, hours = 0..23) {
                        viewModel.setCheckHours(it, checkHours.last)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("do", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    HourPicker(hour = checkHours.last, hours = (checkHours.first + 1)..24) {
                        viewModel.setCheckHours(checkHours.first, it)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Co sprawdzać",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BackgroundCheck.entries.forEach { check ->
                    val checked = check in checks
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { viewModel.setBackgroundCheck(check, it) }
                            )
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(check.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    text = "Przy pierwszym sprawdzeniu nowej kategorii aplikacja tylko zapamiętuje " +
                        "obecne wpisy – powiadomienia pojawią się dla kolejnych.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        requestNotificationPermission()
                        checkRequested = true
                        viewModel.checkNow()
                    },
                    enabled = checks.isNotEmpty() && !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (checkNowState) {
                            CheckNowState.Waiting -> "Czekam na połączenie…"
                            CheckNowState.Running -> "Sprawdzanie…"
                            else -> "Sprawdź teraz"
                        }
                    )
                }
                val done = checkNowState as? CheckNowState.Done
                if (checkRequested && done != null) {
                    Text(
                        text = when (done.found) {
                            0 -> "Brak nowości."
                            1 -> "Znaleziono 1 nowość – zobacz powiadomienia."
                            else -> "Znaleziono nowości: ${done.found} – zobacz powiadomienia."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        if (viewModel.isLibrusActive) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Diagnostyka",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pobiera surowe dane ocen z Librusa dla aktywnego ucznia i otwiera " +
                            "udostępnianie (np. do skopiowania). Dane zawierają oceny ucznia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.exportLibrusDiagnostics() },
                        enabled = !diagnosticsRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (diagnosticsRunning) "Pobieranie…" else "Eksportuj dane diagnostyczne")
                    }
                }
            }
        }
    }
}

/** Full hour as a button; tapping it lists [hours] to choose from (24 = midnight). */
@Composable
private fun HourPicker(hour: Int, hours: IntRange, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$hour:00")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            hours.forEach { h ->
                DropdownMenuItem(
                    text = { Text("$h:00") },
                    onClick = {
                        expanded = false
                        onSelect(h)
                    }
                )
            }
        }
    }
}
