package com.w0qs1.ncmcreader.ui

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.w0qs1.ncmcreader.R
import com.w0qs1.ncmcreader.viewmodel.MainViewModel
import com.w0qs1.ncmcreader.viewmodel.Screen

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isNfcSupported by viewModel.isNfcSupported.collectAsState()
    val isNfcEnabled by viewModel.isNfcEnabled.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isMaskPanEnabled by viewModel.isMaskPanEnabled.collectAsState()
    val isMaskTimeEnabled by viewModel.isMaskTimeEnabled.collectAsState()
    val isMaskDateEnabled by viewModel.isMaskDateEnabled.collectAsState()
    val isMaskMetadataEnabled by viewModel.isMaskMetadataEnabled.collectAsState()

    var showKebabMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val issuesUrl = stringResource(R.string.github_issues_url)

    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main content Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // NFC Status Check Banner
                if (!isNfcSupported) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = "NFC is not supported on this device.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                } else if (!isNfcEnabled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "NFC is currently disabled. Please turn on NFC to scan NCMC cards.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.checkNfcStatus() }
                                ) {
                                    Text("Check NFC Status")
                                }
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                    }
                                ) {
                                    Text("Settings")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Tap Card Guidance Card (Vertically Centered with Image between text)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Line 1: Header
                        Text(
                            text = "Tap NCMC Card",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Imported Image between the 2 lines of text
                        Image(
                            painter = painterResource(id = R.drawable.ic_nfc_tap),
                            contentDescription = "Tap Card Graphic",
                            modifier = Modifier.size(120.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Line 2: Status / Instruction Message
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        if (isLoading) {
                            Spacer(modifier = Modifier.height(20.dp))
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Navigation Button to View All Saved Cards
                Button(
                    onClick = { viewModel.navigateTo(Screen.ALL_CARDS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "View All Scanned Cards",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                val enabledCount = listOf(isMaskPanEnabled, isMaskTimeEnabled, isMaskDateEnabled, isMaskMetadataEnabled).count { it }
                if (enabledCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$enabledCount masking flags enabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Banner-less Kebab Menu on Top Right (Placed AFTER Column so it sits ON TOP in Z-order)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            ) {
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
                        text = { Text("About") },
                        onClick = {
                            showKebabMenu = false
                            showAboutDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About NCMC Reader") },
            text = {
                Text(
                    text = viewModel.getAboutText(),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
