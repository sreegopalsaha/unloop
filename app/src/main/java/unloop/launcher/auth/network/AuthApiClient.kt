package unloop.launcher.auth.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import unloop.launcher.BuildConfig
import unloop.launcher.auth.model.AuthSession
import unloop.launcher.auth.model.UserProfile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AuthApiException(message: String, val statusCode: Int = 0) : Exception(message)

open class AuthApiClient(
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL
) {
    open suspend fun authenticateWithGoogle(idToken: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/auth/google")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val requestBody = JSONObject().apply {
                put("idToken", idToken)
            }.toString()

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody)
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

            val json = JSONObject(responseString)
            val success = json.optBoolean("success", false)

            if (statusCode in 200..299 && success) {
                val dataObj = json.getJSONObject("data")
                val token = dataObj.getString("token")
                val userObj = dataObj.getJSONObject("user")
                val user = UserProfile(
                    id = userObj.getString("id"),
                    name = userObj.optString("name", "User"),
                    email = userObj.getString("email"),
                    picture = if (userObj.isNull("picture")) null else userObj.optString("picture")
                )
                Result.success(AuthSession(token = token, user = user))
            } else {
                val message = json.optString("message", "Authentication failed with status $statusCode")
                Result.failure(AuthApiException(message, statusCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getProfile(token: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/users/me")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
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

            val json = JSONObject(responseString)
            val success = json.optBoolean("success", false)

            if (statusCode in 200..299 && success) {
                val userObj = json.getJSONObject("data")
                val user = UserProfile(
                    id = userObj.getString("id"),
                    name = userObj.optString("name", "User"),
                    email = userObj.getString("email"),
                    picture = if (userObj.isNull("picture")) null else userObj.optString("picture")
                )
                Result.success(user)
            } else {
                val message = json.optString("message", "Failed to fetch profile (status $statusCode)")
                Result.failure(AuthApiException(message, statusCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
