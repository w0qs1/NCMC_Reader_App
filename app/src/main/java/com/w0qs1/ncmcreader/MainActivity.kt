package com.w0qs1.ncmcreader

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.w0qs1.ncmcreader.ui.CardDetailsScreen
import com.w0qs1.ncmcreader.ui.MainScreen
import com.w0qs1.ncmcreader.ui.ScannedCardsScreen
import com.w0qs1.ncmcreader.ui.theme.NCMCReaderTheme
import com.w0qs1.ncmcreader.viewmodel.MainViewModel
import com.w0qs1.ncmcreader.viewmodel.Screen

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private val viewModel: MainViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED == intent?.action) {
                viewModel.checkNfcStatus()
                updateNfcPolling(viewModel.currentScreen.value)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            NCMCReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val isNfcEnabled by viewModel.isNfcEnabled.collectAsState()

                    LaunchedEffect(isNfcEnabled, currentScreen) {
                        updateNfcPolling(currentScreen)
                    }

                    DisposableEffect(currentScreen) {
                        updateNfcPolling(currentScreen)
                        onDispose {
                            nfcAdapter?.disableReaderMode(this@MainActivity)
                        }
                    }

                    AppNavigation(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNfcStatus()
        viewModel.loadSavedCards()
        updateNfcPolling(viewModel.currentScreen.value)

        try {
            registerReceiver(
                nfcStateReceiver,
                IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            )
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(nfcStateReceiver)
        } catch (_: Exception) {}
        nfcAdapter?.disableReaderMode(this)
    }

    private fun updateNfcPolling(screen: Screen) {
        nfcAdapter?.let { adapter ->
            if (screen == Screen.MAIN && adapter.isEnabled) {
                val flags = NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
                adapter.enableReaderMode(this, this, flags, null)
            } else {
                adapter.disableReaderMode(this)
            }
        }
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        // Strictly gate card scanning to Screen.MAIN
        if (viewModel.currentScreen.value != Screen.MAIN) {
            return
        }

        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            viewModel.processTag(isoDep)
        }
    }
}

private val disclaimerText = """
    This app can only read balance and transit history. It cannot perform balance update or balance modification.

    The contributors of this app are not responsible for any warranty or damages to cards since this is based on a reverse-engineered specification.

    Built using LLM Tools. Use at your own risk! No warranty provided.

    This project and its contributors are not affiliated with, endorsed by, sponsored by, or officially associated with:
    • RuPay
    • National Payments Corporation of India (NPCI)
    • National Common Mobility Card (NCMC) program
    • NCMC-issuing banks
    • Metro rail corporations and other transit operators
    • Automatic Fare Collection (AFC) system providers
""".trimIndent()

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsState()
    val showDisclaimerDialog by viewModel.showDisclaimerDialog.collectAsState()
    val isMaskPanEnabled by viewModel.isMaskPanEnabled.collectAsState()
    val isMaskTimeEnabled by viewModel.isMaskTimeEnabled.collectAsState()
    val isMaskDateEnabled by viewModel.isMaskDateEnabled.collectAsState()
    val isMaskMetadataEnabled by viewModel.isMaskMetadataEnabled.collectAsState()
    val context = LocalContext.current

    BackHandler(enabled = currentScreen != Screen.MAIN) {
        when (currentScreen) {
            Screen.CARD_DETAILS -> viewModel.navigateTo(Screen.ALL_CARDS)
            Screen.ALL_CARDS -> viewModel.navigateTo(Screen.MAIN)
            Screen.MAIN -> {}
        }
    }

    when (currentScreen) {
        Screen.MAIN -> MainScreen(viewModel = viewModel)
        Screen.ALL_CARDS -> ScannedCardsScreen(viewModel = viewModel)
        Screen.CARD_DETAILS -> CardDetailsScreen(viewModel = viewModel)
    }

    val tutorialStep by viewModel.tutorialStep.collectAsState()

    if (tutorialStep > 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val title = when (tutorialStep) {
                        1 -> "Tutorial: Scanning Card"
                        2 -> "Tutorial: Main Card Info"
                        3 -> "Tutorial: Expiry & Validity"
                        4 -> "Tutorial: Transit History"
                        5 -> "Tutorial: Test Reporting"
                        6 -> "Tutorial Finished"
                        else -> "Tutorial"
                    }
                    val description = when (tutorialStep) {
                        1 -> "Simulating NFC card scan..."
                        2 -> "Highlighted: This section shows your card balance, PAN number, and card title/type."
                        3 -> "Highlighted: This section shows your card's expiry date and effective date."
                        4 -> "Highlighted: This shows your transit trip entries, stations, fare deductions, and route lines."
                        5 -> "Long-press on the trip entry above to test the report action."
                        6 -> "You successfully tested the report action! Tap Complete to finish the tutorial."
                        else -> ""
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.finishTutorial() }) {
                            Text("Skip Tutorial", color = MaterialTheme.colorScheme.error)
                        }
                        if (tutorialStep in 2..4) {
                            Button(onClick = { viewModel.advanceTutorialStep() }) {
                                Text("Next")
                            }
                        } else if (tutorialStep == 6) {
                            Button(onClick = { viewModel.finishTutorial() }) {
                                Text("Complete Tutorial")
                            }
                        } else if (tutorialStep == 1) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Disclaimer & Terms of Use") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = disclaimerText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.acceptDisclaimer() }) {
                    Text("Accept")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        (context as? Activity)?.finishAffinity()
                    }
                ) {
                    Text("Decline", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowSettingsDialog(false) },
            title = { Text("Settings") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Mask Card PAN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mask Card PAN",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Show only first 6 and last 4 digits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMaskPanEnabled,
                            onCheckedChange = { viewModel.setMaskPanEnabled(it) }
                        )
                    }

                    // Mask Card Metadata (ATC, Expiry & Effective Dates)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mask Card Metadata",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Mask ATC, expiry & effective dates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMaskMetadataEnabled,
                            onCheckedChange = { viewModel.setMaskMetadataEnabled(it) }
                        )
                    }

                    HorizontalDivider()

                    // 2. Mask Transaction Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mask Transaction Time",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Show time as xx:xx",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMaskTimeEnabled,
                            onCheckedChange = { viewModel.setMaskTimeEnabled(it) }
                        )
                    }

                    // 3. Mask Transaction Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mask Transaction Date",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Show date as XXXX-XX-XX",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMaskDateEnabled,
                            onCheckedChange = { viewModel.setMaskDateEnabled(it) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowSettingsDialog(false) }) {
                    Text("Close")
                }
            }
        )
    }
}
