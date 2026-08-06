package com.ledgerengine.feature.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A single row. Reads only its own [TransactionUiModel] — an `@Immutable`
 * value the parent `LazyColumn` diffs by stable key — so scrolling or
 * updating one row never forces its siblings to recompose.
 */
@Composable
fun TransactionRow(model: TransactionUiModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = model.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${model.subtitle} · ${model.statusLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = model.amountFormatted,
            style = MaterialTheme.typography.titleMedium,
            color = if (model.isCredit) MaterialTheme.colorScheme.secondary else Color.Unspecified,
        )
    }
}
