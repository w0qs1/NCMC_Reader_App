package com.w0qs1.ncmcreader

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

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
}
