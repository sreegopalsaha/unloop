package unloop.launcher.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import unloop.launcher.BuildConfig

open class GoogleAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
class GoogleAuthCancellationException(message: String) : GoogleAuthException(message)

class GoogleAuthManager(
    private val serverClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
) {
    suspend fun getGoogleIdToken(context: Context): Result<String> {
        if (serverClientId.isBlank()) {
            return Result.failure(
                GoogleAuthException("Google Web Client ID is not configured. Please check local.properties or environment configuration.")
            )
        }

        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdTokenCredential.idToken)
            } else {
                Result.failure(
                    GoogleAuthException("Received unexpected credential type: ${credential.type}")
                )
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(GoogleAuthCancellationException("Sign-in cancelled by user"))
        } catch (e: NoCredentialException) {
            Result.failure(GoogleAuthException("No Google credentials available on this device", e))
        } catch (e: GetCredentialException) {
            Result.failure(GoogleAuthException("Google authentication failed: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(GoogleAuthException("Unexpected authentication error: ${e.message}", e))
        }
    }
}
