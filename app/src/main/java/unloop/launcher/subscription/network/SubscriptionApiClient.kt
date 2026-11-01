package unloop.launcher.subscription.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import unloop.launcher.BuildConfig
import unloop.launcher.auth.network.AuthApiException
import unloop.launcher.subscription.model.AlreadyEntitledException
import unloop.launcher.subscription.model.CheckoutIntent
import unloop.launcher.subscription.model.EntitlementInfo
import unloop.launcher.subscription.model.PricingHelper
import unloop.launcher.subscription.model.SubscriptionPlan
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

open class SubscriptionApiClient(
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL
) {

    open suspend fun getPlansWithRaw(): Result<Pair<List<SubscriptionPlan>, String>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/plans")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val responseString = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { it.readText() }

            if (statusCode in 200..299) {
                val plans = PricingHelper.parsePlansJson(responseString)
                Result.success(Pair(plans, responseString))
            } else {
                Result.failure(AuthApiException("Failed to fetch plans: HTTP $statusCode", statusCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getPlans(): Result<List<SubscriptionPlan>> = withContext(Dispatchers.IO) {
        getPlansWithRaw().map { it.first }
    }

    open suspend fun createMonthlySubscription(
        token: String,
        planId: String = PricingHelper.PLAN_MONTHLY
    ): Result<CheckoutIntent.Monthly> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/subscriptions/create")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val requestBody = JSONObject().apply {
                put("planId", planId)
            }.toString()

            OutputStreamWriter(connection.outputStream, "UTF-8").use {
                it.write(requestBody)
                it.flush()
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val responseString = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { it.readText() }

            val json = JSONObject(responseString)
            val success = json.optBoolean("success", false)

            if (statusCode in 200..299 && success) {
                val data = json.getJSONObject("data")
                Result.success(
                    CheckoutIntent.Monthly(
                        subscriptionId = data.getString("subscriptionId"),
                        keyId = data.getString("keyId"),
                        amountPaise = data.optLong("amount", PricingHelper.AMOUNT_MONTHLY_PAISE),
                        currency = data.optString("currency", "INR"),
                        name = data.optString("name", "Unloop Launcher"),
                        description = data.optString("description", "Monthly Premium Plan")
                    )
                )
            } else {
                val message = json.optString("message", "Failed to create subscription (HTTP $statusCode)")
                val code = json.optString("code", "")
                if (code == "ALREADY_LIFETIME" || message.contains("already have Lifetime Premium", ignoreCase = true) || message.contains("already own Lifetime Premium", ignoreCase = true)) {
                    Result.failure(AlreadyEntitledException(message))
                } else {
                    Result.failure(AuthApiException(message, statusCode))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun verifyMonthlySubscription(
        token: String,
        paymentId: String,
        subscriptionId: String,
        signature: String
    ): Result<EntitlementInfo> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/subscriptions/verify")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val requestBody = JSONObject().apply {
                put("razorpay_payment_id", paymentId)
                put("razorpay_subscription_id", subscriptionId)
                put("razorpay_signature", signature)
            }.toString()

            OutputStreamWriter(connection.outputStream, "UTF-8").use {
                it.write(requestBody)
                it.flush()
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val responseString = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { it.readText() }

            val json = JSONObject(responseString)
            val success = json.optBoolean("success", false)

            if (statusCode in 200..299 && success) {
                val data = json.getJSONObject("data")
                val tokenStr = data.optString("entitlementToken", "")
                val entObj = data.getJSONObject("entitlement")
                Result.success(parseEntitlementInfo(entObj, tokenStr))
            } else {
                val message = json.optString("message", "Subscription verification failed (HTTP $statusCode)")
                Result.failure(AuthApiException(message, statusCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun createLifetimeOrder(
        token: String,
        planId: String = PricingHelper.PLAN_LIFETIME
    ): Result<CheckoutIntent.Lifetime> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/payments/create-order")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val requestBody = JSONObject().apply {
                put("planId", planId)
            }.toString()

            OutputStreamWriter(connection.outputStream, "UTF-8").use {
                it.write(requestBody)
                it.flush()
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val responseString = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { it.readText() }

            val json = JSONObject(responseString)
            val success = json.optBoolean("success", false)

            if (statusCode in 200..299 && success) {
                val data = json.getJSONObject("data")
                Result.success(
                    CheckoutIntent.Lifetime(
                        orderId = data.getString("orderId"),
                        keyId = data.getString("keyId"),
                        amountPaise = data.optLong("amount", PricingHelper.AMOUNT_LIFETIME_PAISE),
                        currency = data.optString("currency", "INR"),
                        name = data.optString("name", "Unloop Launcher"),
                        description = data.optString("description", "Lifetime Premium Access")
                    )
                )
            } else {
                val message = json.optString("message", "Failed to create lifetime order (HTTP $statusCode)")
                val code = json.optString("code", "")
                if (code == "ALREADY_LIFETIME" || message.contains("already own Lifetime Premium", ignoreCase = true) || message.contains("already have Lifetime Premium", ignoreCase = true)) {
                    Result.failure(AlreadyEntitledException(message))
                } else {
                    Result.failure(AuthApiException(message, statusCode))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun verifyLifetimePayment(
        token: String,
        paymentId: String,
        orderId: String,
        signature: String
    ): Result<EntitlementInfo> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/payments/verify")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val requestBody = JSONObject().apply {
                put("razorpay_payment_id", paymentId)
                put("razorpay_order_id", orderId)
                put("razorpay_signature", signature)
            }.toString()

            OutputStreamWriter(connection.outputStream, "UTF-8").use {
                it.write(requestBody)
                it.flush()
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val responseString = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { it.readText() }

            val json = JSONObject(responseString)
            val success = json.optBoolean("success", false)

            if (statusCode in 200..299 && success) {
                val data = json.getJSONObject("data")
                val tokenStr = data.optString("entitlementToken", "")
                val entObj = data.getJSONObject("entitlement")
                Result.success(parseEntitlementInfo(entObj, tokenStr))
            } else {
                val message = json.optString("message", "Lifetime verification failed (HTTP $statusCode)")
                Result.failure(AuthApiException(message, statusCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getSubscriptionStatus(token: String): Result<EntitlementInfo> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/subscription")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val responseString = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { it.readText() }

            val json = JSONObject(responseString)
            val success = json.optBoolean("success", false)

            if (statusCode in 200..299 && success) {
                val data = json.getJSONObject("data")
                val tokenStr = if (data.isNull("entitlementToken")) null else data.optString("entitlementToken")
                Result.success(parseEntitlementInfo(data, tokenStr))
            } else {
                val message = json.optString("message", "Failed to retrieve subscription status (HTTP $statusCode)")
                Result.failure(AuthApiException(message, statusCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun cancelSubscription(token: String): Result<EntitlementInfo> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/subscriptions/cancel")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val responseString = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { it.readText() }

            val json = JSONObject(responseString)
            val success = json.optBoolean("success", false)

            if (statusCode in 200..299 && success) {
                val data = json.getJSONObject("data")
                val tokenStr = if (data.isNull("entitlementToken")) null else data.optString("entitlementToken")
                Result.success(parseEntitlementInfo(data, tokenStr))
            } else {
                val message = json.optString("message", "Failed to cancel subscription (HTTP $statusCode)")
                Result.failure(AuthApiException(message, statusCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseEntitlementInfo(obj: JSONObject, token: String?): EntitlementInfo {
        return EntitlementInfo(
            isEntitled = obj.optBoolean("isEntitled", false),
            planId = if (obj.isNull("planId")) null else obj.optString("planId"),
            isLifetime = obj.optBoolean("isLifetime", false),
            status = obj.optString("status", "none"),
            startsAt = if (obj.isNull("startsAt")) null else obj.optLong("startsAt"),
            expiresAt = if (obj.isNull("expiresAt")) null else obj.optLong("expiresAt"),
            subscriptionId = if (obj.isNull("subscriptionId")) null else obj.optString("subscriptionId"),
            serverTimestamp = obj.optLong("serverTimestamp", System.currentTimeMillis()),
            entitlementToken = token
        )
    }
}
