package io.github.barszczmm.dzienniczek.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.barszczmm.dzienniczek.util.rememberHtmlText
import io.github.barszczmm.dzienniczek.navigation.Route
import io.github.barszczmm.dzienniczek.viewmodel.MessageDetailsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MessageDetailsScreen(
    route: Route.MessageDetails,
    viewModel: MessageDetailsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reply by viewModel.reply.collectAsStateWithLifecycle()
    var composing by rememberSaveable(route.id) { mutableStateOf(false) }
    var replyText by rememberSaveable(route.id) { mutableStateOf("") }
    var confirmSend by remember { mutableStateOf(false) }

    LaunchedEffect(reply.sent) {
        if (reply.sent) {
            composing = false
            replyText = ""
        }
    }

    LaunchedEffect(route) {
        viewModel.loadMessage(
            id = route.id,
            isHebe = route.isHebe,
            hebeContent = route.hebeContent
        )
    }

    if (confirmSend) {
        AlertDialog(
            onDismissRequest = { confirmSend = false },
            title = { Text("Wysłać odpowiedź?") },
            text = {
                Text("Odpowiedź trafi do: ${state.sender ?: "nadawcy wiadomości"}. Nie da się jej cofnąć.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSend = false
                    viewModel.sendReply(route.id, route.isHebe, replyText)
                }) { Text("Wyślij") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSend = false }) { Text("Anuluj") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.error != null -> {
                ErrorScreen(state.error!!, onRetry = {
                    viewModel.loadMessage(
                        id = route.id,
                        isHebe = route.isHebe,
                        hebeContent = route.hebeContent
                    )
                })
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (state.subject != null) {
                        Text(
                            text = state.subject!!,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (state.sender != null) {
                        Text(
                            text = "Od: ${state.sender}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.date != null) {
                        Text(
                            text = "Data: ${state.date}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.subject != null || state.sender != null || state.date != null) {
                        Spacer(Modifier.height(16.dp))
                    }
                    
                    Text(
                        text = rememberHtmlText(state.content ?: ""),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (viewModel.canReply(route.isHebe)) {
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        if (reply.sent) {
                            Text(
                                text = "Odpowiedź została wysłana.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        if (!composing) {
                            Button(
                                onClick = { viewModel.resetReply(); composing = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Odpowiedz")
                            }
                        } else {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                label = { Text("Twoja odpowiedź") },
                                minLines = 5,
                                enabled = !reply.isSending,
                                modifier = Modifier.fillMaxWidth()
                            )
                            reply.error?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = { composing = false },
                                    enabled = !reply.isSending
                                ) { Text("Anuluj") }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { confirmSend = true },
                                    enabled = replyText.isNotBlank() && !reply.isSending
                                ) {
                                    if (reply.isSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text("Wyślij")
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
