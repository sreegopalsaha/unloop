package unloop.launcher.subscription

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import unloop.launcher.auth.AuthRepository
import unloop.launcher.data.PreferencesManager
import unloop.launcher.subscription.crypto.EntitlementCrypto
import unloop.launcher.subscription.crypto.VerifiedEntitlement
import unloop.launcher.subscription.model.CheckoutIntent
import unloop.launcher.subscription.model.EntitlementInfo
import unloop.launcher.subscription.model.EntitlementSource
import unloop.launcher.subscription.model.EntitlementState
import unloop.launcher.subscription.model.PricingHelper
import unloop.launcher.subscription.model.SubscriptionPlan
import unloop.launcher.subscription.network.SubscriptionApiClient
import unloop.launcher.subscription.storage.EntitlementStorage

class SubscriptionRepository(
    context: Context,
    private val preferencesManager: PreferencesManager = PreferencesManager.getInstance(context),
    private val entitlementStorage: EntitlementStorage = EntitlementStorage.getInstance(context),
    private val apiClient: SubscriptionApiClient = SubscriptionApiClient()
) {
    private val repoScope = CoroutineScope(Dispatchers.IO)

    private val _entitlementState = MutableStateFlow<EntitlementState>(EntitlementState.Loading)
    val entitlementState: StateFlow<EntitlementState> = _entitlementState.asStateFlow()

    val isEntitled: StateFlow<Boolean> = _entitlementState.map { state ->
        when (state) {
            is EntitlementState.Entitled -> true
            else -> false
        }
    }.stateIn(repoScope, SharingStarted.Eagerly, false)

    private val _cachedPlans = MutableStateFlow<List<SubscriptionPlan>>(PricingHelper.getDefaultPlans())
    val cachedPlans: StateFlow<List<SubscriptionPlan>> = _cachedPlans.asStateFlow()

    init {
        // Load plans from local cache or fallback
        val cachedJson = entitlementStorage.getCachedPlans()
        if (cachedJson != null) {
            _cachedPlans.value = PricingHelper.parsePlansJson(cachedJson)
        }
        // Initialize entitlement check from local Keystore-backed cache
        evaluateLocalEntitlement()
    }

    /**
     * Evaluates local Keystore-backed signed entitlement offline.
     * Guarantees that only cryptographically authentic, non-expired tokens grant access.
     */
    fun evaluateLocalEntitlement(): Boolean {
        val session = preferencesManager.getAuthSession()
        if (session == null) {
            _entitlementState.value = EntitlementState.NotEntitled("Not signed in")
            return false
        }

        val cachedToken = entitlementStorage.getCachedEntitlementToken(session.user.id)
        if (cachedToken.isNullOrBlank()) {
            _entitlementState.value = EntitlementState.NotEntitled("No local entitlement record")
            return false
        }

        val lastServerTime = entitlementStorage.getLastServerTime()
        val verified = EntitlementCrypto.verifyEntitlement(
            token = cachedToken,
            expectedUserId = session.user.id,
            lastKnownServerTimeMs = lastServerTime
        )

        return if (verified != null && verified.isCurrentlyValid()) {
            val info = EntitlementInfo(
                isEntitled = true,
                planId = verified.planId,
                isLifetime = verified.isLifetime,
                status = verified.status,
                startsAt = verified.startsAt,
                expiresAt = verified.expiresAt,
                subscriptionId = verified.subscriptionId,
                serverTimestamp = verified.syncTimestamp,
                entitlementToken = verified.rawToken
            )
            _entitlementState.value = EntitlementState.Entitled(info, EntitlementSource.CRYPTOGRAPHICALLY_VERIFIED_OFFLINE)
            true
        } else {
            val reason = if (verified != null && !verified.isCurrentlyValid()) {
                "Monthly subscription has expired"
            } else {
                "Invalid or tampered entitlement cache"
            }
            _entitlementState.value = EntitlementState.NotEntitled(reason)
            false
        }
    }

    suspend fun refreshPlans(): List<SubscriptionPlan> = withContext(Dispatchers.IO) {
        val result = apiClient.getPlans()
        result.fold(
            onSuccess = { plans ->
                if (plans.isNotEmpty()) {
                    _cachedPlans.value = plans
                }
                plans
            },
            onFailure = {
                _cachedPlans.value
            }
        )
    }

    suspend fun synchronizeEntitlement(forceServerCheck: Boolean = false): Result<EntitlementInfo> = withContext(Dispatchers.IO) {
        val session = preferencesManager.getAuthSession()
            ?: return@withContext Result.failure(Exception("User is not authenticated"))

        // If local entitlement is valid and forceServerCheck is false, return offline verified state
        if (!forceServerCheck && evaluateLocalEntitlement()) {
            val current = (_entitlementState.value as? EntitlementState.Entitled)?.info
            if (current != null) {
                return@withContext Result.success(current)
            }
        }

        val result = apiClient.getSubscriptionStatus(session.token)
        result.fold(
            onSuccess = { info ->
                if (info.isEntitled && !info.entitlementToken.isNullOrBlank()) {
                    // Cryptographically verify server token before caching
                    val verified = EntitlementCrypto.verifyEntitlement(
                        token = info.entitlementToken,
                        expectedUserId = session.user.id,
                        lastKnownServerTimeMs = info.serverTimestamp
                    )

                    if (verified != null) {
                        entitlementStorage.saveEntitlement(
                            token = info.entitlementToken,
                            userId = session.user.id,
                            serverTimeMs = info.serverTimestamp
                        )
                        _entitlementState.value = EntitlementState.Entitled(info, EntitlementSource.SERVER_VERIFIED)
                        Result.success(info)
                    } else {
                        _entitlementState.value = EntitlementState.NotEntitled("Server returned untrusted signature")
                        Result.failure(Exception("Untrusted server entitlement signature"))
                    }
                } else {
                    entitlementStorage.clearEntitlement()
                    _entitlementState.value = EntitlementState.NotEntitled("No active subscription")
                    Result.success(info)
                }
            },
            onFailure = { error ->
                // Network unreachable or server error: evaluate local cache
                if (evaluateLocalEntitlement()) {
                    val current = (_entitlementState.value as? EntitlementState.Entitled)?.info
                    if (current != null) {
                        return@withContext Result.success(current)
                    }
                }
                Result.failure(error)
            }
        )
    }

    suspend fun initiateMonthlySubscription(): Result<CheckoutIntent.Monthly> = withContext(Dispatchers.IO) {
        val session = preferencesManager.getAuthSession()
            ?: return@withContext Result.failure(Exception("Please sign in before subscribing"))
        apiClient.createMonthlySubscription(session.token, PricingHelper.PLAN_MONTHLY)
    }

    suspend fun verifyMonthlySubscription(
        paymentId: String,
        subscriptionId: String,
        signature: String
    ): Result<EntitlementInfo> = withContext(Dispatchers.IO) {
        val session = preferencesManager.getAuthSession()
            ?: return@withContext Result.failure(Exception("User session expired"))

        val result = apiClient.verifyMonthlySubscription(session.token, paymentId, subscriptionId, signature)
        result.onSuccess { info ->
            if (info.entitlementToken != null) {
                entitlementStorage.saveEntitlement(
                    token = info.entitlementToken,
                    userId = session.user.id,
                    serverTimeMs = info.serverTimestamp
                )
            }
            _entitlementState.value = EntitlementState.Entitled(info, EntitlementSource.SERVER_VERIFIED)
        }
    }

    suspend fun initiateLifetimePurchase(): Result<CheckoutIntent.Lifetime> = withContext(Dispatchers.IO) {
        val session = preferencesManager.getAuthSession()
            ?: return@withContext Result.failure(Exception("Please sign in before purchasing"))
        apiClient.createLifetimeOrder(session.token, PricingHelper.PLAN_LIFETIME)
    }

    suspend fun verifyLifetimePayment(
        paymentId: String,
        orderId: String,
        signature: String
    ): Result<EntitlementInfo> = withContext(Dispatchers.IO) {
        val session = preferencesManager.getAuthSession()
            ?: return@withContext Result.failure(Exception("User session expired"))

        val result = apiClient.verifyLifetimePayment(session.token, paymentId, orderId, signature)
        result.onSuccess { info ->
            if (info.entitlementToken != null) {
                entitlementStorage.saveEntitlement(
                    token = info.entitlementToken,
                    userId = session.user.id,
                    serverTimeMs = info.serverTimestamp
                )
            }
            _entitlementState.value = EntitlementState.Entitled(info, EntitlementSource.SERVER_VERIFIED)
        }
    }

    suspend fun cancelMonthlySubscription(): Result<EntitlementInfo> = withContext(Dispatchers.IO) {
        val session = preferencesManager.getAuthSession()
            ?: return@withContext Result.failure(Exception("User session expired"))

        val result = apiClient.cancelSubscription(session.token)
        result.onSuccess { info ->
            if (info.entitlementToken != null) {
                entitlementStorage.saveEntitlement(
                    token = info.entitlementToken,
                    userId = session.user.id,
                    serverTimeMs = info.serverTimestamp
                )
            }
            if (info.isEntitled) {
                _entitlementState.value = EntitlementState.Entitled(info, EntitlementSource.SERVER_VERIFIED)
            } else {
                _entitlementState.value = EntitlementState.NotEntitled("Subscription cancelled")
            }
        }
    }

    fun onUserLoggedOut() {
        entitlementStorage.clearEntitlement()
        _entitlementState.value = EntitlementState.NotEntitled("Signed out")
    }

    fun onUserLoggedIn() {
        evaluateLocalEntitlement()
        repoScope.launch {
            synchronizeEntitlement(forceServerCheck = false)
        }
    }
}
