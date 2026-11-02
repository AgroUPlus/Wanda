package com.wander.android

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.wander.android.core.network.loggableMessage
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.sources.agro.explain
import com.wander.android.ui.navigation.LinkConfirmation
import com.wander.android.ui.navigation.LinkConfirmations
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.DeepLinkRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The links that hand something over — the account to an Agro server, or the listening history
 * to a new friend — and so do nothing until the user has seen what they name and said yes.
 */
@Singleton
internal class ConfirmedLinkHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val agroClient: AgroClient,
    private val socialRepository: SocialRepository,
    private val deepLinkRouter: DeepLinkRouter,
    private val linkConfirmations: LinkConfirmations
) {

    // Any page or app can fire this link, and a friend sees what the user listens to, so the
    // code is only redeemed once the user has said yes.
    fun handleFriendCode(uri: Uri) {
        val code = uri.lastPathSegment?.trim().orEmpty()
        if (code.isEmpty()) return
        linkConfirmations.ask(
            LinkConfirmation(
                title = R.string.link_friend_title,
                message = R.string.link_friend_message,
                messageArgs = listOf(code),
                confirmLabel = R.string.link_friend_confirm,
                onConfirm = {
                    val friend = socialRepository.redeemFriendCode(code).getOrNull()
                    val message = friend
                        ?.let { "You and @$it are now friends" }
                        ?: "That code has expired or has already been used."
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (friend != null) deepLinkRouter.request(TopLevelDestination.FRIENDS.route)
                }
            )
        )
    }

    // Any page or app can fire this link, and pairing hands the account to the server it names,
    // so nothing happens until the user has seen that server and said yes.
    fun handleAgroPairing(uri: Uri) {
        val (host, username) = agroClient.describePairingLink(uri.toString()) ?: run {
            Toast.makeText(context, R.string.link_agro_pair_invalid, Toast.LENGTH_LONG).show()
            return
        }
        linkConfirmations.ask(
            LinkConfirmation(
                title = R.string.link_agro_pair_title,
                message = R.string.link_agro_pair_message,
                messageArgs = listOf(host, username.ifBlank { "?" }),
                confirmLabel = R.string.link_agro_pair_confirm,
                onConfirm = {
                    val message = agroClient.parseQrCodePayload(uri.toString()).fold(
                        onSuccess = { petname ->
                            "Paired with Agro as ${petname ?: secureStorage.agroDevicePetname.ifEmpty { "wanda" }}"
                        },
                        onFailure = { error ->
                            android.util.Log.w("Wanda", "Agro pairing failed: ${error.loggableMessage()}")
                            AgroAuthError.from(error).explain(context)
                        }
                    )
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            )
        )
    }
}
