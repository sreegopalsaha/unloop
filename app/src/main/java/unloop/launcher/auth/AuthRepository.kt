package unloop.launcher.auth

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import unloop.launcher.auth.model.AuthSession
import unloop.launcher.auth.model.UserProfile
import unloop.launcher.auth.network.AuthApiClient
import unloop.launcher.auth.network.AuthApiException
import unloop.launcher.data.PreferencesManager
import java.io.IOException
import java.util.Base64

sealed interface SessionValidationResult {
    object NotNeeded : SessionValidationResult
    data class Valid(val session: AuthSession) : SessionValidationResult
    data class OfflineFallback(val session: AuthSession) : SessionValidationResult
    data class Invalid(val reason: String) : SessionValidationResult
    object NoSession : SessionValidationResult
}

class AuthRepository(
    private val preferencesManager: PreferencesManager,
    private val googleAuthManager: GoogleAuthManager = GoogleAuthManager(),
    private val authApiClient: AuthApiClient = AuthApiClient()
) {
    val authSessionFlow: StateFlow<AuthSession?> = preferencesManager.authSessionFlow

    suspend fun loginWithGoogle(activityContext: Context): Result<AuthSession> {
        val tokenResult = googleAuthManager.getGoogleIdToken(activityContext)
        val idToken = tokenResult.getOrElse { error ->
            return Result.failure(error)
        }

        val authResult = authApiClient.authenticateWithGoogle(idToken)
        return authResult.onSuccess { session ->
            preferencesManager.saveAuthSession(session)
        }
    }

    fun logout() {
        preferencesManager.clearAuthSession()
    }

    suspend fun refreshProfile(): Result<UserProfile> {
        val session = preferencesManager.getAuthSession()
            ?: return Result.failure(Exception("Not logged in"))

        val profileResult = authApiClient.getProfile(session.token)
        return profileResult.onSuccess { updatedProfile ->
            preferencesManager.saveAuthSession(
                session.copy(user = updatedProfile)
            )
        }
    }

    fun hasValidSession(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        val session = preferencesManager.getAuthSession() ?: return false
        if (isJwtExpired(session.token, currentTimeMillis)) {
            logout()
            return false
        }
        return true
    }

    suspend fun validateSessionIfNeeded(
        currentTimeMillis: Long = System.currentTimeMillis(),
        intervalMillis: Long = REVALIDATION_INTERVAL_MS
    ): SessionValidationResult {
        val session = preferencesManager.getAuthSession() ?: return SessionValidationResult.NoSession

        // 1. Check local JWT expiration
        if (isJwtExpired(session.token, currentTimeMillis)) {
            logout()
            return SessionValidationResult.Invalid("Session has expired")
        }

        // 2. Check if periodic validation interval (7 days) has passed
        val lastValidationTime = preferencesManager.getLastAuthValidationTime()
        val elapsed = currentTimeMillis - lastValidationTime
        if (lastValidationTime > 0 && elapsed in 0 until intervalMillis) {
            return SessionValidationResult.NotNeeded
        }

        // 3. Perform lightweight revalidation with backend
        val profileResult = authApiClient.getProfile(session.token)
        return profileResult.fold(
            onSuccess = { updatedProfile ->
                val updatedSession = session.copy(user = updatedProfile)
                preferencesManager.saveAuthSession(updatedSession)
                preferencesManager.setLastAuthValidationTime(currentTimeMillis)
                SessionValidationResult.Valid(updatedSession)
            },
            onFailure = { error ->
                if (error is AuthApiException && (error.statusCode == 401 || error.statusCode == 403)) {
                    logout()
                    SessionValidationResult.Invalid(error.message ?: "Session expired. Please sign in again.")
                } else if (error is IOException) {
                    // Offline or network error: allow user to continue using local session
                    SessionValidationResult.OfflineFallback(session)
                } else {
                    if (error is AuthApiException && error.statusCode in 400..499) {
                        logout()
                        SessionValidationResult.Invalid(error.message ?: "Authentication error")
                    } else {
                        // Network/server unreachable fallback
                        SessionValidationResult.OfflineFallback(session)
                    }
                }
            }
        )
    }

    companion object {
        const val REVALIDATION_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000L // 7 days

        fun isJwtExpired(token: String, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
            return try {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val payloadBytes = decodeBase64Url(parts[1])
                    val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
                    if (json.has("exp")) {
                        val expSeconds = json.optLong("exp", 0L)
                        expSeconds > 0 && (currentTimeMillis / 1000) >= expSeconds
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun decodeBase64Url(input: String): ByteArray {
            var str = input.trim()
            val remainder = str.length % 4
            if (remainder == 2) str += "=="
            else if (remainder == 3) str += "="
            return Base64.getUrlDecoder().decode(str)
        }
    }
}
