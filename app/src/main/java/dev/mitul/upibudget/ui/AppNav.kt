package dev.mitul.upibudget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

enum class Screen { DASHBOARD, SETTINGS, CATEGORIES, KEYWORDS, NAMES, RECURRING, RD, DEBUG }

@Composable
fun AppNav(vm: MainViewModel, openTxnId: Long?, onTxnHandled: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }
    BackHandler(enabled = screen != Screen.DASHBOARD) { screen = if (screen == Screen.SETTINGS) Screen.DASHBOARD else Screen.SETTINGS }
    when (screen) {
        Screen.DASHBOARD -> DashboardScreen(vm, { screen = Screen.SETTINGS }, openTxnId, onTxnHandled)
        else -> SettingsHost(vm, screen) { screen = it }
    }
}

