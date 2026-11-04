package com.w0qs1.ncmcreader.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.w0qs1.ncmcreader.data.NcmcCard
import com.w0qs1.ncmcreader.data.NcmcDatabaseHelper
import com.w0qs1.ncmcreader.data.NcmcOperatorDatabaseHelper
import com.w0qs1.ncmcreader.data.RawRecordItem
import com.w0qs1.ncmcreader.data.TicketHistoryItem
import com.w0qs1.ncmcreader.nfc.NcmcCardReader
import com.w0qs1.ncmcreader.nfc.roundTo2Decimals
import com.w0qs1.ncmcreader.utils.HapticFeedbackUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import org.json.JSONArray
import org.json.JSONObject

enum class Screen {
    MAIN,
    ALL_CARDS,
    CARD_DETAILS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val DEFAULT_STATUS_MESSAGE = "Tap an NCMC card on the back of your device"
    }

        fun getAppVersionName(): String {
            return try {
                val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
                pInfo.versionName ?: "unknown"
            } catch (_: Exception) {
            "unknown"
            }
        }

        fun getAboutText(): String {
            val version = getAppVersionName()
            return """NCMC Reader v$version

NCMC Reader is a native Android application designed to read, display, and manage transit card information and transaction history from National Common Mobility Cards (NCMC) via NFC.

Key Features:
• Scan card details (PAN, expiry date, effective date, ATC, and balance)
• View detailed entry, exit, and transaction history
• Decode operators and station names dynamically using local database
• Import and export card records to JSON
• Local offline SQLite storage

Disclaimer:
• This app is not affiliated to NPCI, RuPay, card issuing banks and transport operators
• This app does not write any data to the card
• This app cannot update balance on the card"""
    }

    private val dbHelper = NcmcDatabaseHelper(application)
    private val prefs = application.getSharedPreferences("ncmc_prefs", Context.MODE_PRIVATE)
    private val _isProcessing = MutableStateFlow(false)
    private var statusResetJob: Job? = null

    init {
        checkForAppUpdate()
    }

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _downloadingUpdate = MutableStateFlow(false)
    val downloadingUpdate: StateFlow<Boolean> = _downloadingUpdate.asStateFlow()

    private val _updateDownloadStatus = MutableStateFlow<String?>(null)
    val updateDownloadStatus: StateFlow<String?> = _updateDownloadStatus.asStateFlow()

    private var latestApkUrl: String? = null

    private fun parseVersion(versionStr: String): Pair<Int, Int> {
        val cleaned = versionStr.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return Pair(major, minor)
    }

    fun checkForAppUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/w0qs1/NCMC_Reader_App/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseString)
                    val tagName = json.optString("tag_name", "")
                    val assets = json.optJSONArray("assets")

                    var apkUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    if (tagName.isNotEmpty()) {
                        val (appMajor, appMinor) = parseVersion(getAppVersionName())
                        val (ghMajor, ghMinor) = parseVersion(tagName)
                        val isNewer = (ghMajor > appMajor) || (ghMajor == appMajor && ghMinor > appMinor)

                        if (isNewer && !apkUrl.isNullOrEmpty()) {
                            latestApkUrl = apkUrl
                            _updateAvailable.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error checking for app update", e)
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val urlStr = latestApkUrl ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _downloadingUpdate.value = true
            _updateDownloadStatus.value = "Downloading update..."
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val apkFile = File(context.cacheDir, "update.apk")
                    connection.inputStream.use { input ->
                        apkFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    _updateDownloadStatus.value = "Download complete. Opening installer..."
                    _downloadingUpdate.value = false

                    withContext(Dispatchers.Main) {
                        installApk(context, apkFile)
                    }
                } else {
                    _updateDownloadStatus.value = "Failed to download update (HTTP ${connection.responseCode})"
                    _downloadingUpdate.value = false
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Download update failed", e)
                _updateDownloadStatus.value = "Download failed: ${e.message}"
                _downloadingUpdate.value = false
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to start installer", e)
        }
    }

    private val _isNfcSupported = MutableStateFlow(true)
    val isNfcSupported: StateFlow<Boolean> = _isNfcSupported.asStateFlow()

    private val _isNfcEnabled = MutableStateFlow(false)
    val isNfcEnabled: StateFlow<Boolean> = _isNfcEnabled.asStateFlow()

    private val _isMaskPanEnabled = MutableStateFlow(prefs.getBoolean("mask_pan", false))
    val isMaskPanEnabled: StateFlow<Boolean> = _isMaskPanEnabled.asStateFlow()

    fun setMaskPanEnabled(enabled: Boolean) {
        _isMaskPanEnabled.value = enabled
        prefs.edit { putBoolean("mask_pan", enabled) }
    }

    private val _isMaskMetadataEnabled = MutableStateFlow(prefs.getBoolean("mask_metadata", false))
    val isMaskMetadataEnabled: StateFlow<Boolean> = _isMaskMetadataEnabled.asStateFlow()

    fun setMaskMetadataEnabled(enabled: Boolean) {
        _isMaskMetadataEnabled.value = enabled
        prefs.edit { putBoolean("mask_metadata", enabled) }
    }

    private val _isMaskTimeEnabled = MutableStateFlow(prefs.getBoolean("mask_time", false))
    val isMaskTimeEnabled: StateFlow<Boolean> = _isMaskTimeEnabled.asStateFlow()

    fun setMaskTimeEnabled(enabled: Boolean) {
        _isMaskTimeEnabled.value = enabled
        prefs.edit { putBoolean("mask_time", enabled) }
    }

    private val _isMaskDateEnabled = MutableStateFlow(prefs.getBoolean("mask_date", false))
    val isMaskDateEnabled: StateFlow<Boolean> = _isMaskDateEnabled.asStateFlow()

    fun setMaskDateEnabled(enabled: Boolean) {
        _isMaskDateEnabled.value = enabled
        prefs.edit { putBoolean("mask_date", enabled) }
    }

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    private val _showDisclaimerDialog = MutableStateFlow(!prefs.getBoolean("disclaimer_accepted", false))
    val showDisclaimerDialog: StateFlow<Boolean> = _showDisclaimerDialog.asStateFlow()

    fun acceptDisclaimer() {
        _showDisclaimerDialog.value = false
        prefs.edit { putBoolean("disclaimer_accepted", true) }
        if (!prefs.getBoolean("tutorial_completed", false)) {
            startTutorial()
        }
    }

    fun declineDisclaimer() {
        prefs.edit {
            putBoolean("disclaimer_accepted", false)
            remove("tutorial_completed")
        }
    }

    fun showDisclaimerAgain() {
        prefs.edit {
            putBoolean("disclaimer_accepted", false)
            remove("tutorial_completed")
        }
        _showDisclaimerDialog.value = true
    }

    private val _tutorialStep = MutableStateFlow(0)
    val tutorialStep: StateFlow<Int> = _tutorialStep.asStateFlow()

    fun startTutorial() {
        viewModelScope.launch(Dispatchers.IO) {
            val sampleCard = NcmcCard(
                pan = "8100001234567899",
                expiryDate = "01/31",
                effectiveDate = "01/26",
                ncmcVersion = "3.1",
                cardLanguage = "English",
                atc = 42,
                currentBalance = 245.50,
                cardTitle = "Sample NCMC Card",
                ticketHistory = listOf(
                    TicketHistoryItem(
                        cardPan = "8100001234567899",
                        ticketType = "EXIT",
                        stationOrLocation = "Central",
                        timestamp = "2026-05-25 14:33",
                        amount = 30.00,
                        balance = 245.50,
                        acquirerId = 11,
                        operatorId = 6013,
                        terminalId = 57664
                    )
                )
            )
            dbHelper.saveOrUpdateCard(sampleCard)
            loadSavedCards()

            withContext(Dispatchers.Main) {
                _selectedCard.value = sampleCard
                _currentScreen.value = Screen.MAIN
                _tutorialStep.value = 1
                HapticFeedbackUtils.performScanHaptic(getApplication())
                setStatusMessage("Simulating sample card scan...", isError = false)
            }

            delay(1500.milliseconds)

            withContext(Dispatchers.Main) {
                HapticFeedbackUtils.performSuccessHaptic(getApplication())
                setStatusMessage(DEFAULT_STATUS_MESSAGE, isError = false)
                _currentScreen.value = Screen.CARD_DETAILS
                _tutorialStep.value = 2
            }
        }
    }

    fun advanceTutorialStep() {
        val current = _tutorialStep.value
        if (current in 2..4) {
            _tutorialStep.value = current + 1
        }
    }

    fun setTutorialStep(step: Int) {
        _tutorialStep.value = step
    }

    fun finishTutorial() {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteCard("8100001234567899")
            loadSavedCards()
            prefs.edit { putBoolean("tutorial_completed", true) }
            withContext(Dispatchers.Main) {
                _tutorialStep.value = 0
                _selectedCard.value = null
                _currentScreen.value = Screen.MAIN
            }
        }
    }

    @Suppress("unused")
    private val _lastScannedCard = MutableStateFlow<NcmcCard?>(null)
    @Suppress("unused")
    val lastScannedCard: StateFlow<NcmcCard?> = _lastScannedCard.asStateFlow()

    private val _allSavedCards = MutableStateFlow<List<NcmcCard>>(emptyList())
    val allSavedCards: StateFlow<List<NcmcCard>> = _allSavedCards.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow(DEFAULT_STATUS_MESSAGE)
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _currentScreen = MutableStateFlow(Screen.MAIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedCard = MutableStateFlow<NcmcCard?>(null)
    val selectedCard: StateFlow<NcmcCard?> = _selectedCard.asStateFlow()

    init {
        checkNfcStatus()
        loadSavedCards()
        NcmcOperatorDatabaseHelper(application).checkForRemoteUpdate()
        if (prefs.getBoolean("disclaimer_accepted", false) && !prefs.getBoolean("tutorial_completed", false)) {
            startTutorial()
        }
    }

    fun setStatusMessage(msg: String, isError: Boolean = false) {
        statusResetJob?.cancel()
        _statusMessage.value = msg

        if (isError) {
            statusResetJob = viewModelScope.launch(Dispatchers.Main) {
                delay(3.seconds)
                _statusMessage.value = DEFAULT_STATUS_MESSAGE
            }
        }
    }

    fun checkNfcStatus() {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(getApplication())
        if (nfcAdapter == null) {
            _isNfcSupported.value = false
            _isNfcEnabled.value = false
            setStatusMessage("NFC is not supported on this device", isError = false)
        } else {
            _isNfcSupported.value = true
            val enabled = nfcAdapter.isEnabled
            _isNfcEnabled.value = enabled
            if (!enabled) {
                setStatusMessage("NFC is disabled. Please enable NFC to scan cards.", isError = false)
            } else {
                setStatusMessage(DEFAULT_STATUS_MESSAGE, isError = false)
            }
        }
    }

    fun loadSavedCards() {
        viewModelScope.launch(Dispatchers.IO) {
            val cards = dbHelper.getAllCards()
            _allSavedCards.value = cards
        }
    }

    fun processTag(isoDep: IsoDep) {
        // Strictly gate NFC processing to Screen.MAIN
        if (_currentScreen.value != Screen.MAIN) {
            return
        }
        if (_isProcessing.value) return
        _isProcessing.value = true

        // 1st Haptic Feedback: Card Tag Scanned (Any card)
        HapticFeedbackUtils.performScanHaptic(getApplication())

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            setStatusMessage("Reading NCMC card...", isError = false)
            try {
                val reader = NcmcCardReader(isoDep)
                val card = reader.readCard()

                // Success Haptic: Double positive tone for successful NCMC card read
                HapticFeedbackUtils.performSuccessHaptic(getApplication())

                // Save or update in SQLite Database
                dbHelper.saveOrUpdateCard(card)

                // Re-query updated card from DB (including merged ticket history)
                val updatedCard = dbHelper.getCardByPan(card.pan) ?: card

                _lastScannedCard.value = updatedCard
                _selectedCard.value = updatedCard
                setStatusMessage(DEFAULT_STATUS_MESSAGE, isError = false)
                loadSavedCards()

                // Open card details page directly from scanned cards list
                _currentScreen.value = Screen.CARD_DETAILS
            } catch (e: Exception) {
                // Negative Haptic: Single long tone for wrong/unsupported card or read error
                HapticFeedbackUtils.performErrorHaptic(getApplication())

                Log.e("MAIN_VIEW_MODEL", "Error processing tag: ${e.message}", e)
                val errorMsg = "Error reading card: ${e.localizedMessage ?: "Tag lost or card removed. Tap again."}"
                setStatusMessage(errorMsg, isError = true)
            } finally {
                _isLoading.value = false
                _isProcessing.value = false
            }
        }
    }

    fun exportCardsToJson(context: Context, uri: Uri, selectedPans: List<String>? = null, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (success, resultMessage) = try {
                val allCards = dbHelper.getAllCards()
                val cards = if (selectedPans != null) {
                    allCards.filter { it.pan in selectedPans }
                } else {
                    allCards
                }

                val opDbHelper = NcmcOperatorDatabaseHelper(context)

                val jsonArray = JSONArray()
                for (card in cards) {
                    val cardObj = JSONObject().apply {
                        put("cardTitle", card.cardTitle)
                        put("pan", card.pan)
                        put("expiryDate", card.expiryDate)
                        put("effectiveDate", card.effectiveDate)
                        put("ncmcVersion", card.ncmcVersion)
                        put("cardLanguage", card.cardLanguage)
                        put("atc", card.atc)
                        put("currentBalance", card.currentBalance.roundTo2Decimals())
                        put("lastScannedAt", card.lastScannedAt)

                        val historyArray = JSONArray()
                        for (item in card.ticketHistory) {
                            val resolved = if ((item.acquirerId > 0) || (item.operatorId > 0) || (item.terminalId > 0)) {
                                opDbHelper.resolveStation(item.acquirerId, item.operatorId, item.terminalId)
                            } else null

                            val itemObj = JSONObject().apply {
                                put("ticketType", item.ticketType)
                                put("stationOrLocation", item.stationOrLocation)
                                put("decodedOperatorName", resolved?.operatorName ?: "")
                                put("decodedStationName", resolved?.stationName ?: "")
                                put("timestamp", item.timestamp)
                                put("amount", item.amount.roundTo2Decimals())
                                put("balance", item.balance.roundTo2Decimals())
                                put("acquirerId", item.acquirerId)
                                put("operatorId", item.operatorId)
                                put("terminalId", item.terminalId)
                                put("rawHex", item.rawHex)
                            }
                            historyArray.put(itemObj)
                        }
                        put("ticketHistory", historyArray)

                        val fileSystemArray = JSONArray()
                        val groupedBySfi = card.rawRecords.groupBy { it.sfi }
                        for ((sfi, records) in groupedBySfi.entries.sortedBy { it.key }) {
                            val sfiObj = JSONObject().apply {
                                put("sfi", sfi)
                                val recordsArray = JSONArray()
                                for (rec in records.sortedBy { it.recordNum }) {
                                    val recObj = JSONObject().apply {
                                        put("record", rec.recordNum)
                                        put("rawHex", rec.rawHex)
                                    }
                                    recordsArray.put(recObj)
                                }
                                put("records", recordsArray)
                            }
                            fileSystemArray.put(sfiObj)
                        }
                        put("fileSystem", fileSystemArray)
                    }
                    jsonArray.put(cardObj)
                }

                val jsonString = jsonArray.toString(2)
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    outputStream.close()
                    true to "Successfully exported ${cards.size} card(s) to JSON."
                } else {
                    false to "Failed to open export file."
                }
            } catch (e: Exception) {
                false to "Export failed: ${e.message}"
            }

            withContext(Dispatchers.Main) {
                onResult(success, resultMessage)
            }
        }
    }

    fun importCardsFromJson(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (success, resultMessage) = try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    false to "Failed to open import file."
                } else {
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    inputStream.close()

                    val jsonArray = JSONArray(jsonString)
                    var importedCount = 0

                    for (i in 0 until jsonArray.length()) {
                        val cardObj = jsonArray.getJSONObject(i)
                        val pan = cardObj.optString("pan", "")
                        if (pan.isBlank()) continue

                        val cardTitle = cardObj.optString("cardTitle", "RuPay NCMC Card")
                        val expiryDate = cardObj.optString("expiryDate", "N/A")
                        val effectiveDate = cardObj.optString("effectiveDate", "N/A")
                        val ncmcVersion = cardObj.optString("ncmcVersion", "3.1")
                        val cardLanguage = cardObj.optString("cardLanguage", "English")
                        val atc = cardObj.optInt("atc", 0)
                        val currentBalance = cardObj.optDouble("currentBalance", 0.0).roundTo2Decimals()
                        val lastScannedAt = cardObj.optLong("lastScannedAt", System.currentTimeMillis())

                        val historyList = mutableListOf<TicketHistoryItem>()
                        val historyArray = cardObj.optJSONArray("ticketHistory")
                        if (historyArray != null) {
                            for (j in 0 until historyArray.length()) {
                                val hObj = historyArray.getJSONObject(j)
                                historyList.add(
                                    TicketHistoryItem(
                                        cardPan = pan,
                                        ticketType = hObj.optString("ticketType", "TRANSACTION"),
                                        stationOrLocation = hObj.optString("stationOrLocation", "Metro Station"),
                                        timestamp = hObj.optString("timestamp", ""),
                                        amount = hObj.optDouble("amount", 0.0).roundTo2Decimals(),
                                        balance = hObj.optDouble("balance", 0.0).roundTo2Decimals(),
                                        rawHex = hObj.optString("rawHex", ""),
                                        acquirerId = hObj.optInt("acquirerId", 0),
                                        operatorId = hObj.optInt("operatorId", 0),
                                        terminalId = hObj.optInt("terminalId", 0)
                                    )
                                )
                            }
                        }

                        val rawRecordsList = mutableListOf<RawRecordItem>()
                        val fileSystemArray = cardObj.optJSONArray("fileSystem")
                        if (fileSystemArray != null) {
                            for (j in 0 until fileSystemArray.length()) {
                                val sfiObj = fileSystemArray.getJSONObject(j)
                                val sfi = sfiObj.optInt("sfi", 0)
                                val recordsArray = sfiObj.optJSONArray("records")
                                if (recordsArray != null && sfi > 0) {
                                    for (k in 0 until recordsArray.length()) {
                                        val recObj = recordsArray.getJSONObject(k)
                                        val recordNum = recObj.optInt("record", 0)
                                        val rawHex = recObj.optString("rawHex", "")
                                        if (recordNum > 0 && rawHex.isNotBlank()) {
                                            rawRecordsList.add(
                                                RawRecordItem(
                                                    cardPan = pan,
                                                    sfi = sfi,
                                                    recordNum = recordNum,
                                                    rawHex = rawHex
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val card = NcmcCard(
                            pan = pan,
                            expiryDate = expiryDate,
                            effectiveDate = effectiveDate,
                            ncmcVersion = ncmcVersion,
                            cardLanguage = cardLanguage,
                            atc = atc,
                            currentBalance = currentBalance,
                            lastScannedAt = lastScannedAt,
                            ticketHistory = historyList,
                            rawRecords = rawRecordsList,
                            cardTitle = cardTitle
                        )

                        dbHelper.saveOrUpdateCard(card)
                        importedCount++
                    }

                    loadSavedCards()
                    true to "Successfully imported $importedCount card(s)."
                }
            } catch (e: Exception) {
                false to "Import failed: ${e.message}"
            }

            withContext(Dispatchers.Main) {
                onResult(success, resultMessage)
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun selectCardForDetails(card: NcmcCard) {
        _selectedCard.value = card
        _currentScreen.value = Screen.CARD_DETAILS
    }

    @Suppress("unused")
    fun deleteCard(pan: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteCard(pan)
            if (_lastScannedCard.value?.pan == pan) {
                _lastScannedCard.value = null
            }
            if (_selectedCard.value?.pan == pan) {
                _selectedCard.value = null
                _currentScreen.value = Screen.ALL_CARDS
            }
            loadSavedCards()
        }
    }

    fun deleteSelectedCards(pans: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (pan in pans) {
                dbHelper.deleteCard(pan)
                if (_lastScannedCard.value?.pan == pan) {
                    _lastScannedCard.value = null
                }
                if (_selectedCard.value?.pan == pan) {
                    _selectedCard.value = null
                }
            }
            loadSavedCards()
        }
    }

    fun clearAllCards() {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.clearAll()
            _lastScannedCard.value = null
            _selectedCard.value = null
            loadSavedCards()
        }
    }
}
