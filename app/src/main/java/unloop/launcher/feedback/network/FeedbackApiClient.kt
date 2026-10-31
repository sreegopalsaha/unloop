package unloop.launcher.feedback.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import unloop.launcher.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

sealed class FeedbackSubmissionResult {
    data class Success(val message: String) : FeedbackSubmissionResult()
    data class Error(val message: String, val isNetworkError: Boolean = false) : FeedbackSubmissionResult()
}

open class FeedbackApiClient(
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL
) {

    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    }

    open fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    open fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && EMAIL_REGEX.matches(email.trim())
    }

    open suspend fun submitFeedback(
        context: Context,
        name: String?,
        email: String,
        feedback: String,
        rating: Int? = null,
        token: String? = null
    ): FeedbackSubmissionResult = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(context)) {
            return@withContext FeedbackSubmissionResult.Error(
                message = "No internet connection. Please turn on your internet and try again.",
                isNetworkError = true
            )
        }

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !isValidEmail(trimmedEmail)) {
            return@withContext FeedbackSubmissionResult.Error(
                message = "A valid email address is required to submit feedback."
            )
        }

        val trimmedFeedback = feedback.trim()
        if (trimmedFeedback.isEmpty()) {
            return@withContext FeedbackSubmissionResult.Error(
                message = "Please enter your feedback before submitting."
            )
        }

        if (trimmedFeedback.length > 5000) {
            return@withContext FeedbackSubmissionResult.Error(
                message = "Feedback exceeds the maximum limit of 5,000 characters."
            )
        }

        if (rating != null && (rating < 1 || rating > 5)) {
            return@withContext FeedbackSubmissionResult.Error(
                message = "Rating must be between 1 and 5."
            )
        }

        try {
            val endpoint = URL("$baseUrl/api/feedback")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                val cleanName = name?.trim()?.take(200)?.ifBlank { null }
                if (cleanName != null) {
                    put("name", cleanName)
                }
                put("email", trimmedEmail)
                put("feedback", trimmedFeedback)
                if (rating != null && rating in 1..5) {
                    put("rating", rating)
                }
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseString = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { reader ->
                reader.readText()
            }

            val json = try {
                JSONObject(responseString)
            } catch (_: Exception) {
                null
            }

            when (statusCode) {
                in 200..299 -> {
                    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Thank you! Your feedback has been submitted successfully."
                    FeedbackSubmissionResult.Success(message)
                }
                400 -> {
                    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Validation error: Please check your feedback details and try again."
                    FeedbackSubmissionResult.Error(message)
                }
                429 -> {
                    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "You're sending feedback too quickly. Please try again in a few minutes."
                    FeedbackSubmissionResult.Error(message)
                }
                in 500..599 -> {
                    FeedbackSubmissionResult.Error("Server error ($statusCode). Please try again later.")
                }
                else -> {
                    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Failed to submit feedback ($statusCode). Please try again."
                    FeedbackSubmissionResult.Error(message)
                }
            }
        } catch (e: UnknownHostException) {
            FeedbackSubmissionResult.Error(
                message = "No internet connection. Please turn on your internet and try again.",
                isNetworkError = true
            )
        } catch (e: ConnectException) {
            FeedbackSubmissionResult.Error(
                message = "Unable to connect to server. Please check your internet connection.",
                isNetworkError = true
            )
        } catch (e: SocketTimeoutException) {
            FeedbackSubmissionResult.Error(
                message = "Request timed out. Please check your internet connection and try again.",
                isNetworkError = true
            )
        } catch (e: Exception) {
            FeedbackSubmissionResult.Error(
                message = e.localizedMessage ?: "An unexpected error occurred. Please try again."
            )
        }
    }
}
