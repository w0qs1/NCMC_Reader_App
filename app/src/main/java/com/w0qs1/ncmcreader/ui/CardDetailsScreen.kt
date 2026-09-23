package com.w0qs1.ncmcreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.w0qs1.ncmcreader.viewmodel.MainViewModel
import com.w0qs1.ncmcreader.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedCard by viewModel.selectedCard.collectAsState()

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
                CardDetailsView(card = card)
            } ?: run {
                Text("No card selected")
            }
        }
    }
}
