package unloop.launcher.auth

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import unloop.launcher.auth.model.AuthSession
import unloop.launcher.auth.model.UserProfile
import unloop.launcher.auth.network.AuthApiClient
import unloop.launcher.data.PreferencesManager

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
}
