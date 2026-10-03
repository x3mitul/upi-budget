package dev.mitul.upibudget.gmail

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

object GmailAuth {
    val SCOPE = Scope("https://www.googleapis.com/auth/gmail.readonly")

    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(SCOPE)).build()

    /** `hasResolution()` true => the UI must launch `pendingIntent` (first sign-in / consent). Otherwise `accessToken` is set. */
    suspend fun authorize(ctx: Context): AuthorizationResult = Identity.getAuthorizationClient(ctx).authorize(request()).await()
}
