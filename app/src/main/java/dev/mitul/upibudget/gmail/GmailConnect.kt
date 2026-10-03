package dev.mitul.upibudget.gmail

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.Identity
import dev.mitul.upibudget.App
import dev.mitul.upibudget.data.SettingEntity
import kotlinx.coroutines.launch

suspend fun markGmailConnected(ctx: Context) {
    App.get(ctx).db.misc().putSetting(SettingEntity("gmail_connected", "1"))
    GmailWorker.schedule(ctx); GmailWorker.runNow(ctx)
}

@Composable
fun rememberGmailConnector(onResult: (Boolean) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        scope.launch {
            val ok = try { Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(r.data); markGmailConnected(ctx); true } catch (e: Exception) { false }
            onResult(ok)
        }
    }
    return {
        scope.launch {
            try {
                val res = GmailAuth.authorize(ctx)
                if (res.hasResolution()) launcher.launch(IntentSenderRequest.Builder(res.pendingIntent!!.intentSender).build())
                else { markGmailConnected(ctx); onResult(true) }
            } catch (e: Exception) { onResult(false) }
        }
    }
}
