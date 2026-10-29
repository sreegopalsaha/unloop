package unloop.launcher.subscription.model

import org.json.JSONArray
import org.json.JSONObject

enum class PlanBillingType {
    RECURRING,
    ONE_TIME
}

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val amountPaise: Long,
    val currency: String,
    val billingType: PlanBillingType,
    val interval: String? = null,
    val displayPrice: String,
    val displayPeriod: String,
    val badge: String? = null,
    val features: List<String>
) {
    val isRecurring: Boolean get() = billingType == PlanBillingType.RECURRING
    val isLifetime: Boolean get() = id == PricingHelper.PLAN_LIFETIME

    companion object {
        fun fromJson(obj: JSONObject): SubscriptionPlan {
            val featureList = mutableListOf<String>()
            val featArray = obj.optJSONArray("features")
            if (featArray != null) {
                for (i in 0 until featArray.length()) {
                    featureList.add(featArray.getString(i))
                }
            }

            val bType = if (obj.optString("billingType") == "recurring") {
                PlanBillingType.RECURRING
            } else {
                PlanBillingType.ONE_TIME
            }

            return SubscriptionPlan(
                id = obj.getString("id"),
                name = obj.optString("name", "Premium Plan"),
                tagline = obj.optString("tagline", ""),
                description = obj.optString("description", ""),
                amountPaise = obj.optLong("amount", 0L),
                currency = obj.optString("currency", "INR"),
                billingType = bType,
                interval = if (obj.has("interval")) obj.getString("interval") else null,
                displayPrice = obj.optString("displayPrice", "₹99"),
                displayPeriod = obj.optString("displayPeriod", "/month"),
                badge = if (obj.has("badge")) obj.getString("badge") else null,
                features = featureList
            )
        }
    }
}

object PricingHelper {
    const val PLAN_MONTHLY = "plan_monthly"
    const val PLAN_LIFETIME = "plan_lifetime"

    const val AMOUNT_MONTHLY_PAISE = 9900L // ₹99
    const val AMOUNT_LIFETIME_PAISE = 69900L // ₹699
    const val CURRENCY_INR = "INR"

    val DEFAULT_MONTHLY_PLAN = SubscriptionPlan(
        id = PLAN_MONTHLY,
        name = "Monthly Premium",
        tagline = "Distraction-free focus, month to month",
        description = "Full access to Unloop's premium functionality. Automatically renews every month through Razorpay UPI AutoPay.",
        amountPaise = AMOUNT_MONTHLY_PAISE,
        currency = CURRENCY_INR,
        billingType = PlanBillingType.RECURRING,
        interval = "monthly",
        displayPrice = "₹99",
        displayPeriod = "/month",
        badge = "Flexible",
        features = listOf(
            "Distraction-free minimalist launcher",
            "Short-video reels & shorts blocker",
            "Notification filter & manager",
            "Focus mode with mindful launch pauses",
            "Auto-renews monthly via UPI AutoPay",
            "Cancel anytime from app or UPI app"
        )
    )

    val DEFAULT_LIFETIME_PLAN = SubscriptionPlan(
        id = PLAN_LIFETIME,
        name = "Lifetime Premium",
        tagline = "One payment, permanent mindful digital life",
        description = "Full access to Unloop's premium functionality with a one-time purchase. No recurring fees or mandates.",
        amountPaise = AMOUNT_LIFETIME_PAISE,
        currency = CURRENCY_INR,
        billingType = PlanBillingType.ONE_TIME,
        displayPrice = "₹699",
        displayPeriod = "one-time",
        badge = "Best Value",
        features = listOf(
            "Everything in Monthly Premium",
            "Single one-time payment",
            "No UPI mandates or recurring debits",
            "Permanent entitlement on this account",
            "All future premium updates included"
        )
    )

    fun getDefaultPlans(): List<SubscriptionPlan> = listOf(DEFAULT_MONTHLY_PLAN, DEFAULT_LIFETIME_PLAN)

    fun parsePlansJson(jsonStr: String): List<SubscriptionPlan> {
        return try {
            val json = JSONObject(jsonStr)
            val dataObj = json.optJSONObject("data") ?: json
            val array = dataObj.optJSONArray("plans") ?: JSONArray()
            val list = mutableListOf<SubscriptionPlan>()
            for (i in 0 until array.length()) {
                list.add(SubscriptionPlan.fromJson(array.getJSONObject(i)))
            }
            if (list.isEmpty()) getDefaultPlans() else list
        } catch (_: Exception) {
            getDefaultPlans()
        }
    }
}

data class EntitlementInfo(
    val isEntitled: Boolean,
    val planId: String?,
    val isLifetime: Boolean,
    val status: String,
    val startsAt: Long?,
    val expiresAt: Long?,
    val subscriptionId: String?,
    val serverTimestamp: Long,
    val entitlementToken: String?
)

sealed interface CheckoutIntent {
    data class Monthly(
        val subscriptionId: String,
        val keyId: String,
        val amountPaise: Long,
        val currency: String,
        val name: String,
        val description: String
    ) : CheckoutIntent

    data class Lifetime(
        val orderId: String,
        val keyId: String,
        val amountPaise: Long,
        val currency: String,
        val name: String,
        val description: String
    ) : CheckoutIntent
}

sealed interface EntitlementState {
    data object Loading : EntitlementState
    data class Entitled(
        val info: EntitlementInfo,
        val source: EntitlementSource
    ) : EntitlementState
    data class NotEntitled(
        val reason: String,
        val canRetry: Boolean = true
    ) : EntitlementState
}

enum class EntitlementSource {
    SERVER_VERIFIED,
    CRYPTOGRAPHICALLY_VERIFIED_OFFLINE
}
