package unloop.launcher.subscription.payment

import android.app.Activity
import android.content.Context
import android.util.Log
import com.razorpay.Checkout
import org.json.JSONObject
import unloop.launcher.subscription.model.CheckoutIntent

object RazorpayCheckoutLauncher {

    private const val TAG = "RazorpayCheckout"

    fun preload(context: Context) {
        try {
            Checkout.preload(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to preload Razorpay checkout", e)
        }
    }

    /**
     * Opens Razorpay Standard Checkout for monthly recurring subscription via UPI AutoPay.
     * Passes server-generated subscription_id and recurring = 1.
     */
    fun openSubscriptionCheckout(
        activity: Activity,
        intent: CheckoutIntent.Monthly,
        customerEmail: String
    ): Boolean {
        return try {
            val checkout = Checkout()
            checkout.setKeyID(intent.keyId)

            val options = JSONObject().apply {
                put("name", intent.name)
                put("description", intent.description)
                put("currency", intent.currency)
                put("subscription_id", intent.subscriptionId)
                put("recurring", "1")

                val prefill = JSONObject().apply {
                    put("email", customerEmail)
                }
                put("prefill", prefill)

                // Minimalist dark theme for Razorpay checkout matching Unloop
                val theme = JSONObject().apply {
                    put("color", "#1A1A1A")
                    put("backdrop_color", "#000000")
                }
                put("theme", theme)
            }

            checkout.open(activity, options)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening subscription checkout", e)
            false
        }
    }

    /**
     * Opens Razorpay Standard Checkout for lifetime one-time purchase.
     * Passes server-generated order_id. Does NOT create a recurring subscription.
     */
    fun openLifetimeCheckout(
        activity: Activity,
        intent: CheckoutIntent.Lifetime,
        customerEmail: String
    ): Boolean {
        return try {
            val checkout = Checkout()
            checkout.setKeyID(intent.keyId)

            val options = JSONObject().apply {
                put("name", intent.name)
                put("description", intent.description)
                put("currency", intent.currency)
                put("order_id", intent.orderId)
                put("amount", intent.amountPaise)

                val prefill = JSONObject().apply {
                    put("email", customerEmail)
                }
                put("prefill", prefill)

                val theme = JSONObject().apply {
                    put("color", "#1A1A1A")
                    put("backdrop_color", "#000000")
                }
                put("theme", theme)
            }

            checkout.open(activity, options)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening lifetime checkout", e)
            false
        }
    }
}
