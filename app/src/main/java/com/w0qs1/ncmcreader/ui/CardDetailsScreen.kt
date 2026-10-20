package com.w0qs1.ncmcreader.ui

import android.content.ClipData
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.w0qs1.ncmcreader.R
import com.w0qs1.ncmcreader.viewmodel.MainViewModel
import com.w0qs1.ncmcreader.viewmodel.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedCard by viewModel.selectedCard.collectAsState()
    val isMaskPanEnabled by viewModel.isMaskPanEnabled.collectAsState()
    val isMaskTimeEnabled by viewModel.isMaskTimeEnabled.collectAsState()
    val isMaskDateEnabled by viewModel.isMaskDateEnabled.collectAsState()
    val isMaskMetadataEnabled by viewModel.isMaskMetadataEnabled.collectAsState()
    val context = LocalContext.current
    var showKebabMenu by remember { mutableStateOf(false) }
    var showRawLogsDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val issuesUrl = stringResource(R.string.github_issues_url)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card Details") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    TextButton(onClick = { viewModel.navigateTo(Screen.ALL_CARDS) }) {
                        Text("< Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showKebabMenu = true }) {
                            Text(
                                text = "⋮",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = showKebabMenu,
                            onDismissRequest = { showKebabMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showKebabMenu = false
                                    viewModel.setShowSettingsDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Report/Add New Station/Operator") },
                                onClick = {
                                    showKebabMenu = false
                                    val intent = Intent(Intent.ACTION_VIEW, issuesUrl.toUri())
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("View Raw Card Logs") },
                                onClick = {
                                    showKebabMenu = false
                                    showRawLogsDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            selectedCard?.let { card ->
                CardDetailsView(
                    card = card,
                    isMaskPanEnabled = isMaskPanEnabled,
                    isMaskTimeEnabled = isMaskTimeEnabled,
                    isMaskDateEnabled = isMaskDateEnabled,
                    isMaskMetadataEnabled = isMaskMetadataEnabled
                )
            } ?: run {
                Text("No card selected")
            }
        }
    }

    if (showRawLogsDialog) {
        val logsText = selectedCard?.rawScanLogs?.joinToString("\n") ?: "No raw scan logs available for this card."
        AlertDialog(
            onDismissRequest = { showRawLogsDialog = false },
            title = { Text("Raw Scanned Card Logs") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = logsText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val clipData = ClipData.newPlainText("Raw Card Logs", logsText)
                                clipboard.setClipEntry(ClipEntry(clipData))
                            }
                        }
                    ) {
                        Text("Copy")
                    }
                    Button(onClick = { showRawLogsDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}
