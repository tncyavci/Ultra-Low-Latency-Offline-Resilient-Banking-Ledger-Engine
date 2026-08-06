package com.ledgerengine.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.feature.ledger.LedgerScreen
import com.ledgerengine.ui.theme.LedgerEngineTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LedgerEngineTheme {
                LedgerScreen(
                    accountId = AccountId(LedgerApplication.DEMO_ACCOUNT_ID),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
