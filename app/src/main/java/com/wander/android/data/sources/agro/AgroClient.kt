package com.wander.android.data.sources.agro

import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/** Registration, handoff publishing and pairing. Transport lives in [AgroGraphQl]. */
@Singleton
class AgroClient @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val nodeRegistrar: AgroNodeRegistrar,
    private val pairingCoordinator: AgroPairingCoordinator
) {
    constructor(
        login: AgroLogin,
        graphQl: AgroGraphQl,
        secureStorage: SecureStorage,
        identityKeyManager: IdentityKeyManager
    ) : this(
        graphQl = graphQl,
        nodeRegistrar = AgroNodeRegistrar(graphQl, secureStorage, identityKeyManager),
        pairingCoordinator = AgroPairingCoordinator(
            login,
            graphQl,
            secureStorage,
            AgroNodeRegistrar(graphQl, secureStorage, identityKeyManager)
        )
    )

    val isConfigured: Boolean get() = graphQl.isConfigured

    /**
     * Battery-first one-shot registration: called on app launch or pairing only.
     * Never runs in an unconstrained background loop.
     */
    suspend fun registerNode(currentTrack: String? = null): Result<String?> =
        nodeRegistrar.registerNode(currentTrack)

    /**
     * Asks the server who this device's stored token belongs to.
     */
    internal suspend fun verify(): Result<AgroIdentity> =
        nodeRegistrar.verify()

    /** Unregisters this device from Agro on unpair or credential reset. */
    suspend fun unregisterNode(): Result<Unit> =
        nodeRegistrar.unregisterNode()

    /**
     * `agro://connect?username=…&token=…&server=…`, as minted by the server's pairing QR.
     */
    suspend fun parseQrCodePayload(qrString: String): Result<String?> =
        pairingCoordinator.parseQrCodePayload(qrString)

    /**
     * Exchanges a passphrase for a device token, unwrap or enrols the account vault key, then pairs.
     */
    suspend fun pairWithPassphrase(
        serverUrl: String,
        username: String,
        passphrase: String
    ): Result<String?> =
        pairingCoordinator.pairWithPassphrase(serverUrl, username, passphrase)

    /**
     * Stores a device token and proves it by registering.
     */
    suspend fun pairWithToken(
        serverUrl: String,
        username: String,
        token: String
    ): Result<String?> =
        pairingCoordinator.pairWithToken(serverUrl, username, token)

    companion object {
        const val DEFAULT_SERVER_URL = "https://agro.kolbxyz.xyz"

        /** Placeholder title on a sealed handoff — the real one is inside [HandoffInput]'s envelope. */
        const val PRIVATE_SESSION_TITLE = "Private Session"
    }
}
