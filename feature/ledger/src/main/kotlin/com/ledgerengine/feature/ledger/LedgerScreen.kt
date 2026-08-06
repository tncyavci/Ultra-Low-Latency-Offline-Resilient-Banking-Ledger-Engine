package com.ledgerengine.feature.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.ui.chart.SpendingGraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    accountId: AccountId,
    modifier: Modifier = Modifier,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(accountId) {
        viewModel.onIntent(LedgerIntent.LoadAccount(accountId))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LedgerEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.accountDisplayName.ifBlank { "Account" }) },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(LedgerIntent.Refresh) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync now")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (state.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.isLoading) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "balance_header") {
                    BalanceHeader(
                        balanceFormatted = state.balanceFormatted,
                        availableBalanceFormatted = state.availableBalanceFormatted,
                        hasPendingTransactions = state.hasPendingTransactions,
                    )
                }
                item(key = "spending_graph") {
                    SpendingGraph(
                        points = state.spendingPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item(key = "divider") { HorizontalDivider() }
                items(state.transactions, key = { it.id }) { transaction ->
                    TransactionRow(model = transaction)
                }
            }
        }
    }
}

@Composable
private fun BalanceHeader(
    balanceFormatted: String,
    availableBalanceFormatted: String,
    hasPendingTransactions: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = "Balance", style = MaterialTheme.typography.labelSmall)
        Text(text = balanceFormatted, style = MaterialTheme.typography.displaySmall)
        if (hasPendingTransactions) {
            Text(
                text = "$availableBalanceFormatted available · pending sync",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
