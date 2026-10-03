package dev.mitul.upibudget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.mitul.upibudget.notify.Notifier
import dev.mitul.upibudget.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var openTxn by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTxn = intent.txnExtra()
        setContent {
            AppTheme {
                var onboarded by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) { onboarded = vm.isOnboarded() }
                when (onboarded) {
                    null -> Unit
                    false -> OnboardingScreen(vm) { onboarded = true }
                    true -> AppNav(vm, openTxn) { openTxn = null }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        lifecycleScope.launch { runCatching { val a = App.get(this@MainActivity); a.awaitReady(); a.catchUpSms(); a.missedChargesCheck() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTxn = intent.txnExtra()
    }

    private fun Intent.txnExtra(): Long? = getLongExtra(Notifier.EXTRA_TXN, -1L).takeIf { it >= 0 }
}
