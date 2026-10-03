package com.w0qs1.ncmcreader.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.w0qs1.ncmcreader.data.NcmcCard
import com.w0qs1.ncmcreader.viewmodel.MainViewModel
import com.w0qs1.ncmcreader.viewmodel.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun generateExportFilename(): String {
    val sdf = SimpleDateFormat("yyMMddHHmmss", Locale.US)
    return "ncmc_reader_${sdf.format(Date())}.json"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannedCardsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedCards by viewModel.allSavedCards.collectAsState()

    val selectedPans = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedPans.isNotEmpty()

    var showKebabMenu by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val pansToExport = if (isSelectionMode) selectedPans.toList() else null
            viewModel.exportCardsToJson(context, uri, pansToExport) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                if (isSelectionMode) {
                    selectedPans.clear()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCardsFromJson(context, uri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // TopAppBar during Selection Mode
                TopAppBar(
                    title = { Text("${selectedPans.size} Selected") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    navigationIcon = {
                        TextButton(onClick = { selectedPans.clear() }) {
                            Text("✕ Cancel")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                if (selectedPans.size == savedCards.size) {
                                    selectedPans.clear()
                                } else {
                                    selectedPans.clear()
                                    selectedPans.addAll(savedCards.map { it.pan })
                                }
                            }
                        ) {
                            Text(if (selectedPans.size == savedCards.size) "Deselect All" else "Select All")
                        }

                        TextButton(onClick = { exportLauncher.launch(generateExportFilename()) }) {
                            Text("Export")
                        }

                        TextButton(onClick = { showDeleteSelectedDialog = true }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            } else {
                // Normal TopAppBar with Kebab Menu
                TopAppBar(
                    title = { Text("All Scanned Cards (${savedCards.size})") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        TextButton(onClick = { viewModel.navigateTo(Screen.MAIN) }) {
                            Text("< Back")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showKebabMenu = true }) {
                                Text("⋮", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }

                            DropdownMenu(
                                expanded = showKebabMenu,
                                onDismissRequest = { showKebabMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import Cards") },
                                    onClick = {
                                        showKebabMenu = false
                                        importLauncher.launch("application/json")
                                    }
                                )
                                if (savedCards.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Export All Cards") },
                                        onClick = {
                                            showKebabMenu = false
                                            exportLauncher.launch(generateExportFilename())
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear All Cards", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showKebabMenu = false
                                            showClearAllDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (savedCards.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No cards scanned yet. Tap an NCMC card on the main screen.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedCards) { card ->
                        val isSelected = selectedPans.contains(card.pan)
                        ScannedCardItem(
                            card = card,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) {
                                        selectedPans.remove(card.pan)
                                    } else {
                                        selectedPans.add(card.pan)
                                    }
                                } else {
                                    viewModel.selectCardForDetails(card)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedPans.add(card.pan)
                                } else {
                                    if (isSelected) {
                                        selectedPans.remove(card.pan)
                                    } else {
                                        selectedPans.add(card.pan)
                                    }
                                }
                            },
                            onDelete = { viewModel.deleteCard(card.pan) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete Selected Cards") },
            text = { Text("Are you sure you want to delete ${selectedPans.size} selected card(s)?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedCards(selectedPans.toList())
                        selectedPans.clear()
                        showDeleteSelectedDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Cards") },
            text = { Text("Are you sure you want to remove all saved cards and transaction history from SQLite database?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllCards()
                        selectedPans.clear()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScannedCardItem(
    card: NcmcCard,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = dateFormat.format(Date(card.lastScannedAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.cardTitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.pan,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "₹%.2f", card.currentBalance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (card.ncmcVersion == "NCMC Not Enabled") "NCMC Not Enabled | Scanned: $formattedDate" else "History: ${card.ticketHistory.size} entries | Scanned: $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
